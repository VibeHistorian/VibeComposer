# Project state ownership

`ProjectService` owns Angular signals, session storage, generation error reporting, and the history/persistence commit boundary. UI consumers continue to use its public commands. Candidate-building helpers do not publish state or create history entries.

Independent tracks own generator settings and mix values. Global role edits update their member tracks; there is no duplicated project-level generator settings or role mix layer. Adding a track to an empty role uses the corresponding `DEFAULT_TRACKS` entry. Imports read track values and omit obsolete top-level role settings and mix when constructing project state.

Role track presets store selected global tracks, including mix, generator settings and track-owned manual notes. `track-presets.ts` validates libraries and builds load candidates; `ProjectService.loadTrackPreset` supplies new-track section presence and commits the whole load once. Replace fills the role's unlocked tracks in order, retaining their IDs, positions, presence and section overrides. Surplus unlocked tracks and their section references are removed. Additional tracks get fresh IDs and newly generated presence; append preserves all existing tracks. Locked tracks are kept. Section overrides are not included in presets. The track preset dialog owns the separate local-storage library and JSON import/export; library changes do not enter project history.

Global note editing saves `phraseEdits` on the independent track: ordered section-relative beat windows, notes, and explicit pickup/tail margins. Resolve each section's generated or whole manual phrase first, then replace only overlapping note onsets. Empty windows suppress generation; outside notes continue to inherit. Section `trackPhrases` and `generatedTrackIds` take precedence. Whole-phrase commands replace/reset range edits; Restore clears both. Range data survives export, history, duplication and manual Mode conversion.

Melody's project-wide inputs remain in `melodyGlobal` and are edited in Settings → Behavior → Melody. Target direction, target percentages, key emphasis, direction-change budget, new-block chance and per-chord pause filling belong to each Melody track's generator settings, with ordinary section inheritance. Java XML imports copy the corresponding saved GUIConfig values into each Melody track, keeping only project-wide fields in `melodyGlobal`.

Exported files and session storage use `trackPresence: { melody: [...], bass: [...], chords: [...], arpeggio: [...], drums: [...] }`. Each boolean array follows the order of tracks with that role in `tracks`, with exactly one value per member. Empty roles export as `[]`. Import expands these arrays to ID-keyed app maps and also accepts existing ID-map files. Mixed forms, unknown roles, non-booleans, and incorrect array lengths are rejected.

## Domain contracts

Import contracts directly from their owner. `project.model.ts` defines the complete project and does not forward the musical, track or section contracts. All model dependencies use type-only imports; role/section constants and project defaults have explicit runtime owners. These modules remain independent of Angular, the DOM and Wails.

| Module relative to `core/` | Responsibility |
| --- | --- |
| `music/phrase-note.ts` | Shared note shape for generation, manual editing, playback and MIDI. |
| `music/part-role.ts` | Five roles, Java group numbers, generation availability and stable role ordering. |
| `music/part-settings.model.ts` | Common/rhythm inputs, role settings, melody globals and part settings patches. |
| `music/generation-policy.model.ts` | Instrument pools and policies; track generation policies and patches. |
| `project/track.model.ts` | Track identity, mix, manual phrase ownership and role/settings discriminated union. |
| `project/arrangement.model.ts` | Section types and their catalogue, musical context, presence, overrides and phrases. |
| `project/part-settings-scope.ts` | Application editing scopes and workflow results. |
| `project/project.model.ts` | `CompositionProject`, composing the narrower contracts. |
| `project/project-defaults.ts` | Project/track/part/mix/arrangement defaults and fresh project construction. |

Rhythm, arpeggio and harmony vocabulary stays with its existing musical owner (`rhythm-patterns.ts`, `arpeggio-pattern.ts`, `harmony.ts`, etc.). Generator-specific defaults stay with their existing helpers, including `melody-global-settings.ts` and `track-generation.ts`. Low-level musical settings do not depend on project, track or section structures. Existing type-only relationships through `chord-span-fill.ts` and `durations.ts` introduce no runtime cycles.

## State operations

| Module | Responsibility |
| --- | --- |
| `project-codec.ts` | Decode imported/session data; validate complete command candidates. |
| `project-json.ts` | Format project exports with inline numeric/boolean arrays and one manual note per line. |
| `project-storage.ts` | Store presence as role arrays; expand them to ID maps on import/session restore. |
| `project-part-decoder.ts` | Decode and validate each role's full generator settings. |
| `project-track-decoder.ts` | Decode tracks, mix values, and manual phrases; validate phrase bounds. |
| `project-arrangement-decoder.ts` | Decode sections, scoped overrides, track references, and presence; define arrangement limits. |
| `project-load-diagnostics.ts` | Return field diagnostics after a rejected load; diagnostics do not define acceptance rules. |
| `project-defaults.ts` | Define stored defaults and construct a fresh default project with explicit section chances/randomness. |
| `section-type-chances.ts` | Framework-independent section chance defaults and their type. |
| `project-arrangement-commands.ts` | Build immutable candidates for section structure, degree overrides, and presence edits. |
| `project-history.ts` | Compare history content and preserve audition state during restoration. |

Codecs, defaults, and arrangement commands receive section chances and any presence random source as explicit inputs. They do not access Angular, storage, the DOM, or Wails. `ProjectService` supplies current preferences and `Math.random` for presence work; seeded music generation retains its existing Java random streams and draw order.

Keep imported-data decoding distinct from complete-candidate validation: imports retain the existing optional-field defaults and recovery behavior, while commands must supply a complete valid project. Treat `schemaVersion` as informational; do not add migrations or version gates during buildout.

The service validates candidates before committing. It owns no-op detection, history compaction, redo invalidation, persistence, and atomic rejection of invalid batches. A command helper may return `undefined` for a rejected request; a returned candidate still passes through the service's validation boundary.

Run `pnpm run test:parts` for project workflow coverage and the framework-independent codec/command checks in `scripts/project-codec.test.cjs`. Import, arrangement, and generation changes also use the relevant suites listed in `package.json`; production browser suites require a fresh `pnpm run build`. See [the test guide](../../../../scripts/README.md).

## Global output processing

Project-owned optional humanization defaults to zero timing and duration offsets. Each bound is 0–0.25 beats. A separate JavaRandom stream combines the exact project seed, a 31-based track-ID hash and salt 0x48554d414e; two uniform draws per arranged note supply independent signed offsets. Processing follows part timing/swing and manual replacements once in the shared cached layout. Durations floor at 1/960 beat. Pickups/tails may cross section edges and retain their source section; manual stored notes and dependency onset grids remain unchanged.

Section context owns optional velocityDb (−60 to +12 dB), inheriting neutral 0 dB. Final arranged velocities use round(velocity × 10^(dB/20)), clamped to 1–127 for positive events; zero stays zero. This applies equally to manual and generated notes without changing mixer gain or score shading. Reset removes the field; ordinary section context copy/duplication and history preserve it.

Project-owned MIDI padding defaults to zero per role, accepts integer minima 0–32 and only affects composition MIDI export. Export retains every independent track in role order and appends named empty fillers after each role, including empty roles. Fillers have no notes or mix CCs; channels are 1 for pitched roles and 10 for drums. Counts exceeding a requested minimum are never truncated. This follows Java's minimum output-track padding concept while including empty roles explicitly.

Compose BPM bounds belong to UI preferences (default 60–100, supported 30–240, ordered inclusive integers). Equal bounds still consume nextInt(1). Compose draws tempo after transpose even when tempo randomization is disabled; disabled tempo preserves the explicit project value. Preference edits never commit musical history; Compose records the resulting tempo normally.
