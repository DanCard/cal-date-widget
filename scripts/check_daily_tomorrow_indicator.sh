#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="ai.dcar.caldatewidget"
PROVIDER_COMPONENT="${PACKAGE_NAME}/.DailyWidgetProvider"
LOG_TAG="WidgetDrawer"
WAIT_SECONDS=3
SERIAL=""
SCENARIO=""

usage() {
    cat <<'EOF'
Usage:
  scripts/check_daily_tomorrow_indicator.sh --scenario <auto|today> [--serial <adb-serial>] [--wait <seconds>]

Scenarios:
  auto   Expect auto-advance to tomorrow and rainbow indicator logic to trigger.
         Prep: all valid events for today must be in the past.
  today  Expect no auto-advance and no rainbow indicator logic.
         Prep: at least one valid future event exists today (or no events today).

Examples:
  scripts/check_daily_tomorrow_indicator.sh --scenario auto
  scripts/check_daily_tomorrow_indicator.sh --scenario today --serial emulator-5554
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --scenario)
            SCENARIO="${2:-}"
            shift 2
            ;;
        --serial)
            SERIAL="${2:-}"
            shift 2
            ;;
        --wait)
            WAIT_SECONDS="${2:-}"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage
            exit 2
            ;;
    esac
done

if [[ "${SCENARIO}" != "auto" && "${SCENARIO}" != "today" ]]; then
    echo "Error: --scenario must be 'auto' or 'today'." >&2
    usage
    exit 2
fi

if ! [[ "${WAIT_SECONDS}" =~ ^[0-9]+$ ]]; then
    echo "Error: --wait must be a whole number of seconds." >&2
    exit 2
fi

if ! command -v adb >/dev/null 2>&1; then
    echo "Error: adb was not found in PATH." >&2
    exit 1
fi

ADB=(adb)
if [[ -n "${SERIAL}" ]]; then
    ADB+=( -s "${SERIAL}" )
fi

if ! "${ADB[@]}" get-state >/dev/null 2>&1; then
    echo "Error: No reachable adb device for args: ${ADB[*]}" >&2
    exit 1
fi

echo "Clearing logcat..."
"${ADB[@]}" logcat -c

echo "Broadcasting DailyWidgetProvider update..."
"${ADB[@]}" shell am broadcast \
    -a android.appwidget.action.APPWIDGET_UPDATE \
    -n "${PROVIDER_COMPONENT}" >/dev/null

echo "Waiting ${WAIT_SECONDS}s for widget render..."
sleep "${WAIT_SECONDS}"

RAW_LOGS="$("${ADB[@]}" logcat -d -s "${LOG_TAG}:D" 2>/dev/null || true)"
MATCHED_LOGS="$(printf '%s\n' "${RAW_LOGS}" | grep -E "Starting drawDailyCalendar|Auto-advancing to tomorrow|Drawing tomorrow indicator|Skipped tomorrow indicator due to narrow column" || true)"

echo
echo "Relevant ${LOG_TAG} logs:"
if [[ -n "${MATCHED_LOGS}" ]]; then
    printf '%s\n' "${MATCHED_LOGS}"
else
    echo "(none)"
fi
echo

HAS_AUTO=false
HAS_INDICATOR=false
HAS_DRAWN=false
HAS_SKIPPED=false

if printf '%s\n' "${MATCHED_LOGS}" | grep -q "Auto-advancing to tomorrow"; then
    HAS_AUTO=true
fi
if printf '%s\n' "${MATCHED_LOGS}" | grep -q "Drawing tomorrow indicator"; then
    HAS_DRAWN=true
    HAS_INDICATOR=true
fi
if printf '%s\n' "${MATCHED_LOGS}" | grep -q "Skipped tomorrow indicator due to narrow column"; then
    HAS_SKIPPED=true
    HAS_INDICATOR=true
fi

if [[ "${SCENARIO}" == "auto" ]]; then
    if [[ "${HAS_AUTO}" == "true" && "${HAS_INDICATOR}" == "true" ]]; then
        if [[ "${HAS_DRAWN}" == "true" ]]; then
            echo "PASS: Auto-advance detected and rainbow indicator draw path executed."
        else
            echo "PASS: Auto-advance detected and indicator logic executed, but glyph was skipped due to narrow column."
            echo "Tip: Resize widget wider to visually confirm the rainbow."
        fi
        exit 0
    fi

    echo "FAIL: Expected auto scenario but log evidence was incomplete."
    echo "Expected: auto-advance + indicator logic."
    exit 1
fi

if [[ "${HAS_AUTO}" == "false" && "${HAS_INDICATOR}" == "false" ]]; then
    echo "PASS: Today scenario detected (no auto-advance, no rainbow indicator logic)."
    exit 0
fi

echo "FAIL: Expected today scenario but found auto-advance and/or indicator logs."
echo "Expected: no auto-advance and no indicator logic."
exit 1
