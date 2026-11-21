#!/usr/bin/env bash
# Turns Gradle failure lines into GitHub annotations (readable via the Checks API),
# then prints the tail of the log for the Actions UI. Never fails the step itself.
set -u
LOG="${1:-build.log}"
if [ ! -f "$LOG" ]; then
  echo "::error title=Build log missing::$LOG was not produced"
  exit 0
fi
grep -E '^e: |^w: .*\.kt|FAILED|What went wrong|Execution failed|Could not |Caused by|BUILD FAILED' "$LOG" \
  | head -n 40 \
  | while IFS= read -r line; do
      msg=$(printf '%s' "$line" | tr -d '\r' | sed -e 's/%/%25/g' -e 's/\r/%0D/g' -e 's/\n/%0A/g')
      echo "::error title=Gradle::${msg}"
    done
echo "--- last 150 lines of $LOG ---"
tail -n 150 "$LOG"
exit 0
