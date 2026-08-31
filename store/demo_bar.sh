#!/usr/bin/env bash
# Clean, deterministic status bar for store screenshots.
set -e
adb shell settings put global sysui_demo_allowed 1
B="adb shell am broadcast -a com.android.systemui.demo"
$B -e command enter >/dev/null
$B -e command clock -e hhmm 1000 >/dev/null
$B -e command battery -e level 100 -e plugged false >/dev/null
$B -e command network -e wifi show -e level 4 -e mobile hide >/dev/null
$B -e command notifications -e visible false >/dev/null
echo "demo bar on"
