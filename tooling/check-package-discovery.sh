#!/bin/bash
# Runs the EXACT jq expression from .github/workflows/ci.yml against the
# real find|sed|sort output, so the CI discovery step can be verified locally
# on a machine that does not have jq (jq is preinstalled on ubuntu runners).
set -euo pipefail
cd "$(dirname "$0")/.."

JQ_EXPR=$(python3 - <<'PY'
import re, yaml
run = yaml.safe_load(open('.github/workflows/ci.yml'))['jobs']['discover_packages']['steps'][1]['run']
# Take the single-quoted jq filter: jq -R -s -c '<filter>'
m = re.search(r"jq\s+-R\s+-s\s+-c\s+'(.*?)'", run, re.S)
if not m:
    raise SystemExit('ERROR: could not locate the jq filter in ci.yml')
# Join the YAML-folded lines back into the logical one-liner.
print(' '.join(m.group(1).split()))
PY
)

paths=$(find packages -maxdepth 2 -name pubspec.yaml -not -path '*/.*' \
  | sed 's|/pubspec.yaml$||' \
  | sort)

echo "find|sed|sort produced:"
printf '%s\n' "$paths" | sed 's/^/  /'
echo
echo "jq expression extracted from ci.yml:"
printf '%s\n' "$JQ_EXPR" | sed 's/^/  /'
echo

if command -v jq >/dev/null 2>&1; then
  echo "real jq output:"
  printf '%s\n' "$paths" | jq -R -s -c "$JQ_EXPR" | sed 's/^/  /'
else
  echo "jq not installed locally; evaluating the same transform with python3:"
  PATHS="$paths" JQ_EXPR="$JQ_EXPR" python3 - <<'PY'
import json, os
expr = os.environ['JQ_EXPR']
if 'map({path: ., name: (split("/") | last)})' not in expr:
    raise SystemExit('ERROR: ci.yml jq expression changed; update this checker.')
paths = [l.strip() for l in os.environ['PATHS'].splitlines() if l.strip()]
print('  ' + json.dumps([{'path': p, 'name': p.split('/')[-1]} for p in paths]))
PY
fi
