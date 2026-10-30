set -euo pipefail
export LC_ALL=C

doc="${ERROR_CODES_DOC:-docs/error-codes.md}"
codes_file="${CODES_FILE:-blaze/src/main/java/in/breeze/blaze/BlazeErrorCodes.kt}"

for file in "$doc" "$codes_file"; do
  if [ ! -f "$file" ]; then
    echo "$file not found; run this from the repository root" >&2
    exit 1
  fi
done

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

sed -nE '/^[[:space:]]*\/\//d; s/.*const val [A-Z0-9_]+[[:space:]]*(:[[:space:]]*String)?[[:space:]]*=[[:space:]]*"([A-Z0-9_]+)".*/\2/p' "$codes_file" | sort -u > "$work/sdk"

awk -v rows="$work/rows" '
  BEGIN { printf "" > rows }
  /^\| *Code *\|/ { found = 1; next }
  found && match($0, /^\| *`[A-Z0-9_]+`/) { code = substr($0, RSTART, RLENGTH); gsub(/[| `]/, "", code); print code > rows }
  END { if (!found) { printf "%s: no table with a Code column\n", FILENAME > "/dev/stderr"; exit 1 } }
' "$doc"
status=0
fail() { echo "$1" >&2; status=1; }
for code in $(sort "$work/rows" | uniq -d); do
  fail "$doc: $code has more than one row in the table"
done

sort -u -o "$work/rows" "$work/rows"
grep '^SDK_' "$work/rows" > "$work/sdk_rows" || true

for code in $(grep -v '^SDK_' "$work/sdk" || true); do
  fail "$codes_file: $code must start with SDK_"
done
for code in $(comm -23 "$work/sdk" "$work/rows"); do
  fail "$codes_file: $code is sent by the SDK but has no row in $doc"
done
for code in $(comm -23 "$work/sdk_rows" "$work/sdk"); do
  fail "$doc: $code has a row but is not defined in $codes_file"
done
if [ "$status" -ne 0 ]; then
  exit 1
fi

echo "$(wc -l < "$work/rows" | tr -d ' ') error codes in $doc match $codes_file."
