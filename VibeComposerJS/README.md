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

## UI organization

Build VibeComposerJS as one persistent music workspace. Keep grouped instrument tracks at left, arrangement and dense score together in the center, selection-aware settings at right, and playback plus editable key, mode, transpose, and tempo in the bottom transport. Primary chord setup sits above Tracks, and a mixer icon opens the mixer popup.

Arrange sections directly in the canvas. Selecting a score note or choosing Edit notes in the track inspector opens the relevant piano roll or drum grid, including empty tracks; Apply commits the phrase and returns to arrangement, while Cancel discards the draft. Group selection exposes settings shared by that instrument type, and track selection exposes its local generator and instrument settings.

Melody is the first role and uses a red theme. Its current role shell supports manual notes, independent instruments/channels/mixing, arrangement presence and MIDI export; automatic melody generation is not available yet. Existing saved projects retain their tracks: use + in Melody to add the first melody track. Any role may be empty and added back; the project retains at least one track. See the P1a checkpoint in [CONTEXTUAL_CONTROLS_PLAN.md](CONTEXTUAL_CONTROLS_PLAN.md) for the next block-generator slice.

Section part settings offer Freeze and Copy workflows in the quick panel header. Select destination section headers, then click the source cell or track; Copy shows the count and excludes the source. Copy preserves destination exceptions; Copy All snapshots each current track's effective values independently. Hover text explains the actions. Freeze keeps current part values from following inherited settings; new tracks still inherit. Reset cell + track overrides clears both local layers for a group. Each action is one undo step. See the P7a checkpoint in [CONTEXTUAL_CONTROLS_PLAN.md](CONTEXTUAL_CONTROLS_PLAN.md).

Fill and Fill flip select which progression chords generate notes for Bass, Chords, Arpeggio and Drums at global or section-local scope. Java's ODD means the second/fourth/etc. chords, EVEN the first/third/etc.; HALF1 uses the first floor(n/2), HALF2 the remainder. Flip reverses the choice (flipped ALL is silence). Fill preserves section presence and timing; saved manual notes still replace generated notes. See the P4a checkpoint in [CONTEXTUAL_CONTROLS_PLAN.md](CONTEXTUAL_CONTROLS_PLAN.md).

Chords also expose Hits, Shift and Pattern flip beside their Rhythm pattern picker, including one-six, euclid and custom. Hits subdivides each four-beat chord; Shift rotates the rhythm, and Pattern flip swaps notes/rests within that grid. Euclid distributes the requested Pulses (default 4) across Hits. Pulses is capped at Hits during generation but retained when Hits is reduced. Custom exposes an editable 32-cell grid: click/drag paints notes/rests with one undo entry, and reducing Hits retains hidden cells. The compact preview and both editors share the generator's pattern logic. Custom velocities enables 0–127 per-subdivision sliders in place of random Min/Max dynamics; zero silences a hit. Disabling retains the 32-cell grid. Velocity values stay at their displayed subdivision when rhythm Shift/Flip changes. Fill still controls which whole chords play. Rhythm identities and static masks come from `frontend/src/app/core/music/rhythm-patterns.ts`; UI, validation, model types and generators share that catalogue, with explicit subsets for currently supported role consumers. See P4b–P4e in [CONTEXTUAL_CONTROLS_PLAN.md](CONTEXTUAL_CONTROLS_PLAN.md).

Use the feature and shared UI READMEs for the workspace behavior and Angular ownership boundaries. Preserve useful density in piano rolls and drum grids, use role colors consistently, and use shared theme tokens for the rest of the interface.
