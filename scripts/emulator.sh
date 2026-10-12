#!/usr/bin/env bash
# Android emulators for Fox. One AVD per system image in the `.#emulator` dev shell, each on a fixed
# console port, so its adb serial never changes:
#   fox-api29  emulator-5554   minSdk
#   fox-api33  emulator-5556   same API as the POCO F3; boost can still kill other apps (ADR-5)
#   fox-api37  emulator-5558   compile/target SDK
#
# Usage:
#   emulator.sh create                 create the missing AVDs (Pixel 7: 1080x2400 @ 420 dpi, close to the phone)
#   emulator.sh start <29|33|37> [--window]   boot it (headless unless --window) and wait for boot_completed
#   emulator.sh stop [<29|33|37>|all]  shut it down (default: all running fox emulators)
#   emulator.sh list                   AVDs and the ones running
#
# With the phone on adb as well, plain `adb shell` and the perf scripts need ANDROID_SERIAL=emulator-5556 (or the
# phone's serial), and connectedDebugAndroidTest runs on every attached device. AVDs live in ~/.android/avd and
# point at their system image relative to the SDK, so they survive flake updates. Logs go to build/emulator.
# Rendering is software (the emulator rejects the host GPU driver): measure performance on the phone only.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

if ! command -v emulator >/dev/null 2>&1; then
    if ! command -v nix >/dev/null 2>&1; then
        echo "fox emulator: no emulator and no nix on PATH — run 'nix develop .#emulator' first" >&2
        exit 1
    fi
    exec nix develop .#emulator -c "$0" "$@"
fi

apis=(29 33 37)
logs="$root/build/emulator"

avd_of() { echo "fox-api$1"; }
port_of() {
    case "$1" in
        29) echo 5554 ;; 33) echo 5556 ;; 37) echo 5558 ;;
        *) echo "fox emulator: unknown API '$1' (have: ${apis[*]})" >&2; exit 1 ;;
    esac
}
# Image directory names in the SDK: API 37's is "android-37.0".
image_of() {
    case "$1" in
        37) echo "system-images;android-37.0;google_apis;x86_64" ;;
        *) echo "system-images;android-$1;google_apis;x86_64" ;;
    esac
}
running() { adb devices | awk -v s="emulator-$(port_of "$1")" '$1 == s { found = 1 } END { exit !found }'; }

create() {
    local api avd config
    for api in "${apis[@]}"; do
        avd=$(avd_of "$api")
        if [[ -d "$HOME/.android/avd/$avd.avd" ]]; then
            echo "$avd exists"
            continue
        fi
        # The images ship no devices.xml, so avdmanager complains about each one; the AVD is fine.
        echo no | avdmanager create avd --name "$avd" --package "$(image_of "$api")" --device pixel_7 2>&1 \
            | { grep -v -e 'devices.xml' -e 'Loading local repository' -e 'Auto-selecting single ABI' || true; }
        [[ -f "$HOME/.android/avd/$avd.avd/config.ini" ]] || { echo "fox emulator: avdmanager did not create $avd" >&2; exit 1; }
        config="$HOME/.android/avd/$avd.avd/config.ini"
        # Host keyboard types into the device; 4 GB RAM so the memory widgets see a phone-like device.
        sed -i -e '/^hw.keyboard=/d' -e '/^hw.ramSize=/d' "$config"
        printf 'hw.keyboard=yes\nhw.ramSize=4096\n' >> "$config"
        echo "$avd created"
    done
}

start() {
    local api=${1:?usage: emulator.sh start <29|33|37> [--window]} window=${2:-}
    local avd port serial
    avd=$(avd_of "$api"); port=$(port_of "$api"); serial="emulator-$port"
    if running "$api"; then
        echo "$avd already running as $serial"
        return
    fi
    [[ -d "$HOME/.android/avd/$avd.avd" ]] || create
    mkdir -p "$logs"
    local args=(-avd "$avd" -port "$port" -no-audio -no-boot-anim)
    if [[ "$window" == "--window" ]]; then
        args+=(-gpu auto)
    else
        args+=(-no-window -gpu swiftshader_indirect)
    fi
    setsid nohup emulator "${args[@]}" > "$logs/$avd.log" 2>&1 < /dev/null &
    echo "$avd booting as $serial (log: build/emulator/$avd.log)"
    timeout 300 adb -s "$serial" wait-for-device
    until [[ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; do
        if ! kill -0 $! 2>/dev/null; then
            echo "fox emulator: $avd exited during boot; see build/emulator/$avd.log" >&2
            exit 1
        fi
        sleep 2
    done
    echo "$avd ready: adb -s $serial"
}

stop() {
    local which=${1:-all} api
    for api in "${apis[@]}"; do
        [[ "$which" == all || "$which" == "$api" ]] || continue
        if running "$api"; then
            adb -s "emulator-$(port_of "$api")" emu kill > /dev/null
            # adb keeps listing it for a few seconds while it shuts down.
            timeout 60 bash -c "while adb devices | grep -q '^emulator-$(port_of "$api")\b'; do sleep 1; done" || true
            echo "$(avd_of "$api") stopped"
        fi
    done
}

list() {
    local api state
    for api in "${apis[@]}"; do
        if [[ ! -d "$HOME/.android/avd/$(avd_of "$api").avd" ]]; then state="not created"
        elif running "$api"; then state="running"
        else state="stopped"; fi
        printf '%-10s emulator-%s  %s\n' "$(avd_of "$api")" "$(port_of "$api")" "$state"
    done
}

case "${1:-}" in
    create) create ;;
    start) shift; start "$@" ;;
    stop) shift; stop "$@" ;;
    list) list ;;
    *) sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
