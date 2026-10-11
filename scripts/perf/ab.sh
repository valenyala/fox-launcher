#!/usr/bin/env bash
# A/B of one scenario between APKs: installs them in turn (A B A B, so thermal drift hits both) and
# records gfxinfo framestats around each of 3 repetitions per round, then prints per-frame UI-thread
# and GPU work, which (unlike janky %) does not depend on the refresh rate MIUI picks.
# Scenarios: open (drawer opening), scroll (drawer flings), pages (home page swipes).
# Usage: ab.sh <open|scroll|pages> A.apk B.apk [...]
set -u
source "$(dirname "$0")/common.sh"
scenario=$1; shift
run_once() {
  case "$scenario" in
    open) adb shell input swipe 540 1900 540 900 150; sleep 0.8 ;;
    scroll) adb shell input swipe 540 1900 540 600 120; sleep 0.5; adb shell input swipe 540 600 540 1900 120; sleep 0.5 ;;
    pages) adb shell input swipe 900 1100 180 1100 180; sleep 0.4; adb shell input swipe 180 1100 900 1100 180; sleep 0.4 ;;
  esac
}
for apk in "$@"; do rm -f "$out/ab-$scenario-$(basename "$apk" .apk).txt"; done
for round in 1 2; do
  for apk in "$@"; do
    name=$(basename "$apk" .apk)
    adb install -r "$apk" > /dev/null
    go_home; sleep 4; require_fox_in_front
    [ "$scenario" = scroll ] && { adb shell input swipe 540 1900 540 900 150; sleep 1.2; }
    for i in 1 2 3; do
      adb shell dumpsys gfxinfo $pkg reset > /dev/null
      run_once
      adb shell dumpsys gfxinfo $pkg framestats >> "$out/ab-$scenario-$name.txt"
      [ "$scenario" = open ] && { adb shell input keyevent KEYCODE_HOME; sleep 1.5; }
    done
    adb shell input keyevent KEYCODE_HOME; sleep 1
  done
done
for apk in "$@"; do name=$(basename "$apk" .apk); awk -v label="$scenario $name" -f "$here/frame-work.awk" "$out/ab-$scenario-$name.txt"; done
