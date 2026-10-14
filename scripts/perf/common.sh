# Shared by the scripts/perf tools; sourced, not run. Results go to build/perf (git-ignored).
pkg=dev.foxlauncher
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
out=$(cd "$here/../.." && pwd)/build/perf
mkdir -p "$out"

# Never drive another app: every tool checks that Fox is the app in front before it swipes.
require_fox_in_front() {
  local top
  top=$(adb shell dumpsys activity activities | grep -m1 topResumedActivity)
  case "$top" in *"$pkg"*) ;; *) echo "Fox is not in front ($top); stopping." >&2; exit 1 ;; esac
}

go_home() { adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME > /dev/null 2>&1; }

# After an install: the state a user's phone reaches once Android has compiled Fox (task 8.2), as Play's
# installs or the idle dexopt would: start once, install the baseline profile, compile against it.
compile_like_play() {
  go_home; sleep 3
  adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE $pkg/androidx.profileinstaller.ProfileInstallReceiver > /dev/null
  adb shell am force-stop $pkg
  adb shell cmd package compile -f -m speed-profile $pkg > /dev/null
}
