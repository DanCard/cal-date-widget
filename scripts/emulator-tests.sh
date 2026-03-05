#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RESULTS_ROOT="${PROJECT_DIR}/app/build/outputs/androidTest-results/connected"
GRADLE_TASK=":app:connectedDebugAndroidTest"

SERIAL=""
TEST_CLASS=""
EXTRA_GRADLE_ARGS=()

usage() {
    cat <<'EOF'
Usage:
  scripts/emulator-tests.sh [--serial <adb-serial>] [--class <fqcn[#method]>] [-- <extra-gradle-args>]

Description:
  Runs Android instrumented tests and prints a concise summary with:
  Total / Passed / Failed / Errors / Skipped

Options:
  --serial SERIAL   Target a specific connected device/emulator
  --class CLASS     Run one test class or method (e.g. ai.dcar.caldatewidget.ExampleTest#works)
  -h, --help        Show this help

Examples:
  scripts/emulator-tests.sh                       # Runs all connected emulators by default
  scripts/emulator-tests.sh --serial emulator-5554
  scripts/emulator-tests.sh --class ai.dcar.caldatewidget.ExampleTest
  scripts/emulator-tests.sh --class ai.dcar.caldatewidget.ExampleTest#works -- --info
EOF
}

contains_value() {
    local needle="$1"
    shift
    local item
    for item in "$@"; do
        if [[ "${item}" == "${needle}" ]]; then
            return 0
        fi
    done
    return 1
}

extract_attr() {
    local line="$1"
    local key="$2"
    local value
    value="$(printf '%s\n' "${line}" | sed -n "s/.* ${key}=\"\\([0-9][0-9]*\\)\".*/\\1/p")"
    if [[ -n "${value}" ]]; then
        printf '%s\n' "${value}"
    else
        printf '0\n'
    fi
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --serial)
                SERIAL="${2:-}"
                if [[ -z "${SERIAL}" ]]; then
                    echo "Error: --serial requires a value." >&2
                    exit 2
                fi
                shift 2
                ;;
            --class)
                TEST_CLASS="${2:-}"
                if [[ -z "${TEST_CLASS}" ]]; then
                    echo "Error: --class requires a value." >&2
                    exit 2
                fi
                shift 2
                ;;
            --)
                shift
                while [[ $# -gt 0 ]]; do
                    EXTRA_GRADLE_ARGS+=("$1")
                    shift
                done
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                echo "Error: Unknown argument: $1" >&2
                usage
                exit 2
                ;;
        esac
    done
}

parse_args "$@"

if ! command -v adb >/dev/null 2>&1; then
    echo "Error: adb was not found in PATH." >&2
    exit 1
fi

if [[ ! -x "${PROJECT_DIR}/gradlew" ]]; then
    echo "Error: gradlew not found or not executable at ${PROJECT_DIR}/gradlew" >&2
    exit 1
fi

mapfile -t CONNECTED_DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" {print $1}')
if [[ ${#CONNECTED_DEVICES[@]} -eq 0 ]]; then
    echo "Error: No connected Android devices/emulators." >&2
    exit 1
fi

TARGET_SERIALS=()
if [[ -n "${SERIAL}" ]]; then
    if ! contains_value "${SERIAL}" "${CONNECTED_DEVICES[@]}"; then
        echo "Error: Requested --serial '${SERIAL}' is not connected." >&2
        echo "Connected devices:"
        printf '  %s\n' "${CONNECTED_DEVICES[@]}"
        exit 1
    fi
    TARGET_SERIALS=("${SERIAL}")
else
    mapfile -t EMULATOR_DEVICES < <(printf '%s\n' "${CONNECTED_DEVICES[@]}" | awk '/^emulator-/ {print $0}')

    if [[ ${#EMULATOR_DEVICES[@]} -gt 0 ]]; then
        TARGET_SERIALS=("${EMULATOR_DEVICES[@]}")
        if [[ ${#TARGET_SERIALS[@]} -eq 1 ]]; then
            echo "Auto-selected emulator: ${TARGET_SERIALS[0]}"
        else
            echo "Auto-selected all connected emulators:"
            printf '  %s\n' "${TARGET_SERIALS[@]}"
        fi
    elif [[ ${#CONNECTED_DEVICES[@]} -eq 1 ]]; then
        TARGET_SERIALS=("${CONNECTED_DEVICES[0]}")
        echo "No emulator detected; using only connected device: ${TARGET_SERIALS[0]}"
    else
        echo "Error: Multiple non-emulator devices are connected; use --serial to choose one." >&2
        echo "Connected devices:"
        printf '  %s\n' "${CONNECTED_DEVICES[@]}"
        exit 1
    fi
fi

CMD=("${PROJECT_DIR}/gradlew" "${GRADLE_TASK}" "--console=plain")
if [[ -n "${TEST_CLASS}" ]]; then
    CMD+=("-Pandroid.testInstrumentationRunnerArguments.class=${TEST_CLASS}")
fi
if [[ ${#EXTRA_GRADLE_ARGS[@]} -gt 0 ]]; then
    CMD+=("${EXTRA_GRADLE_ARGS[@]}")
fi

OVERALL_GRADLE_EXIT=0
OVERALL_TOTAL=0
OVERALL_PASSED=0
OVERALL_FAILED=0
OVERALL_ERRORS=0
OVERALL_SKIPPED=0

run_for_serial() {
    local serial="$1"
    local marker_file
    local log_file
    local gradle_exit
    local total=0
    local failed=0
    local errors=0
    local skipped=0
    local passed=0
    local t
    local f
    local e
    local s

    marker_file="$(mktemp)"
    log_file="/tmp/cal-date-widget-connected-tests-${serial}-$(date +%Y%m%d-%H%M%S).log"

    echo
    echo "Target device: ${serial}"
    echo "Running command:"
    printf '  %q' "${CMD[@]}"
    printf '\n'
    echo "Gradle output log: ${log_file}"

    set +e
    ANDROID_SERIAL="${serial}" "${CMD[@]}" 2>&1 | tee "${log_file}"
    gradle_exit=${PIPESTATUS[0]}
    set -e

    mapfile -t RESULT_XML_FILES < <(find "${RESULTS_ROOT}" -type f -name 'TEST-*.xml' -newer "${marker_file}" 2>/dev/null | sort)

    if [[ ${#RESULT_XML_FILES[@]} -gt 0 ]]; then
        for xml_file in "${RESULT_XML_FILES[@]}"; do
            suite_line="$(grep -m1 '<testsuite ' "${xml_file}" || true)"
            if [[ -z "${suite_line}" ]]; then
                continue
            fi
            t="$(extract_attr "${suite_line}" "tests")"
            f="$(extract_attr "${suite_line}" "failures")"
            e="$(extract_attr "${suite_line}" "errors")"
            s="$(extract_attr "${suite_line}" "skipped")"
            total=$((total + t))
            failed=$((failed + f))
            errors=$((errors + e))
            skipped=$((skipped + s))
        done
    fi

    passed=$((total - failed - errors - skipped))
    if [[ ${passed} -lt 0 ]]; then
        passed=0
    fi

    echo
    echo "Instrumented Test Summary (${serial})"
    echo "  Total:   ${total}"
    echo "  Passed:  ${passed}"
    echo "  Failed:  ${failed}"
    echo "  Errors:  ${errors}"
    echo "  Skipped: ${skipped}"
    echo "  Gradle exit code: ${gradle_exit}"
    if [[ ${#RESULT_XML_FILES[@]} -gt 0 ]]; then
        echo "  Result files parsed: ${#RESULT_XML_FILES[@]}"
    else
        echo "  Result files parsed: 0"
        echo "  Note: No new androidTest XML results were found for this run."
    fi

    OVERALL_TOTAL=$((OVERALL_TOTAL + total))
    OVERALL_PASSED=$((OVERALL_PASSED + passed))
    OVERALL_FAILED=$((OVERALL_FAILED + failed))
    OVERALL_ERRORS=$((OVERALL_ERRORS + errors))
    OVERALL_SKIPPED=$((OVERALL_SKIPPED + skipped))

    if [[ ${gradle_exit} -ne 0 && ${OVERALL_GRADLE_EXIT} -eq 0 ]]; then
        OVERALL_GRADLE_EXIT=${gradle_exit}
    fi
}

for target_serial in "${TARGET_SERIALS[@]}"; do
    run_for_serial "${target_serial}"
done

if [[ ${#TARGET_SERIALS[@]} -gt 1 ]]; then
    echo
    echo "Overall Summary (${#TARGET_SERIALS[@]} emulators)"
    echo "  Total:   ${OVERALL_TOTAL}"
    echo "  Passed:  ${OVERALL_PASSED}"
    echo "  Failed:  ${OVERALL_FAILED}"
    echo "  Errors:  ${OVERALL_ERRORS}"
    echo "  Skipped: ${OVERALL_SKIPPED}"
    echo "  Gradle exit code: ${OVERALL_GRADLE_EXIT}"
fi

if [[ ${OVERALL_GRADLE_EXIT} -ne 0 ]]; then
    exit "${OVERALL_GRADLE_EXIT}"
fi

if [[ $((OVERALL_FAILED + OVERALL_ERRORS)) -gt 0 ]]; then
    exit 1
fi

exit 0
