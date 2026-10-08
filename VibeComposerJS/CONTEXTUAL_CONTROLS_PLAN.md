# Contextual controls and melody buildout

Status: implementation in progress, 2026-10-08. This phase extends [DAW_REDESIGN_PLAN.md](DAW_REDESIGN_PLAN.md). The attached legacy and translated screenshots inform placement; Java source determines behavior. See [CONTROL_PLACEMENT_MAP.md](CONTROL_PLACEMENT_MAP.md) for old control names and lookup paths.

### UI checkpoint 1 — Selection and contextual panel shell

Implemented the P2 selection foundation ahead of melody: header-only section ranges, section-role/section-track settings targets, middle-click and keyboard I presence toggles, and distinct selection visuals. Header ranges remain separate from settings targets. Added the default-expanded between-canvas panel with breadcrumbs, current-value summaries, and an inspector link. The local inspector provides explicit presence editing and navigation to global track settings. Section-local musical controls remain read-only until P3 wires patches to generation; no local action writes global generator values implicitly.

Validation: 34 overview, selection, and playback tests passed; production Angular build passed (existing Pixi CommonJS dependency warning). Ready for user UI testing. Melody, header reroll-policy controls, quick musical editors, and section patches remain outstanding. The batch intentionally pauses here before those additions.

### UI checkpoint 2 — Editable inherited part settings

Implemented the P3 patch foundation and quick editing for the currently supported role generators. Section role and track patches are validated, sparse, immutable, and resolved before generating section notes. The shared editor in the between-canvas panel and inspector supports the existing role controls plus pitched transpose/velocity bounds and part note length. Mixed values are aggregated across effective track settings. Reset is available per field and per selected local scope; role edits/resets preserve track exceptions. Customized cells and track buttons carry an asterisk.

Patches survive project JSON/session restore, undo/redo, section/track duplication, and presence rerolls. Track deletion removes associated track patches. Velocity bounds are checked against every effective track/section combination, including inherited bounds; invalid edits/resets leave valid settings intact and show feedback. Default pitched velocity bounds remain 69–89, preserving the current translated generators' seeded output until bounds are explicitly changed. Percussion transpose and velocity-range controls are deferred; percussion groove/swing/length controls work now.

Saved track-wide manual phrases remain explicit replacements: generator patches are retained but masked by those notes. Both panels identify affected tracks, and the local inspector offers an explicitly whole-track Restore generated notes action. Section-local manual phrase ownership remains a later editor slice. Generated bass octave intervals are applied inside their resolved section; saved manual notes are no longer doubled implicitly. Secondary-track seed derivation is now applied once in arrangement generation, matching the editor; existing secondary tracks can therefore produce different notes from the previous twice-hashed arrangement path.

Validation: 42 tests passed, including real resolver/service/generator/parsed-MIDI export tests and existing overview/selection/playback checks; production build passed with the existing Pixi CommonJS warning. Ready for UI testing; melody, the full pattern/fill engine, and header reroll policies remain outstanding.

## Product contract

Keep the single workspace, persistent transport, arrangement and score. Add Melody first in every role ordering: **0 Melody (red), 1 Bass (cyan), 2 Chords (green), 3 Arpeggio (pink), 4 Drums (gold)**. Numeric types are explicit Java correspondence, not array positions to be inferred elsewhere. Only the current block-based melody algorithm is in scope.

Separate two kinds of settings:

- **Part settings:** values consumed when producing MIDI, including probability values such as pause, note variance, and split chance. Put frequent controls in the panel between arrangement and score, and the complete set in the inspector.
- **Track generation settings:** rules used to choose/change part settings when rerolling tracks, including randomize-pattern toggles, length bounds, maximum split, and hits constraints. Put frequent rules directly in each expanded Tracks group header; expose the full set in a clearly named inspector section.

Classification follows the consumer of a value, not its name or whether it uses randomness. For example, `exceptionChance` determines generated notes; `randomArpMaxExceptionChance` chooses that parameter on a track reroll. `randomArpCorrectMelodyNotes` and `randomArpUseOctaveAdjustments` are direct phrase-generation inputs despite their names. Some controls in the old tab headers therefore belong with part settings.

The inspector remains the complete editor. A quick control and its inspector equivalent bind to the same value, validation, effective-value resolver, and mutation command. Global harmony, transport, playback/mix, and application preferences retain distinct homes.

## Interaction and context

| Target | Primary action | Editing context |
| --- | --- | --- |
| Tracks group title or arrangement row label | Select global role | Global role part settings; header exposes that role's track-generation policy |
| Tracks track row | Select global track | That track's base part settings |
| Arrangement section header | Select section, with existing Ctrl/Meta and Shift range semantics | Section structure, chords/durations and section-wide settings |
| Arrangement cell body | Left-click selects section + role cell | Part settings for that role in that section |
| Track button inside a cell | Left-click selects section + track | Part settings for that instrument in that section |
| Track button inside a cell | Middle-click toggles track presence | Change presence only; keep the current editing context |
| Score note | Existing note-edit action | Track/note editor, with its section identified where available |

Section selection is reachable through the arrangement header only. Body hit regions must no longer invoke section selection. Track buttons take priority over cell bodies; one click must never activate both. Clicking an absent track still selects it for editing without including it. Selected state, presence state, and customized state use distinct visual cues.

Keep section-range selection (used for score focus and section operations) separate from the active settings target. Cell/track selection switches the settings target without adding to, trimming, or creating a header-selected range. If a previous range remains highlighted, the panel breadcrumb must still identify the single edited cell; range actions stay in the section toolbar. Only an explicit Apply-to-range command edits several sections.

Replace ambiguous combinations of `selectedRole`, `selectedTrackId`, and section precedence with one discriminated settings target: project, global-role, global-track, section, section-role, section-track, or note. Use stable section and track IDs. A playback/editor track pointer may remain separate but must not silently determine the settings scope. Reconcile targets after imports, deletions, undo, and reordering; never redirect a deleted target's pending edit into another track.

Provide keyboard selection and an explicit Include/Exclude action in inspector/context menu for devices without middle-click. Prevent browser autoscroll on middle-button pointerdown and dispatch toggles through `auxclick` or an equivalent single path. Enter selects; a separately documented action toggles presence. Update the canvas accessible labels, focus order, tooltips, and key handling along with pointer hit testing.

## Quick panel and expanded Tracks headers

The **Part settings** panel sits inside the center column, immediately after arrangement and before score. It starts expanded. Its always-visible heading contains collapse/expand, role color, scope breadcrumb (for example `VERSE1 · Chords · C2`), inherited/customized status, Reset to inherited, and Open inspector. Collapsing it preserves context and values. Expansion is UI state, not a musical history entry.

Use two compact rows where space permits: fill + pattern selector/grid, then knobs for transpose, hits, span, repeat, shift, pause, split, length, swing, and velocity range. Add a small role-specific subset from the placement map. Show only supported controls; never ship active controls that have no generator effect. A role cell aggregates its member tracks: unequal values show Mixed, not the first track's value. A mixed pattern shows a summary until the user explicitly chooses a common pattern. Narrow widths wrap or scroll the quick controls while leaving score usable.

Expanded Tracks group headers show track count/Add/Reroll plus the most-used track-generation rules. Keep them directly accessible above the track rows; use an Advanced link to the corresponding inspector policy rather than a second full form. Header expansion and group selection are separate actions. Track rows retain instrument summary, channel, mute/solo, visibility, and optional reroll lock/action. Randomization rules do not appear as controls in the central Part settings panel.

Do not replace the old removed mode-controls drawer with this panel: it edits the active musical context and does not change workspace routes or modes.

## State and generation design

Proposed boundaries (names may adapt to existing types):

- Project musical settings: harmony, tempo/key/mode, explicit global musical defaults.
- `roleDefaults[role]`: part defaults used by newly added tracks. Group edits explicitly apply a patch to current member tracks and update the defaults in one command, preserving independent identities.
- `tracks[id].partSettings`: independently stored base MIDI-generation settings. The current `generatorSettings` contains this kind of data; do not repurpose it into a randomization policy accidentally.
- `trackGenerationPolicies[role]`: serializable reroll options, bounds, and automatic triggers. Add per-track policy overrides only where required by existing behavior; track locks are a separate field.
- `section.rolePartOverrides[role]`: sparse musical patches for a section cell.
- `section.trackPartOverrides[trackId]`: sparse musical patches for an individual section part.
- Section `trackPresence`, chord/duration overrides, variations, and section-wide musical overrides stay separate from part settings and reroll policy.

For a track in a section, resolve **track base → section role patch → section track patch**. Role defaults initialize tracks; they are not another hidden layer that overwrites independent track bases. Section-wide tempo/harmony/swing rules form a separate generation context with explicitly documented precedence. Preserve patch omission: absent means inherit; zero and false are real overrides. Pattern grids/custom velocities replace complete arrays; do not merge arrays by index accidentally.

Example: C1 base transpose 0 and C2 base transpose 12; the section-2 Chords cell displays Mixed. Setting that cell to -12 adds a section-role patch affecting C1 and C2 there. Setting section-2/C2 to 7 adds a higher-priority track patch. Resetting C2 restores -12; resetting the role patch restores C1=0 and C2=12, while any explicit C2 track patch remains until reset separately. Editing the global tracks must not remove local patches. A newly added chord track inherits the section-role patch but no other track's local patch.

Sparse inheritance is a proposed translation design, not a claim that Java already does this: Java stores customized lists of complete `InstPart` values on `Section`. Preserve the useful musical result and make propagation explicit. Use an explicit **Freeze effective settings** action if users need a full snapshot that no longer follows globals; do not freeze unrelated fields on the first knob edit.

Keep overrides effective during regeneration and presence rerolls. Editing a cell automatically creates its patch; a user should not need to enable a hidden MANUAL flag. Use visible customized indicators and scoped reset/apply/copy commands to replace the useful old MANUAL/Apply workflow. Any later automatic reset-on-compose option must be explicit and default to preserving local edits.

Define commands separately: Regenerate MIDI (same part settings), Reroll track settings (policy chooses new base values), Reroll arrangement presence, and Reroll selected section part settings (policy writes local patches). Do not overload the existing arrangement REROLL, which currently randomizes presence. Rerolls never add/remove tracks unless a separate Generate N tracks action is requested. Honor track locks and instrument locks; preserve track IDs, channels, and mix state.

Use explicit JavaRandom streams. Java's panel reroll code sometimes uses unseeded `new Random()`; those paths need a documented deterministic seed policy, not a claim of exact replay compatibility with an unrecorded Java run. Preserve seeded phrase-generator consumption order. Resolve each section's effective settings before generating its notes, and share that resulting arrangement output among playback, previews, score, and MIDI export.

Current `editedPhrase` is track-wide. Before wiring section part edits, define whether manual note edits replace a whole track or a section segment. Recommended next model: edited phrases keyed by section + track, with existing track-wide edits explicitly marked as replacements. Keep manual notes when changing generator settings and show when they mask the generated result; Restore generated notes is the explicit way back. Do not silently discard note edits on a knob change or change one section while presenting a whole-track edited phrase as regenerated.

All musical commands pass through ProjectService and commit immutable historical state. Knob drag and pattern painting preview during a gesture and commit once on release. Export/import/session restore must preserve policies, part settings, overrides, locks, and exact decimal seed strings. Validate values and references without version gates or migrations, per translation rules.

## Delivery stages

### P0 — Contracts and implementation inventory

Agree the interaction table, resolver precedence, scope labels, and classification above. Audit each placement-map row through old UI → model/config → actual consumer before implementing it. Record exact units, defaults, bounds, dependencies and seeded streams. Do not infer them from screenshot labels alone. Distinguish current approximations (`rhythm`, `rate`, `voicing`, drum `groove`) from Java parity; do not equate them to the old full pattern engine.

Exit: typed settings/policy/selection design and a first vertical slice chosen. This document and the placement map supply the starting backlog.

### P1 — Melody as the fifth role (early pre-work)

Add the `melody` union variant, role/type correspondence, defaults, independent tracks, import validation, session/export persistence, channels, role sorting, group/score/overview/inspector/mixer presentation, and red theme tokens. Extend section-type chances and presence generation explicitly for melody; retain drums on channel 10. Adding a track must work even when a role has no current track, instead of relying solely on copying an existing role member.

Port the smallest coherent current block-generation path using `MelodyGenerator` and `MelodyBlockSkeletonGenerator`, with needed block/target/rhythm helpers. Stage the larger melody feature set in P5. Never use a bass or arp generator as an unlabeled melody substitute. Role-shell work can precede the generator, but mark it unavailable for generation until real block output works. Exclude `LegacyMelodySkeletonGenerator` and controls used only by it, including `melodyLegacyMode`, which selects the old skeleton branch.

Exit: melody appears first everywhere, independent melody tracks produce deterministic block-based notes, play and export correctly, and survive import/undo. Validate fixed-seed phrases against Java fixtures for the supported path, including seed-offset behavior; changing existing role ordering must not change other roles' seed derivation.

### P2 — Selection and panel shells (early pre-work)

Implement the settings-target union and separate section range. Change hit testing/keyboard actions to header-only section selection, cell selection, track selection, and middle-click presence toggle. Add selected/custom/present visual states. Add the default-expanded central panel and expanded-header slots with working breadcrumbs, collapse behavior, and inspector links. Do not show editable placeholders for unported settings.

Exit: each interaction activates exactly one intended target, absent parts remain selectable, section ranges still work from headers, and the panel/inspector agree on scope. Verify canvas coordinates under scroll, zoom, resize, device pixel ratio, and overlapping track hit regions; retain existing performance characteristics.

### P3 — Overrides and first working controls

Add typed common part settings and sparse section role/track patches, pure resolution, ProjectService commands, import validation, serialization, undo, and scoped reset. Wire a complete vertical slice using transpose, length, and velocity range, including playback/score/export. Support mixed group values and a cell's higher-priority track overrides; signal exceptions rather than silently wiping them during a group edit.

Exit: the inheritance example above works, editing section-2/C2 never changes other cells, group edits are one history action, and save/reload produces the same effective notes. Fix whole-track edited-phrase masking before declaring section-scoped regeneration complete.

### P4 — Shared pattern and rhythm controls

Port `RhythmPattern`, `ChordSpanFill`, pattern grid/custom velocities, hits/span/repeat/shift/flips, pause/split, swing and accent behavior. Build reusable framework-independent pattern logic and Angular controls. Retain Java fill indexing (including ODD/EVEN masks), HALF behavior on odd lengths, supported role differences, and interactions between hits, span, repeat and custom grid length. Do not confuse rhythm pattern with arp pitch direction or melody block structure.

Exit: panel and inspector edit the same data; patterns change the scoped generated MIDI; unsupported role controls are omitted; pattern drag is one undo entry. Check representative full/custom/shifted/flipped patterns, fill masks, rests, velocities, and section boundaries against Java behavior.

### P5 — Role-specific musical settings and full inspector

Deliver in bounded slices: bass; chords/strum/transitions; arp direction/contour and melody-dependent correction; drums/ghost velocities; extended block melody/targets/custom durations. Populate the role quick subsets and retain advanced settings in inspector. Add section chords/durations and SectionConfig settings with shared generation context; section tempo changes require matching playback scheduling and MIDI tempo events before exposing their control.

Exit per role: every active mapped control has an audible/exported effect, correct scope, validation, history, persistence, and supported fixed-seed comparison. Cross-role dependencies use melody from the same section and a documented generation order, including when that melody track is excluded.

### P6 — Track-generation policies in Tracks headers

Port reroll policies and distinct actions from `createRandom*Panels` with the placement map. Start with arp hits/Random#/One#/2^n, fills, max split and length bounds; then chords vary length/strum/split; drum rules; melody seed/pattern/target reroll triggers. Bass has fewer exposed rules: preserve its creation behavior without inventing legacy checkboxes. Show current generated part values in the central panel after reroll.

Header controls can be implemented role by role after the corresponding P3–P5 musical consumer exists. P6 need not wait for every advanced melody control. Separate instrument randomization permission, parameter randomization, shared-hit constraints and automatic Compose/Regenerate triggers.

Exit: changing a policy alone leaves stored part values and current notes unchanged; reroll changes only its explicit targets, respects locks, is reproducible, preserves local overrides, and commits one undo entry. Presence reroll never performs a settings reroll. Validate bound/order edge cases and Java behavior where seeded comparison is possible.

### P7 — Apply/reset workflows and workspace polish

Add cell/track Apply to selected range, copy/freeze effective settings, copy back to globals with explicit scope, reset per field/cell/track, and badges indicating local exceptions. Preserve identity during section copy/reorder/duplicate; give copied sections independent patch objects. Add clear policies for deleting tracks with overrides and duplicating tracks (recommended: explicitly copy source overrides to the new ID).

Exit: keyboard/device alternatives, expanded/collapsed layouts, mixed values, inspector navigation, section range operations, and persistence all remain usable with many tracks and narrow windows. Run the existing selection/overview/playback checks plus targeted resolver/reroll/export tests and a production build.

### P8 — Deferred global behavior and ExtraSettingsGUI

Port remaining app/playback/device/export preferences separately. Musical settings from ExtraSettingsGUI remain in a later global musical-settings inspector, not automatically in a reroll header. See the deferred inventory. Java-specific soundbanks and device behavior require deliberate Web Audio/Web MIDI equivalents; Wails remains thin.

Suggested order: **P0 → P1 → P2 → P3 → P4**, then P5/P6 in role slices, P7, P8. Panel shells and selection can be prepared while melody core is still being translated, provided generation remains clearly unavailable until implemented.

## Current TypeScript implementation touchpoints

Paths below are relative to `frontend/src/app/`:

| Area | Existing files | Work |
| --- | --- | --- |
| Musical/project model | `core/project/project.model.ts`, `project.service.ts` | Fifth role, settings/policy split, overrides, validation, commands, history |
| Selection | `shared/workspace-ui.service.ts` | Explicit settings target, separate section range, deletion reconciliation |
| Arrangement canvas | `shared/arrangement-overview.component.ts/.html/.css` | Body/header/button hit paths, auxclick, focus and visual states |
| Workspace, Tracks, inspector | `shared/workspace-canvas.component.ts/.html/.css` | Extract contextual settings and header components; central panel insertion |
| Knobs | `shared/compact-knob.component.ts/.html/.css` | Reuse accessibility/gesture behavior; shared commit contract |
| Role theme | `frontend/src/styles.css` (relative to project root) | Red melody token family matching existing role tokens |
| Phrase generation | `core/music/*-generator.ts`, `arrangement-generator.ts` | Melody and shared pattern engine, effective section inputs |
| Section probabilities | `core/project/section-type-settings.service.ts`, section-types feature | Melody probabilities, keep presence separate from part settings |
| Generated output | `core/audio/audio-playback.service.ts`, `core/music/midi-export.ts`, `shared/score-canvas.component.ts` | Shared resolved arrangement, cross-role dependencies and export parity |
| Note editing/mix | `features/edit/*`, `features/mix/*` | Melody support, section phrase ownership, preserve track mix/channel identity |
| Existing validation | `frontend/scripts/section-selection.test.cjs`, `arrangement-overview.test.cjs`, `audio-playback.test.cjs` (project-relative) | Update interaction expectations and protect existing behavior |

Implementation stages carry their own validation gates above; completed checkpoint evidence is recorded at the top of this document.
