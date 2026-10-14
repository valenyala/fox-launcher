#!/usr/bin/env bash
# Local CI gate (ADR-13). Everything a hosted runner would check, checked here:
#   check  -> unit tests + lint, all variants
#   assembleDebug -> the APK
#   :app:compileDebugAndroidTestKotlin -> instrumented sources, which nothing
#   else compiles while no device is attached.
#   :baselineprofile:compileBenchmarkReleaseKotlin -> the profile generator and
#   macrobenchmarks (task 8.1), likewise device-only.
#
# Runs inside `nix develop` unless the caller is already in the dev shell.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
cd "$root"

tasks=(check assembleDebug :app:compileDebugAndroidTestKotlin :baselineprofile:compileBenchmarkReleaseKotlin)

if [[ -n "${ANDROID_HOME:-}" ]] && command -v java >/dev/null 2>&1; then
    exec ./gradlew "${tasks[@]}" "$@"
fi

if ! command -v nix >/dev/null 2>&1; then
    echo "fox ci: not in the dev shell and no nix on PATH — run 'nix develop' first" >&2
    exit 1
fi

exec nix develop -c ./gradlew "${tasks[@]}" "$@"
