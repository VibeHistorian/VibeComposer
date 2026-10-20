# VibeComposerJS

The starting point for the Angular rewrite of VibeComposer. The desktop window is provided by Wails v2; the UI lives in `frontend/` and Go methods exposed to Angular live in `App`.

This project is separate from the existing Java application. The rewrite can move feature by feature while the Java app remains available as a reference and working product.

## Setup and builds

Use **pnpm 12.9.1** (pinned in `frontend/package.json`) and a Node.js version matching its `engines` field: 22.22.3+, 24.15.0+, or 26+. Install the pinned pnpm version using the [pnpm installation instructions](https://pnpm.io/installation), and ensure `pnpm` is on PATH for Wails.

`frontend/pnpm-lock.yaml` is the authoritative frontend lockfile. From this directory, install exactly that dependency tree:

```powershell
cd frontend
pnpm install --frozen-lockfile
cd ..
```

Use pnpm for all frontend commands. Its workspace configuration applies the required [TinySynth patch](frontend/patches/README.md) during installation and explicitly permits Angular's native dependency build scripts. When intentionally updating dependencies or patches, use pnpm, regenerate and review the lockfile, and commit it with the manifest/patch changes. Do not generate npm or Yarn lockfiles.

Start the desktop app from this directory:

```powershell
wails dev
```

Wails runs the Angular dev server and refreshes the desktop window as the UI changes. Its frontend install and build commands are configured in `wails.json`.

To work on the UI in a browser without starting Wails:

```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm start
```

The Angular shell reports when it is running outside Wails. To make a desktop build, run `wails build`; the generated application is written under `build/bin/`. Wails uses the same frozen pnpm installation and `pnpm run build` for production.

DM Sans and DM Mono are bundled through pinned `@fontsource` packages, including Latin extended glyphs and the weights used by the UI. After installing dependencies, building and displaying the app require no Google Fonts connection. Keep font imports in `frontend/src/styles.css` local; remote font links in `src/index.html` make Angular's production font inlining depend on network access. Their SIL Open Font License notices ship from `frontend/public/licenses/`; update these copies when upgrading the font packages.

Wails v2 caches installation using only `package.json.md5`, and writes that checksum before installation succeeds. This explains why a failed install can appear to succeed on a second build: that attempt may skip installation. Run `pnpm install --frozen-lockfile` after pulling lockfile/workspace/patch changes, or use `wails build -f` to force installation. Never rely on retrying a failed install.

If migrating an existing npm or mixed npm/pnpm checkout, remove only `frontend/node_modules` and any untracked `frontend/package-lock.json`, then run the frozen pnpm install. The previous `npm install` Wails command could crash while traversing pnpm links (`Cannot read properties of null (reading 'children')`); using pnpm throughout fixes that failure.

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

From `frontend/`, run `pnpm run test:playback` for transport/routing checks and `pnpm run test:synth:browser` for actual Chromium offline audio rendering of GM presets, percussion, distinct bass timbres and voice cancellation. The browser test auto-detects Chrome/Edge, or accepts `OVERVIEW_TEST_BROWSER` pointing to a Chromium executable.

## Workspace and migration

The app uses one persistent workspace with grouped Tracks, arrangement/score, contextual musical editors, a mixer popup and bottom transport. Melody uses the current block generator; all five roles support independent tracks, manual-note replacement, history, persistence and playback/MIDI output. Role totals support zero tracks, including an empty project.

Follow [TRANSLATION_RULES.md](TRANSLATION_RULES.md) and the [workspace contracts](DAW_REDESIGN_PLAN.md). The [active backlog](CONTEXTUAL_CONTROLS_PLAN.md) and [remaining control inventory](CONTROL_PLACEMENT_MAP.md) describe unfinished translation work. Completed checkpoint narratives, Java parity evidence and superseded limitations live in [__work_history](__work_history/README.md).

Angular ownership rules live in the [feature README](frontend/src/app/features/README.md) and [shared UI README](frontend/src/app/shared/README.md). Project codecs, command helpers, and the service commit boundary are mapped in the [project-state guide](frontend/src/app/core/project/README.md). Java fixture regeneration is documented in [the fixture guide](frontend/scripts/java/README.md).

Test commands, the focused and exhaustive browser matrices, and fixture performance notes are documented in the [frontend test guide](frontend/scripts/README.md).
