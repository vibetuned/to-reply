#!/usr/bin/env bash
# Seed the 2Reply demo plays straight into the app's private storage + Room DB,
# reproducing exactly what PlayRepository.import() would have written.
set -euo pipefail
PKG=com.vibetuned.to_reply
SRC=/home/flux/mac_share/demo/plays
SP="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA=/data/data/$PKG
NOW=$(date +%s)000

adb root >/dev/null 2>&1 || true
adb wait-for-device
adb shell am force-stop $PKG
UID_NAME=$(adb shell stat -c '%U' $DATA/databases | tr -d '\r')
echo "app user: $UID_NAME"

adb shell rm -rf "$DATA/files/plays"
adb shell "sqlite3 $DATA/databases/to_reply.db 'DELETE FROM plays; DELETE FROM positions;'"

SQL="${TMPDIR:-/tmp}/2reply-seed.sql"
: > "$SQL"

# name|role(s, unit-separated)|progress fraction|recency rank (0 = most recent)
PLAYS=(
  "proposal|LOMOV|0.34|0"
  "overtones|HETTY\x1FHARRIET|0.12|1"
  "trifles|MRS PETERS|0.62|2"
  "angel|JIMMY|0|3"
  "husbands|FAMOUS ACTRESS|0|4"
)

for row in "${PLAYS[@]}"; do
  IFS='|' read -r name roles progress rank <<< "$row"
  id=$(cat /proc/sys/kernel/random/uuid)
  m4b=$(ls "$SRC/$name"/*.m4b)
  json="$SRC/$name/play.json"
  title=$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))['title'])" "$json")
  dur=$(python3 -c "import json,sys;print(int(round(json.load(open(sys.argv[1]))['total_duration_seconds']*1000)))" "$json")
  size=$(stat -c %s "$m4b")
  imported=$((NOW - rank * 86400000))
  dir="$DATA/files/plays/$id"

  adb shell mkdir -p "$dir"
  adb push "$json" "$dir/play.json" >/dev/null
  adb push "$m4b"  "$dir/audio.m4b" >/dev/null

  # Only two of the demo recordings ship with artwork; the rest legitimately fall
  # back to the library's placeholder tile.
  cover_sql=NULL
  if [ -f "$SP/covers/cover-$name.jpg" ]; then
    adb push "$SP/covers/cover-$name.jpg" "$dir/cover.jpg" >/dev/null
    cover_sql="'$dir/cover.jpg'"
  fi

  esc_title=${title//\'/\'\'}
  esc_roles=$(printf '%b' "$roles" | sed "s/'/''/g")
  cat >> "$SQL" <<EOF
INSERT OR REPLACE INTO plays (id,title,audioPath,scriptPath,coverPath,durationMs,importedAt,fileSize,selectedSpeaker)
 VALUES ('$id','$esc_title','$dir/audio.m4b','$dir/play.json',$cover_sql,$dur,$imported,$size,'$esc_roles');
EOF
  if [ "$progress" != "0" ]; then
    pos=$(python3 -c "print(int($dur*$progress))")
    upd=$((NOW - rank * 3600000))
    echo "INSERT OR REPLACE INTO positions (playId,positionMs,updatedAt) VALUES ('$id',$pos,$upd);" >> "$SQL"
  fi
  echo "seeded $title ($dur ms, $((size/1000000))MB) -> $id"
done

adb shell chown -R "$UID_NAME:$UID_NAME" "$DATA/files"
adb shell restorecon -R "$DATA/files"
adb push "$SQL" /data/local/tmp/seed.sql >/dev/null
adb shell "sqlite3 $DATA/databases/to_reply.db < /data/local/tmp/seed.sql"
adb shell chown -R "$UID_NAME:$UID_NAME" "$DATA/databases"
adb shell restorecon -R "$DATA/databases"
echo "--- plays in DB ---"
adb shell "sqlite3 $DATA/databases/to_reply.db 'SELECT title,durationMs,coverPath IS NOT NULL AS cover,selectedSpeaker FROM plays ORDER BY importedAt DESC;'"
