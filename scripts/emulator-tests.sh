#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PACKAGE="ai.dcar.caldatewidget"
RUNNER="androidx.test.runner.AndroidJUnitRunner"

SERIAL=""
TEST_CLASS=""

usage() {
    cat <<'EOF'
Usage:
  scripts/emulator-tests.sh [--serial <adb-serial>] [--class <fqcn[#method]>]

Description:
  Runs Android instrumented tests while preserving installed widgets.
  Uses in-place app install (installDebug) instead of Gradle's
  connectedDebugAndroidTest which uninstalls and reinstalls the app,
  wiping widget state.

Options:
  --serial SERIAL   Target a specific connected device/emulator
  --class CLASS     Run one test class or method (e.g. ai.dcar.caldatewidget.ExampleTest#works)
  -h, --help        Show this help

Examples:
  scripts/emulator-tests.sh
  scripts/emulator-tests.sh --serial emulator-5554
  scripts/emulator-tests.sh --class ai.dcar.caldatewidget.ExampleTest
  scripts/emulator-tests.sh --class ai.dcar.caldatewidget.ExampleTest#works
EOF
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

ADB_CMD=(adb)
TARGET_SERIALS=()

if [[ -n "${SERIAL}" ]]; then
    found=0
    for dev in "${CONNECTED_DEVICES[@]}"; do
        if [[ "${dev}" == "${SERIAL}" ]]; then
            found=1
            break
        fi
    done
    if [[ ${found} -eq 0 ]]; then
        echo "Error: Requested --serial '${SERIAL}' is not connected." >&2
        echo "Connected devices:"
        printf '  %s\n' "${CONNECTED_DEVICES[@]}"
        exit 1
    fi
    TARGET_SERIALS=("${SERIAL}")
    ADB_CMD=(adb -s "${SERIAL}")
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

OVERALL_TOTAL=0
OVERALL_PASSED=0
OVERALL_FAILED=0
OVERALL_ERRORS=0

run_for_serial() {
    local serial="$1"
    local adb_serial=(adb -s "${serial}")
    local total=0
    local passed=0
    local failed=0
    local errors=0

    echo
    echo "=== Target device: ${serial} ==="

    echo "Building and installing app (in-place update, preserves widgets)..."
    set +e
    ANDROID_SERIAL="${serial}" "${PROJECT_DIR}/gradlew" installDebug installDebugAndroidTest --console=plain 2>&1
    local build_exit=$?
    set -e
    if [[ ${build_exit} -ne 0 ]]; then
        echo "Error: Build or install failed (exit ${build_exit})." >&2
        OVERALL_ERRORS=$((OVERALL_ERRORS + 1))
        return 1
    fi

    echo "Running instrumented tests..."
    local instr_args=(-w)
    if [[ -n "${TEST_CLASS}" ]]; then
        instr_args+=(-e class "${TEST_CLASS}")
    fi

    set +e
    local instr_output
    instr_output="$("${adb_serial[@]}" shell am instrument "${instr_args[@]}" "${PACKAGE}.test/${RUNNER}" 2>&1)"
    local instr_exit=$?
    set -e

    echo "${instr_output}"

    # Parse am instrument output for summary
    # Format: "OK (X tests)" or failures listed before
    if echo "${instr_output}" | grep -q "^OK ("; then
        local test_count
        test_count=$(echo "${instr_output}" | grep "^OK (" | sed 's/^OK (\([0-9]*\) tests\?)/\1/')
        total=${test_count:-0}
        passed=${total}
        failed=0
    else
        # Count failures from output
        local fail_lines
        fail_lines=$(echo "${instr_output}" | grep -c "^FAILED " || true)
        failed=${fail_lines:-0}
        # Try to extract total from "Tests run: X" line
        local run_line
        run_line=$(echo "${instr_output}" | grep -oP 'Tests run: \K[0-9]+' | tail -1 || true)
        if [[ -n "${run_line}" ]]; then
            total=${run_line}
            passed=$((total - failed))
        else
            total=0
            passed=0
        fi
    fi

    if [[ ${instr_exit} -ne 0 ]]; then
        errors=$((errors + 1))
    fi

    echo
    echo "Instrumented Test Summary (${serial})"
    echo "  Total:   ${total}"
    echo "  Passed:  ${passed}"
    echo "  Failed:  ${failed}"
    echo "  Errors:  ${errors}"

    OVERALL_TOTAL=$((OVERALL_TOTAL + total))
    OVERALL_PASSED=$((OVERALL_PASSED + passed))
    OVERALL_FAILED=$((OVERALL_FAILED + failed))
    OVERALL_ERRORS=$((OVERALL_ERRORS + errors))
}

for target_serial in "${TARGET_SERIALS[@]}"; do
    run_for_serial "${target_serial}"
done

if [[ ${#TARGET_SERIALS[@]} -gt 1 ]]; then
    echo
    echo "Overall Summary (${#TARGET_SERIALS[@]} devices)"
    echo "  Total:   ${OVERALL_TOTAL}"
    echo "  Passed:  ${OVERALL_PASSED}"
    echo "  Failed:  ${OVERALL_FAILED}"
    echo "  Errors:  ${OVERALL_ERRORS}"
fi

if [[ $((OVERALL_FAILED + OVERALL_ERRORS)) -gt 0 ]]; then
    exit 1
fi

exit 0