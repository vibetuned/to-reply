#!/usr/bin/env bash
# Screenshot toolkit for 2Reply: put the app in an exact state, then capture.
PKG=com.vibetuned.to_reply
DB=/data/data/$PKG/databases/to_reply.db
SHOTS="$1"; shift 2>/dev/null || true
SP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

sql()  { adb shell "sqlite3 $DB \"$1\""; }
pid_of_title() { sql "SELECT id FROM plays WHERE title='$1';" | tr -d '\r'; }

# Park the playhead of a play at an exact ms and make it the most recent (cold start reopens it).
set_pos() { # <title> <ms>
  local id; id=$(pid_of_title "$1")
  local now; now=$(date +%s)000
  sql "INSERT OR REPLACE INTO positions (playId,positionMs,updatedAt) VALUES ('$id',$2,$now);" >/dev/null
}

restart() {
  adb shell am force-stop $PKG
  adb shell am start -n $PKG/.MainActivity >/dev/null
}

# Tap the centre of the first node whose content-desc matches $1.
tap_desc() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local center
  center=$(adb shell cat /sdcard/ui.xml | python3 "$SP_DIR/find_desc.py" "$1")
  if [ -z "$center" ]; then echo "!! no node with desc '$1'" >&2; return 1; fi
  adb shell input tap $center
  echo "tapped '$1' at $center"
}

has_desc() { # true if a node with this content-desc exists
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml | grep -q "content-desc=\"$1\""
}

play_now() { adb shell cmd media_session dispatch play >/dev/null 2>&1; }

shot() { adb exec-out screencap -p > "$SHOTS/$1.png"; echo "shot -> $SHOTS/$1.png"; }

# Force a text-drill toggle to a desired state, identified by its two content-descs.
# ensure_toggle "Hide my lines" "Show my lines" on|off
ensure_toggle() {
  local off_desc="$1" on_desc="$2" want="$3"
  if [ "$want" = "on" ]; then
    has_desc "$on_desc" && return 0
    tap_desc "$off_desc"
  else
    has_desc "$off_desc" && return 0
    tap_desc "$on_desc"
  fi
  sleep 1
}

# Start playback via the docked transport, so the bar shows the live "playing" state.
start_playing() { has_desc "Pause" || tap_desc "Play"; }
pause_playing() { has_desc "Play" || tap_desc "Pause"; }
