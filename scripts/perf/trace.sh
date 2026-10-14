#!/usr/bin/env bash
# A system trace of Fox with composable names (task 8.2), for finding what a slow frame does. Needs the
# profiling build installed (profileable, with Compose's composition tracing): ./gradlew :app:assembleProfiling.
# Records for <seconds> while you (or another script) use Fox, then pulls build/perf/<label>.pftrace; open it at
# https://ui.perfetto.dev or query it: uv run --with perfetto --with pandas python -I <your query script>.
# Usage: trace.sh <label> [seconds=12]
set -u
source "$(dirname "$0")/common.sh"
label=${1:-trace}; seconds=${2:-12}; remote=/data/misc/perfetto-traces/fox-$label.pftrace
go_home; sleep 2; require_fox_in_front
# The handshake that turns on composable names in Fox's process (androidx.tracing.perfetto).
adb shell am broadcast -a androidx.tracing.perfetto.action.ENABLE_TRACING -n $pkg/androidx.tracing.perfetto.TracingReceiver | tail -1
cat <<CONFIG | adb shell perfetto --txt -c - -o "$remote" > /dev/null 2>&1 &
buffers: { size_kb: 65536 fill_policy: RING_BUFFER }
data_sources: { config { name: "linux.ftrace" ftrace_config {
  ftrace_events: "sched/sched_switch"
  atrace_categories: "gfx" atrace_categories: "view" atrace_categories: "input" atrace_categories: "dalvik"
  atrace_apps: "$pkg" } } }
data_sources: { config { name: "linux.process_stats" } }
data_sources: { config { name: "track_event" track_event_config { enabled_categories: "*" } } }
duration_ms: $((seconds * 1000))
CONFIG
echo "Recording ${seconds}s: use Fox now."
wait
adb pull "$remote" "$out/$label.pftrace" | tail -1
