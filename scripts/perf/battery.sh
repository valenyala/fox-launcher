#!/usr/bin/env bash
# Fox's share of the battery over a quiet stretch (task 8.4). Unplug the phone, run this, turn the screen off, and
# leave it: after <minutes> it prints what Android charged to Fox (CPU, wakeups, wakelocks, jobs, alarms) and the
# WorkManager jobs with their constraints. Expect near zero from Fox itself beyond the wallpaper interval job.
# Usage: battery.sh <label> [minutes=60]   (the attached phone; never an emulator, it reports no power data)
set -u
source "$(dirname "$0")/common.sh"
label=${1:-run}; minutes=${2:-60}; file=$out/battery-$label.txt; : > "$file"
log() { echo "$*" | tee -a "$file"; }

case "$(adb get-serialno)" in emulator-*) echo "Emulators report no battery data; use the phone." >&2; exit 1 ;; esac
if adb shell dumpsys battery | grep -qE "(AC|USB|Wireless|Dock) powered: true"; then
  echo "The phone is charging; unplug it first (charging skews the stats)." >&2; exit 1
fi

uid=$(adb shell dumpsys package $pkg | grep -m1 -oE "userId=[0-9]+" | cut -d= -f2)
log "== $label  $(date -Iseconds)  $(adb shell getprop ro.product.model)  uid $uid  $minutes min"
log "-- battery level before: $(adb shell dumpsys battery | awk '/level:/ {print $2}')%"
adb shell dumpsys batterystats --reset > /dev/null
echo "Stats reset. Turn the screen off now; reading in $minutes min."
sleep $((minutes * 60))

log "-- wakefulness at the end: $(adb shell dumpsys power | grep -m1 -oE 'mWakefulness=[A-Za-z]+')"
log "-- battery level after: $(adb shell dumpsys battery | awk '/level:/ {print $2}')%"
log "-- Fox in batterystats (CPU, wakeups, wakelocks, jobs, alarms)"
adb shell dumpsys batterystats $pkg | grep -E "Estimated power use|Uid u0a|Cpu|Wake|wakelock|Job|Alarm|Foreground|Total cpu" | sed 's/^ */   /' | tee -a "$file"
log "-- checkin lines for uid $uid"
adb shell dumpsys batterystats --checkin | grep -E "^9,$uid," | cut -c1-200 | sed 's/^/   /' | tee -a "$file"
log "-- jobs (WorkManager's JobScheduler jobs and their constraints)"
adb shell dumpsys jobscheduler $pkg | grep -E "JOB #|Required constraints|Satisfied constraints|Run time|Last run|Execution" | sed 's/^ */   /' | tee -a "$file"
log "-- alarms"
adb shell dumpsys alarm | grep -B1 -A3 "$pkg" | head -20 | sed 's/^/   /' | tee -a "$file"
echo "Saved to $file"
