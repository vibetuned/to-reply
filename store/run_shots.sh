#!/usr/bin/env bash
# Capture the 4 Play Store screenshots on the connected device.
# Usage: run_shots.sh <outdir> <prefix>
set -u
SP="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$1"; PFX="$2"
mkdir -p "$OUT"
source "$SP/cap.sh" "$OUT"
"$SP/demo_bar.sh" >/dev/null
PLAY="The Proposal"
MY_OFF="Hide my lines";      MY_ON="Show my lines"
OT_OFF="Hide others' lines"; OT_ON="Show others' lines"

# ---- 1. Library ---------------------------------------------------------------
# A cold start reopens the last play on the training screen; Back drops to the
# library with the mini-player still docked.
set_pos "$PLAY" 751000
restart; sleep 7
ensure_toggle "$MY_OFF" "$MY_ON" off
ensure_toggle "$OT_OFF" "$OT_ON" off
adb shell input keyevent KEYCODE_BACK; sleep 3
shot "${PFX}-1-library"

# ---- 2. Training, normal ------------------------------------------------------
# CHUBUKOV's blessing (232.4-248.2s). An "others" line is under the playhead, so
# audio is live and no mute banner is up.
set_pos "$PLAY" 238000
restart; sleep 7
start_playing; sleep 2
shot "${PFX}-2-training-normal"
pause_playing

# ---- 3. Training, rehearsal mute ---------------------------------------------
# Mid-quarrel LOMOV line (784.6-793.7s): the playhead sits inside a rehearsed
# line, so playback is muted and the "Your line - speak now" banner shows.
set_pos "$PLAY" 786000
restart; sleep 7
start_playing; sleep 2
shot "${PFX}-3-training-mute"
pause_playing

# ---- 4. Training, text drill -------------------------------------------------
# Memorization drill in the Squeezer-vs-Guess argument (~1542s): LOMOV's own
# lines collapse to a name tag plus a duration-scaled gauge, the cues stay readable.
set_pos "$PLAY" 1545000
restart; sleep 7
ensure_toggle "$MY_OFF" "$MY_ON" on
start_playing; sleep 2
shot "${PFX}-4-training-drill"
pause_playing
ensure_toggle "$MY_OFF" "$MY_ON" off
echo "done -> $OUT"
