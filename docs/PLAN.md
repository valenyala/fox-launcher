# Fox Launcher — Long-Term Plan

This plan was written once, up front, by a stronger model. It is meant to be executed task-by-task by
smaller models (Sonnet/Opus) across many sessions with no shared memory between them. Everything a
session needs is in this repo: `CLAUDE.md`, this file, `STATUS.md`, one phase file, `DECISIONS.md`,
`PLATFORM-NOTES.md`.

## Product in one paragraph

A fast, minimal Android launcher whose three differentiators are (1) a fully remappable gesture layer
on the home screen including system actions, (2) native home-screen widgets that show real memory
state and offer an honest "boost", and (3) wallpaper rotation that is visually seamless because the
launcher ships its own live-wallpaper engine that crossfades instead of swapping the system wallpaper.
Everything else (drawer, grid, folders, app widgets, icon packs) is table stakes done well.

## Phases

| # | Phase | Milestone when done | File |
|---|---|---|---|
| 0 | Toolchain & scaffold | `./gradlew assembleDebug` works; app can be set as default launcher | `phases/00-scaffold.md` |
| 1 | App catalog & drawer | Daily-usable: browse/search/launch every app, package changes handled | `phases/01-app-catalog-drawer.md` |
| 2 | Home grid | Pages, dock, drag & drop, folders, shortcuts, persisted in Room | `phases/02-home-grid.md` |
| 3 | Gesture engine | All gestures remappable; system actions via accessibility service | `phases/03-gestures.md` |
| 4 | Wallpaper rotation | Live-wallpaper crossfade from user folders on interval/unlock/gesture | `phases/04-wallpaper-rotation.md` |
| 5 | Memory widgets | Native widget framework + RAM gauge / boost / detail widgets | `phases/05-memory-widgets.md` |
| 6 | App widgets | Third-party AppWidgets hosted on the grid | `phases/06-app-widgets.md` |
| 7 | Polish & personalization | Icon packs, dynamic color, notification dots, animations, backup | `phases/07-polish.md` |
| 8 | Performance & release | Baseline profiles, R8, battery, Play listing | `phases/08-release.md` |
| 9 | Power features (optional) | Shizuku boost on API 34+, online wallpaper sources, macros | `phases/09-power-features.md` |

Phases 0→4 are strictly sequential. Phase 5 depends on 2. Phase 6 depends on 2 and 3. Phases 7–9 depend on
everything before them and their tasks are mostly independent of each other.

## Session protocol (follow exactly)

1. Read `CLAUDE.md`, then `docs/STATUS.md`. Note the current phase and the first unchecked task.
2. Open **only** that phase file. Read the phase goal, definition of done, and the task.
3. Read the `PLATFORM-NOTES.md` section the task points to, if any.
4. Do exactly that task. Do not start the next task in the same commit. Do not refactor unrelated code.
5. Run the task's **Verify** command(s). Run the pre-commit gate from `CLAUDE.md`.
6. If a device is attached, run the task's device checklist. If not, note "device checklist not run" in `STATUS.md`.
7. Update `docs/STATUS.md`: tick the task, add a one-line note if anything deviated, record blockers.
8. Commit: `phaseN: <task title>`.
9. If time remains, go to step 1 for the next task.

Rules when things go wrong:

- Gradle/KSP/Hilt error: read the whole error, fix the root cause. Do not change library versions to
  "try something"; look the version up.
- Blocked (needs a device you don't have, needs a decision, API doesn't behave as documented): write a
  **Blocker** entry in `STATUS.md` with what you tried, then move to the next task that does not depend on it.
- A task turns out to be wrong or impossible as written: do not silently change scope. Record what is wrong
  in `STATUS.md` under Notes, implement the closest correct thing, and say so in the commit message.
- Never "fix" a failing test by deleting it or loosening the assertion.

## Task format

Every task in a phase file has: **Why** (one line), **Do** (concrete steps), **Files** (where code goes),
**Verify** (a command and/or device checklist), **Done when** (observable criteria). If a task is larger
than one session, its sub-bullets are the checkpoints; tick sub-bullets in `STATUS.md`.

## Cross-cutting definition of done for any phase

- Pre-commit gate green.
- No new lint errors (warnings are fine, new suppressions must have a reason comment).
- Pure-Kotlin logic introduced in the phase has unit tests.
- `STATUS.md` shows every task ticked or a blocker.
- Device checklist for the phase passed at least once on a real device (or an emulator), recorded in `STATUS.md`
  with device model and Android version.
