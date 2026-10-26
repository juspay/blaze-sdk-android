set -euo pipefail

readme="${README:-README.md}"
sample_dir="${SAMPLE_DIR:-app/src/main}"

if [ ! -f "$readme" ]; then
  echo "$readme not found; run this from the repository root" >&2
  exit 1
fi
if [ ! -d "$sample_dir" ]; then
  echo "$sample_dir not found; set SAMPLE_DIR to the sample app's sources" >&2
  exit 1
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir "$work/readme" "$work/sample"

awk -v out="$work/readme" '
  function fail(msg) { printf "%s:%d: %s\n", FILENAME, FNR, msg > "/dev/stderr"; failed = 1; exit 1 }
  tag != "" && !infence {
    if ($0 !~ /^[ \t]*```/) fail("<!-- guide:" tag " --> must be followed by a code block")
    opened = FNR
    infence = 1
    next
  }
  infence {
    if ($0 ~ /^[ \t]*```[ \t]*$/) { close(out "/" tag); tag = ""; infence = 0; next }
    print > (out "/" tag)
    next
  }
  /^[ \t]*<!-- guide:[A-Za-z0-9][A-Za-z0-9_.-]* -->[ \t]*$/ {
    tag = $0
    sub(/^[ \t]*<!-- guide:/, "", tag)
    sub(/ -->[ \t]*$/, "", tag)
    if (tag in seen) fail("guide:" tag " is used more than once")
    seen[tag] = 1
    tagged = FNR
    printf "" > (out "/" tag)
  }
  END {
    if (failed) exit 1
    if (tag != "" && !infence) { printf "%s:%d: <!-- guide:%s --> must be followed by a code block\n", FILENAME, tagged, tag > "/dev/stderr"; exit 1 }
    if (tag != "") { printf "%s:%d: the guide:%s code block opened here has no closing ```\n", FILENAME, opened, tag > "/dev/stderr"; exit 1 }
  }
' "$readme"

while IFS= read -r -d '' source; do
  awk -v out="$work/sample" '
    function fail(msg) { printf "%s:%d: %s\n", FILENAME, FNR, msg > "/dev/stderr"; failed = 1; exit 1 }
    /^[ \t]*\/\/ region guide:[A-Za-z0-9][A-Za-z0-9_.-]*[ \t]*$/ {
      if (name != "") fail("region guide:" name " is still open; regions cannot be nested")
      name = $0
      sub(/^[ \t]*\/\/ region guide:/, "", name)
      sub(/[ \t]*$/, "", name)
      path = out "/" name
      if (system("test -e \"" path "\"") == 0) fail("region guide:" name " is defined more than once")
      printf "" > path
      opened = FNR
      next
    }
    name != "" && /^[ \t]*\/\/ endregion/ { close(path); name = ""; next }
    name != "" { print > path }
    END {
      if (failed) exit 1
      if (name != "") { printf "%s:%d: region guide:%s opened here has no // endregion\n", FILENAME, opened, name > "/dev/stderr"; exit 1 }
    }
  ' "$source"
done < <(find "$sample_dir" -type f \( -name '*.kt' -o -name '*.java' -o -name '*.swift' \) -print0)

normalize() {
  awk '
    { line[NR] = $0 }
    END {
      first = 1
      last = NR
      while (first <= last && line[first] ~ /^[ \t]*$/) first++
      while (last >= first && line[last] ~ /^[ \t]*$/) last--
      indent = -1
      for (i = first; i <= last; i++) {
        if (line[i] ~ /^[ \t]*$/) continue
        match(line[i], /^[ \t]*/)
        if (indent < 0 || RLENGTH < indent) indent = RLENGTH
      }
      if (indent < 0) indent = 0
      for (i = first; i <= last; i++) {
        text = substr(line[i], indent + 1)
        sub(/[ \t]+$/, "", text)
        print text
      }
    }
  ' "$1"
}

checked=0
failed=0
for snippet in "$work"/readme/*; do
  [ -e "$snippet" ] || continue
  name=$(basename "$snippet")
  if [ ! -e "$work/sample/$name" ]; then
    echo "$readme: guide:$name has no '// region guide:$name' in $sample_dir" >&2
    failed=1
    continue
  fi
  checked=$((checked + 1))
  normalize "$snippet" > "$work/readme.txt"
  normalize "$work/sample/$name" > "$work/sample.txt"
  if ! diff -u "$work/readme.txt" "$work/sample.txt" > "$work/diff.txt"; then
    echo "$readme: guide:$name differs from its region in $sample_dir (- README, + sample):" >&2
    tail -n +3 "$work/diff.txt" >&2
    failed=1
  fi
done

if [ "$failed" -ne 0 ]; then
  echo "Update the README example or the sample code so they match." >&2
  exit 1
fi
if [ "$checked" -eq 0 ]; then
  echo "$readme has no <!-- guide:<name> --> code blocks; nothing to check."
else
  echo "$checked README code example(s) match $sample_dir."
fi
