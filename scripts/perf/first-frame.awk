# Usage: awk -v label=NAME -f first-frame.awk <dumpsys gfxinfo framestats output>
# The frames drawn as a window becomes visible again (Flags bit 1, so frame-work.awk skips them), as when
# returning home: UI thread = SyncQueued(13) - HandleInputStart(6), plus the whole frame from its
# vsync to FrameCompleted(17) - Vsync(4). Fields are 1-based.
BEGIN { FS = "," }
/---PROFILEDATA---/ { inside = !inside; next }
inside && $1 ~ /^[0-9]+$/ && int($1 / 1) % 2 == 1 { ui[n] = ($13 - $6) / 1e6; total[n] = ($17 - $4) / 1e6; n++ }
function median(a,   i, j, t, s) { for (i = 0; i < n; i++) s[i] = a[i]
  for (i = 1; i < n; i++) { t = s[i]; for (j = i - 1; j >= 0 && s[j] > t; j--) s[j + 1] = s[j]; s[j + 1] = t }
  return s[int((n - 1) / 2)] }
END { if (n) printf "%s: %d first frames | UI thread median %.1f ms | vsync to completed median %.1f ms\n",
  label, n, median(ui), median(total) }
