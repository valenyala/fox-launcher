#!/usr/bin/env bash
# Whole-scenario numbers for the attached phone: cold start (5 runs), gfxinfo summaries for page
# swipes and the drawer, and memory. Install the build to measure first; measure release-like
# builds (`./gradlew :app:assembleProfiling`), never debug ones. Usage: measure.sh <label>
set -u
source "$(dirname "$0")/common.sh"
label=${1:-run}; file=$out/$label.txt; : > "$file"
log() { echo "$*" | tee -a "$file"; }
stats() { adb shell dumpsys gfxinfo $pkg | grep -E "Total frames rendered|Janky frames:|50th percentile|90th percentile|95th percentile|99th percentile|Number Slow UI thread|Number Frame deadline missed:" | sed 's/^ */   /' | tee -a "$file"; }

go_home; sleep 2; require_fox_in_front
log "== $label  $(date -Iseconds)  $(adb shell getprop ro.product.model)"
log "-- cold start (am start -W TotalTime, ms)"
for i in 1 2 3 4 5; do
  adb shell am force-stop $pkg; sleep 1
  log "   run $i: $(adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME | awk '/TotalTime/ {print $2}')"
  sleep 2
done
go_home; sleep 2; require_fox_in_front
log "-- page swipes (20)"
adb shell dumpsys gfxinfo $pkg reset > /dev/null
for i in $(seq 1 10); do adb shell input swipe 900 1100 180 1100 180; sleep 0.6; adb shell input swipe 180 1100 900 1100 180; sleep 0.6; done
stats
log "-- drawer open, 8 flings, close"
adb shell dumpsys gfxinfo $pkg reset > /dev/null
adb shell input swipe 540 1900 540 900 150; sleep 1.2
for i in 1 2 3 4; do adb shell input swipe 540 1900 540 600 120; sleep 0.9; done
for i in 1 2 3 4; do adb shell input swipe 540 600 540 1900 120; sleep 0.9; done
adb shell input keyevent KEYCODE_HOME; sleep 1
stats
log "-- refresh rate the frames used (MIUI may cap it; see STATUS 2026-10-11)"
adb shell dumpsys gfxinfo $pkg framestats | awk -F, '/---PROFILEDATA---/ {i=!i; next} i && $1=="0" {c[sprintf("%.1f ms", $12/1e6)]++} END {for (k in c) printf "   %s x%d\n", k, c[k]}' | tee -a "$file"
log "-- memory (KB)"
adb shell dumpsys meminfo $pkg | grep -E "^ +(Java Heap|Native Heap|Code|Graphics):|TOTAL PSS" | sed 's/^ */   /' | tee -a "$file"
