# Usage: awk -v label=NAME -f frame-work.awk <dumpsys gfxinfo framestats output>
# Per-frame work from gfxinfo framestats, independent of the refresh rate (awk fields are 1-based):
# UI thread = SyncQueued(13) - HandleInputStart(6); GPU/render = FrameCompleted(17) - IssueDrawCommandsStart(15).
BEGIN { FS = "," }
/---PROFILEDATA---/ { inside = !inside; next }
inside && $1 == "0" { ui[n] = ($13 - $6) / 1e6; gpu[n] = ($17 - $15) / 1e6; n++ }
function pct(a, p,   i, j, t, m) { m = n; for (i = 0; i < m; i++) s[i] = a[i]
  for (i = 1; i < m; i++) { t = s[i]; for (j = i - 1; j >= 0 && s[j] > t; j--) s[j + 1] = s[j]; s[j + 1] = t }
  return s[int((m - 1) * p)] }
END { printf "%s: %d frames | UI thread p50 %.1f p90 %.1f max %.1f ms | GPU p50 %.1f p90 %.1f max %.1f ms\n",
  label, n, pct(ui, .5), pct(ui, .9), pct(ui, 1), pct(gpu, .5), pct(gpu, .9), pct(gpu, 1) }
