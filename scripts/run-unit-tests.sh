#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RESULTS_ROOT="${PROJECT_DIR}/app/build/test-results"

TASK=":app:test"
TEST_FILTER=""
FORCE_RERUN=false
EXTRA_GRADLE_ARGS=()

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

usage() {
    cat <<'EOF'
Usage:
  scripts/run-unit-tests.sh [--task <gradle-task>] [--tests <fqcn[#method]>] [--force] [-- <extra-gradle-args>]

Description:
  Runs JVM unit tests and prints a concise summary:
  Total / Passed / Failed / Errors / Skipped

Defaults:
  --task :app:test   (runs all unit-test variants)

Options:
  --task TASK        Gradle test task (examples: :app:test, :app:testDebugUnitTest)
  --tests FILTER     Gradle --tests filter (class or class#method)
  --force            Add --rerun-tasks
  -h, --help         Show this help

Examples:
  scripts/run-unit-tests.sh
  scripts/run-unit-tests.sh --task :app:testDebugUnitTest
  scripts/run-unit-tests.sh --tests ai.dcar.caldatewidget.PrefsManagerTest
  scripts/run-unit-tests.sh --tests ai.dcar.caldatewidget.PrefsManagerTest#someMethod
EOF
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

while [[ $# -gt 0 ]]; do
    case "$1" in
        --task)
            TASK="${2:-}"
            if [[ -z "${TASK}" ]]; then
                echo "Error: --task requires a value." >&2
                exit 2
            fi
            shift 2
            ;;
        --tests)
            TEST_FILTER="${2:-}"
            if [[ -z "${TEST_FILTER}" ]]; then
                echo "Error: --tests requires a value." >&2
                exit 2
            fi
            shift 2
            ;;
        --force)
            FORCE_RERUN=true
            shift
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

if [[ ! -x "${PROJECT_DIR}/gradlew" ]]; then
    echo "Error: gradlew not found or not executable at ${PROJECT_DIR}/gradlew" >&2
    exit 1
fi

MARKER_FILE="$(mktemp)"
LOG_FILE="/tmp/cal-date-widget-unit-tests-$(date +%Y%m%d-%H%M%S).log"

CMD=("${PROJECT_DIR}/gradlew" "${TASK}" "--console=plain")
if [[ "${FORCE_RERUN}" == "true" ]]; then
    CMD+=("--rerun-tasks")
fi
if [[ -n "${TEST_FILTER}" ]]; then
    CMD+=("--tests" "${TEST_FILTER}")
fi
if [[ ${#EXTRA_GRADLE_ARGS[@]} -gt 0 ]]; then
    CMD+=("${EXTRA_GRADLE_ARGS[@]}")
fi

echo -e "${BLUE}Running unit tests...${NC}"
printf '  Task: %s\n' "${TASK}"
if [[ -n "${TEST_FILTER}" ]]; then
    printf '  Filter: %s\n' "${TEST_FILTER}"
fi
if [[ "${FORCE_RERUN}" == "true" ]]; then
    echo "  Rerun: enabled"
fi
echo "  Gradle output log: ${LOG_FILE}"
echo "  Command:"
printf '    %q' "${CMD[@]}"
printf '\n'

set +e
"${CMD[@]}" 2>&1 | tee "${LOG_FILE}"
GRADLE_EXIT=${PIPESTATUS[0]}
set -e

TOTAL=0
FAILED=0
ERRORS=0
SKIPPED=0
PASSED=0
FAILED_TESTS=()

mapfile -t RESULT_XML_FILES < <(find "${RESULTS_ROOT}" -type f -name 'TEST-*.xml' -newer "${MARKER_FILE}" 2>/dev/null | sort)
if [[ ${#RESULT_XML_FILES[@]} -eq 0 ]]; then
    mapfile -t RESULT_XML_FILES < <(find "${RESULTS_ROOT}" -type f -name 'TEST-*.xml' 2>/dev/null | sort)
fi

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

        TOTAL=$((TOTAL + t))
        FAILED=$((FAILED + f))
        ERRORS=$((ERRORS + e))
        SKIPPED=$((SKIPPED + s))

        if [[ $((f + e)) -gt 0 ]]; then
            while IFS= read -r failed_name; do
                FAILED_TESTS+=("${failed_name}")
            done < <(
                awk '
                    /<testcase / {
                        in_case = 1
                        failed = 0
                        name = ""
                        class = ""
                        if (match($0, /name="[^"]+"/)) {
                            name = substr($0, RSTART + 6, RLENGTH - 7)
                        }
                        if (match($0, /classname="[^"]+"/)) {
                            class = substr($0, RSTART + 11, RLENGTH - 12)
                        }
                        if ($0 ~ /\/>/) {
                            in_case = 0
                        }
                    }
                    /<failure|<error/ {
                        if (in_case) {
                            failed = 1
                        }
                    }
                    /<\/testcase>/ {
                        if (in_case && failed) {
                            if (class != "" && name != "") {
                                print class "#" name
                            } else if (name != "") {
                                print name
                            }
                        }
                        in_case = 0
                        failed = 0
                    }
                ' "${xml_file}"
            )
        fi
    done
fi

PASSED=$((TOTAL - FAILED - ERRORS - SKIPPED))
if [[ ${PASSED} -lt 0 ]]; then
    PASSED=0
fi

echo
echo "Unit Test Summary"
echo "  Total:   ${TOTAL}"
echo -e "  ${GREEN}Passed:  ${PASSED}${NC}"
if [[ ${FAILED} -gt 0 ]]; then
    echo -e "  ${RED}Failed:  ${FAILED}${NC}"
else
    echo "  Failed:  ${FAILED}"
fi
if [[ ${ERRORS} -gt 0 ]]; then
    echo -e "  ${RED}Errors:  ${ERRORS}${NC}"
else
    echo "  Errors:  ${ERRORS}"
fi
if [[ ${SKIPPED} -gt 0 ]]; then
    echo -e "  ${YELLOW}Skipped: ${SKIPPED}${NC}"
else
    echo "  Skipped: ${SKIPPED}"
fi
echo "  Gradle exit code: ${GRADLE_EXIT}"
echo "  Result files parsed: ${#RESULT_XML_FILES[@]}"

if [[ ${#FAILED_TESTS[@]} -gt 0 ]]; then
    echo
    echo -e "${RED}Failed tests:${NC}"
    for failed_test in "${FAILED_TESTS[@]}"; do
        echo "  - ${failed_test}"
    done
fi

if [[ ${GRADLE_EXIT} -ne 0 ]]; then
    exit "${GRADLE_EXIT}"
fi

if [[ $((FAILED + ERRORS)) -gt 0 ]]; then
    exit 1
fi

exit 0
