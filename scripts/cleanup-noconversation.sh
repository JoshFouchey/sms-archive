#!/usr/bin/env bash
# Finds media files in <media root>/_noconversation that no message references and,
# with --apply, moves them to a quarantine folder next to it (nothing is deleted).
#
# Background: before the fix in ImportService, re-importing an overlapping backup wrote
# a fresh copy of every MMS attachment (plus thumbnail) to _noconversation for each
# duplicate message and never removed it.
#
# Usage (run from the directory containing docker-compose.yml):
#   scripts/cleanup-noconversation.sh            # dry run: report only
#   scripts/cleanup-noconversation.sh --apply    # move unreferenced files to quarantine
#
# Safety:
#   - A file is kept if it - or the media file it is a thumbnail of - is referenced by
#     message_parts.file_path or any messages.media "parts[].filePath" under _noconversation.
#   - Files modified in the last MIN_AGE_MINUTES (default 60) are never touched, so an
#     import that is running right now is safe. Still, avoid running during an import.
#   - Aborts if the database returns no media references at all.
#   - Take a backup first. After --apply, check the gallery; then delete the quarantine
#     folder, or move its files back with the command printed at the end.
set -euo pipefail

APPLY=false
case "${1:-}" in
  --apply) APPLY=true ;;
  "") ;;
  *) echo "usage: $0 [--apply]" >&2; exit 2 ;;
esac
MIN_AGE_MINUTES="${MIN_AGE_MINUTES:-60}"

keep_list="$(mktemp)"
trap 'rm -f "$keep_list"' EXIT

psql_db() { docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAq'; }

total_refs="$(psql_db <<'SQL'
SELECT count(*) FROM message_parts WHERE file_path IS NOT NULL;
SQL
)"
if [ "${total_refs:-0}" -eq 0 ]; then
  echo "Refusing to continue: the database returned no media references at all." >&2
  exit 1
fi

# Only references that point INTO _noconversation keep a file there. (Relocated media keeps
# its file name, so the first leaked duplicate shares a name with a file in a conversation dir.)
psql_db > "$keep_list" <<'SQL'
SELECT regexp_replace(file_path, '^.*/', '') FROM message_parts
WHERE file_path ~ '(^|/)_noconversation/[^/]+$'
UNION
SELECT regexp_replace(e->>'filePath', '^.*/', '')
FROM messages m, jsonb_array_elements(m.media->'parts') e
WHERE jsonb_typeof(m.media->'parts') = 'array' AND e->>'filePath' ~ '(^|/)_noconversation/[^/]+$';
SQL
sed -i.bak '/^$/d' "$keep_list" && rm -f "$keep_list.bak"

echo "Media references in database: $total_refs ($(wc -l < "$keep_list" | tr -d ' ') point into _noconversation)"

# The reference list can be megabytes, so it goes over stdin; the script is passed as an argument.
read -r -d '' REMOTE_SCRIPT <<'SH' || true
set -eu
root="${SMSARCHIVE_MEDIA_ROOT:-/app/media/messages}"
dir="$root/_noconversation"
if [ ! -d "$dir" ]; then echo "No $dir - nothing to do."; exit 0; fi

keep="$(mktemp)"; cand="$(mktemp)"; orphans="$(mktemp)"
trap 'rm -f "$keep" "$cand" "$orphans"' EXIT
cat > "$keep"

find "$dir" -maxdepth 1 -type f -mmin +"$MIN_AGE" -printf '%s\t%f\n' > "$cand"
# Keep each referenced name and its thumbnail (<stem>_thumb.jpg, see ThumbnailService.deriveStemThumbnail)
awk -F'\t' '
  FILENAME == ARGV[1] { keep[$0] = 1; stem = $0; sub(/\.[^.]*$/, "", stem); keep[stem "_thumb.jpg"] = 1; next }
  !($2 in keep)
' "$keep" "$cand" > "$orphans"

total=$(find "$dir" -maxdepth 1 -type f | wc -l)
n=$(wc -l < "$orphans")
bytes=$(awk -F'\t' '{ s += $1 } END { printf "%.0f", s }' "$orphans")
human=$(awk -v b="$bytes" 'BEGIN { split("B KB MB GB TB", u); i = 1; while (b >= 1024 && i < 5) { b /= 1024; i++ } printf "%.1f %s", b, u[i] }')
echo "Files in $dir: $total"
echo "Unreferenced (older than ${MIN_AGE} min): $n files, $human"

if [ "$n" -eq 0 ]; then exit 0; fi
echo "Examples:"; head -n 5 "$orphans" | cut -f2 | sed 's/^/  /'

if [ "$APPLY" != "true" ]; then
  echo
  echo "Dry run - nothing changed. Re-run with --apply to move these files to quarantine."
  exit 0
fi

q="$root/_quarantine-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$q"
moved=0; failed=0
while IFS="$(printf '\t')" read -r _ name; do
  if mv "$dir/$name" "$q/" 2>/dev/null; then moved=$((moved + 1)); else failed=$((failed + 1)); fi
done < "$orphans"
echo
echo "Moved $moved files to $q ($failed failed)."
echo "Check the app, then either delete it:"
echo "  docker compose exec app rm -rf '$q'"
echo "or undo:"
echo "  docker compose exec app sh -c 'find \"$q\" -type f -exec mv -t \"$dir\" {} +'"
SH

docker compose exec -T -e APPLY="$APPLY" -e MIN_AGE="$MIN_AGE_MINUTES" app sh -c "$REMOTE_SCRIPT" < "$keep_list"
