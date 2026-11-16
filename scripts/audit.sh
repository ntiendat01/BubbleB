#!/usr/bin/env bash
# BubbleB milestone audit (AGENTS.md §1, §3, §8):
#  1. No human-authored source/config/test/script file over 500 physical lines (target <= 300).
#  2. No obvious hardcoded secrets.
#  3. No cleartext http:// endpoints in sources/config.
# Usage: scripts/audit.sh   (exit code != 0 on violation)
set -euo pipefail
cd "$(dirname "$0")/.."

MAX_LINES=500
TARGET_LINES=300
status=0

files=$(git ls-files --cached --others --exclude-standard \
  | grep -E '\.(kt|kts|java|xml|toml|sh|pro|properties|gradle)$' \
  | grep -vE '(^|/)build/' || true)

echo "== Line-count audit (max ${MAX_LINES}, target ${TARGET_LINES}) =="
max=0; maxfile=""
for f in $files; do
  n=$(wc -l < "$f")
  if [ "$n" -gt "$max" ]; then max=$n; maxfile=$f; fi
  if [ "$n" -gt "$MAX_LINES" ]; then echo "VIOLATION: $f has $n lines"; status=1
  elif [ "$n" -gt "$TARGET_LINES" ]; then echo "WARN (>${TARGET_LINES}): $f has $n lines"; fi
done
echo "Files checked: $(echo "$files" | wc -w); largest: $maxfile ($max lines)"

echo "== Secret audit =="
secret_re='(sk-[A-Za-z0-9_-]{16,}|Bearer [A-Za-z0-9._-]{16,}|AIza[0-9A-Za-z_-]{30,}|(api[_-]?key|apikey|secret|token)[[:space:]]*[:=][[:space:]]*"[A-Za-z0-9._-]{12,}")'
if echo "$files" | xargs grep -nEi "$secret_re" 2>/dev/null; then
  echo "VIOLATION: possible hardcoded secret"; status=1
else
  echo "No hardcoded secrets found"
fi
for f in local.properties secrets.properties; do
  if git ls-files --error-unmatch "$f" >/dev/null 2>&1; then echo "VIOLATION: $f is tracked"; status=1; fi
done

echo "== Cleartext endpoint audit =="
if echo "$files" | grep -v '^scripts/' | xargs grep -n 'http://' 2>/dev/null | grep -v 'schemas.android.com' | grep -v 'www.apache.org'; then
  echo "VIOLATION: cleartext http:// reference"; status=1
else
  echo "No cleartext endpoints found"
fi

exit $status
