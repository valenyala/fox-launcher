#!/usr/bin/env bash
# The wallpaper engine's budget (task 4.8): memory before and after N changes, and the frame timing of
# each crossfade as the engine logs it. Needs a DEBUG build (its NEXT_WALLPAPER receiver and the frame
# log), Fox's live wallpaper applied, and the phone unlocked on Fox's home screen, so every change fades
# on screen. A few warm-up changes come first (the first fade fills caches); each memory reading follows a
# forced GC, so it shows what stays, not garbage waiting. Usage:
#   wallpaper.sh <label> [changes=20] [seconds between changes=3] [warm-up changes=3]
set -u
source "$(dirname "$0")/common.sh"
label=${1:-wallpaper}; changes=${2:-20}; gap=${3:-3}; warmup=${4:-3}; file=$out/$label-wallpaper.txt; : > "$file"
log() { echo "$*" | tee -a "$file"; }
next() { adb shell am broadcast -a dev.foxlauncher.debug.NEXT_WALLPAPER -p $pkg > /dev/null; sleep "$gap"; }
# SIGUSR1 makes ART collect garbage; run-as can signal a debuggable app's process.
gc() { adb shell run-as $pkg kill -10 "$(adb shell pidof $pkg)"; sleep 2; }
mem() { gc; adb shell dumpsys meminfo $pkg | grep -E "^ +(Java Heap|Native Heap|Graphics):|TOTAL (PSS|RSS):" | sed 's/^ */   /' | tee -a "$file"; }

go_home; sleep 2; require_fox_in_front
log "== $label  $(date -Iseconds)  $(adb shell getprop ro.product.model)  $changes changes, ${gap}s apart"
for i in $(seq 1 "$warmup"); do next; done
log "-- memory after $warmup warm-up changes (KB)"; mem
adb logcat -c
for i in $(seq 1 "$changes"); do
  next
  if [ $((i % 10)) -eq 0 ] || [ "$i" -eq "$changes" ]; then log "-- memory after $i changes (KB)"; mem; fi
done
log "-- crossfades (engine log)"
adb logcat -d -s FoxLauncher:D | grep -o "crossfade frames: .*" | sed 's/^crossfade frames: /   /' | tee -a "$file"
log "-- engine trouble, if any"
adb logcat -d | grep -E "lost its surface|not reaching the screen|surface had destroyed" | sort | uniq -c | sed 's/^ */   /' | tee -a "$file"
echo "saved $file"
