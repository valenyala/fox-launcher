#!/usr/bin/env bash
# A/B of the drawer opening between APKs: installs them in turn (A B A B, so thermal drift hits both)
# and records gfxinfo framestats right after each of 3 opens per round, then prints per-frame UI-thread
# and GPU work, which (unlike janky %) does not depend on the refresh rate MIUI picks.
# Usage: drawer-open-ab.sh A.apk B.apk [...]
set -u
source "$(dirname "$0")/common.sh"
for apk in "$@"; do rm -f "$out/ab-$(basename "$apk" .apk).txt"; done
for round in 1 2; do
  for apk in "$@"; do
    name=$(basename "$apk" .apk)
    adb install -r "$apk" > /dev/null
    go_home; sleep 4; require_fox_in_front
    for i in 1 2 3; do
      adb shell dumpsys gfxinfo $pkg reset > /dev/null
      adb shell input swipe 540 1900 540 900 150; sleep 0.8
      adb shell dumpsys gfxinfo $pkg framestats >> "$out/ab-$name.txt"
      adb shell input keyevent KEYCODE_HOME; sleep 1.5
    done
  done
done
for apk in "$@"; do name=$(basename "$apk" .apk); awk -v label="$name" -f "$here/frame-work.awk" "$out/ab-$name.txt"; done
