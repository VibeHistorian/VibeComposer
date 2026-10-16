# VibeComposerJS

The starting point for the Angular rewrite of VibeComposer. The desktop window is provided by Wails v2; the UI lives in `frontend/` and Go methods exposed to Angular live in `App`.

This project is separate from the existing Java application. The rewrite can move feature by feature while the Java app remains available as a reference and working product.

Then start the desktop app from this directory:

```powershell
wails dev
```

Wails runs the Angular dev server and refreshes the desktop window as the UI changes. Its frontend install and build commands are configured in `wails.json`.

To work on the UI in a browser without starting Wails:

```powershell
cd frontend
npm install
npm start
```

The Angular shell reports when it is running outside Wails. To make a desktop build, run `wails build`; the generated application is written under `build/bin/`.

## Project layout

```text
VibeComposerJS/
  App.go                 Go methods bound to the web UI
  main.go                Wails desktop entry point
  frontend/
    src/app/core/        Wails bridge and app-wide services
    src/app/features/    Angular feature areas for the rewrite
    src/app/shared/      Reusable UI pieces
    src/styles.css       Global visual system
  wails.json             Wails and Angular development/build commands
```

The starter bridge is intentionally small. Add generator operations to Go only when they need native or long-running work; keep presentation and interaction in Angular. Wails generates its JavaScript bindings during `wails dev` / `wails build`.

## Audio playback

Browser and desktop playback use `webaudio-tinysynth` 1.1.3 with its higher-quality GM synthesis bank. Instrument programs select individual presets; percussion pitches 35–81 select the GM drum map. Sounds are synthesized locally without sample downloads. These are synthesized approximations of acoustic instruments.

`frontend/src/app/core/audio/tiny-synth-engine.ts` adapts the bank to the existing playback scheduler and per-track mixer buses. One engine and AudioContext are reused across transport starts and LIVE reloads. Each note owns its sources, envelopes and release time, preserving overlapping notes and independent track programs even when exported MIDI channels match. Stop, pause and LIVE crossfades cancel every operator, including queued percussion. MIDI export still uses the stored track programs and channels.

The adapter deliberately uses the pinned library's `_note`, `_releaseNote`, timbre bank and voice handles: the public channel/pitch note-off API cannot express independent overlapping voices. Revalidate this bridge before upgrading TinySynth. The engine's internal housekeeping timer remains a single app-lifetime timer; it is not recreated per track or reload.

From `frontend/`, run `npm run test:playback` for transport/routing checks and `npm run test:synth:browser` for actual Chromium offline audio rendering of GM presets, percussion, distinct bass timbres and voice cancellation. The browser test auto-detects Chrome/Edge, or accepts `OVERVIEW_TEST_BROWSER` pointing to a Chromium executable.

## Workspace and migration

The app uses one persistent workspace with grouped Tracks, arrangement/score, contextual musical editors, a mixer popup and bottom transport. Melody uses the current block generator; all five roles support independent tracks, manual-note replacement, history, persistence and playback/MIDI output. Role totals support zero tracks, including an empty project.

Follow [TRANSLATION_RULES.md](TRANSLATION_RULES.md) and the [workspace contracts](DAW_REDESIGN_PLAN.md). The [active backlog](CONTEXTUAL_CONTROLS_PLAN.md) and [remaining control inventory](CONTROL_PLACEMENT_MAP.md) describe unfinished translation work. Completed checkpoint narratives, Java parity evidence and superseded limitations live in [__work_history](__work_history/README.md).

Angular ownership rules live in the [feature README](frontend/src/app/features/README.md) and [shared UI README](frontend/src/app/shared/README.md). Java fixture regeneration is documented in [the fixture guide](frontend/scripts/java/README.md).

Test commands, the focused and exhaustive browser matrices, and fixture performance notes are documented in the [frontend test guide](frontend/scripts/README.md).
