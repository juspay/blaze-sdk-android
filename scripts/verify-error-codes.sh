#!/usr/bin/env bash

set -euo pipefail
export LC_ALL=C

readonly DOC="${ERROR_CODES_DOC:-docs/error-codes.md}"
readonly CODES_FILE="${CODES_FILE:-blaze/src/main/java/in/breeze/blaze/BlazeErrorCodes.kt}"
readonly PREFIX="SDK_"

problems=0
work=""

report() {
  echo "$1" >&2
  problems=$((problems + 1))
}

require_file() {
  if [[ ! -f "$1" ]]; then
    echo "$1 not found; run this from the repository root" >&2
    exit 1
  fi
}

sdk_codes() {
  awk -v prefix="$PREFIX" '
    # Comment lines
    /^[[:space:]]*\/\// { next }

    # The enum starts here; its entries follow the opening brace.
    !inside && /enum class BlazeErrorCodes/ {
      found = 1
      inside = 1
      brace = index($0, "{")
      if (brace == 0) next
      $0 = substr($0, brace + 1)
    }

    # The entries end at the first ";" or "}".
    inside {
      line = $0
      sub(/\/\/.*/, "", line)
      ends_here = (line ~ /[;}]/)
      sub(/[;}].*/, "", line)
      count = split(line, words, /[^A-Za-z0-9_]+/)
      for (i = 1; i <= count; i++) {
        if (words[i] ~ /^[A-Z][A-Z0-9_]*$/) print prefix words[i]
      }
      if (ends_here) exit
    }

    END {
      if (!found) {
        print FILENAME ": no enum class BlazeErrorCodes" > "/dev/stderr"
        exit 1
      }
    }
  ' "$CODES_FILE" | sort -u
}

documented_codes() {
  awk '
    /^\|[[:space:]]*Code[[:space:]]*\|/ { in_table = 1; next }

    in_table && match($0, /^\|[[:space:]]*`[A-Z0-9_]+`/) {
      code = substr($0, RSTART, RLENGTH)
      gsub(/[|` \t]/, "", code)
      print code
    }

    END {
      if (!in_table) {
        print FILENAME ": no table with a Code column" > "/dev/stderr"
        exit 1
      }
    }
  ' "$DOC"
}

check_no_duplicate_rows() {
  local documented=$1 code
  while read -r code; do
    report "$DOC: $code has more than one row"
  done < <(sort "$documented" | uniq -d)
}

check_sdk_codes_documented() {
  local sdk=$1 documented=$2 code
  while read -r code; do
    report "$CODES_FILE: $code has no row in $DOC"
  done < <(comm -23 "$sdk" <(sort -u "$documented"))
}

check_sdk_rows_have_entries() {
  local sdk=$1 documented=$2 code
  while read -r code; do
    report "$DOC: $code has a row but no entry in BlazeErrorCodes"
  done < <(grep "^$PREFIX" "$documented" | sort -u | comm -23 - "$sdk")
}

main() {
  require_file "$DOC"
  require_file "$CODES_FILE"

  work=$(mktemp -d)
  trap 'rm -rf "$work"' EXIT

  sdk_codes > "$work/sdk"
  documented_codes > "$work/documented"

  check_no_duplicate_rows "$work/documented"
  check_sdk_codes_documented "$work/sdk" "$work/documented"
  check_sdk_rows_have_entries "$work/sdk" "$work/documented"

  if ((problems > 0)); then
    exit 1
  fi
  echo "$DOC matches BlazeErrorCodes (SDK codes: $(wc -l < "$work/sdk" | tr -d ' '))."
}

main "$@"
