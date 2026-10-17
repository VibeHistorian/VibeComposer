# Contextual controls: remaining migration work

Active backlog as of 2026-10-11. Completed checkpoints, Java comparison evidence and prior scope revisions live in [__work_history](__work_history/README.md). Do not reload those records unless the task needs their implementation details.

## Remaining order

1. **P6g / P6h:** Remaining creation branches and policies after their musical consumers exist.
2. **P7c:** Final workspace acceptance after those changes.
3. **P8:** Remaining global/app/playback/device/export preferences.
4. **P9 / P10 / P11:** Custom Melody durations, drum-driven rhythm accents, and imported melody respectively.

## Completed entry gates

P1b (current block Melody), P4f (single-pitch drum tracks) and P4g (applicable shared musical consumers) are delivered. P5a–P5f, P6a–P6f and P7a/P7b are also delivered in their supported scope. P5e includes explicit section inferred-harmony/Melody selection and shared voicing flattening at its original fixed timing; its [implementation evidence](__work_history/p5e-harmony-integration-record.md) documents parity boundaries and deliberate recoveries. P5f adds section Chordlets/durations and sparse timing/key context with shared playback/MIDI tempo maps; its [implementation record](__work_history/p5f-section-context-record.md) documents normalized timing and section-local modulation differences from Java. Advanced exclusions are represented below and in the [remaining placement inventory](CONTROL_PLACEMENT_MAP.md); historical checkpoint text is not an additional backlog.

The legacy melody skeleton and its exclusive controls remain excluded. Current implementation rules live in [TRANSLATION_RULES.md](TRANSLATION_RULES.md); workspace layout contracts live in [DAW_REDESIGN_PLAN.md](DAW_REDESIGN_PLAN.md).

Java contextual melody now uses continuous, context-sensitive Speed duration weights instead of the old 35/75 gates; see [the parameter guide](../midimasterpiece/CONTEXTUAL_GENERATION_PARAMETERS.md). This applies to Java's contextual phrase planner. The current TypeScript block generator retains its separate speed behavior; contextual phrase-planner parity is not implied by the completed block-melody gates above.

## Product contract

Keep the single workspace, persistent transport, arrangement and score. Add Melody first in every role ordering: **0 Melody (red), 1 Bass (cyan), 2 Chords (green), 3 Arpeggio (pink), 4 Drums (gold)**. Numeric types are explicit Java correspondence, not array positions to be inferred elsewhere. Only the current block-based melody algorithm is in scope.

Separate two kinds of settings:

- **Part settings:** values consumed when producing MIDI, including probability values such as pause, note variance, and split chance. Put frequent controls in the panel between arrangement and score, and the complete set in the inspector.
- **Track generation settings:** rules used to choose/change part settings when rerolling tracks, including randomize-pattern toggles, length bounds, maximum split, and hits constraints. Put frequent rules directly in each expanded Tracks group header; expose all supported rules directly in the expanded Tracks header, with no reroll policy in the musical inspector.

Classification follows the consumer of a value, not its name or whether it uses randomness. For example, `exceptionChance` determines generated notes; `randomArpMaxExceptionChance` chooses that parameter on a track reroll. `randomArpCorrectMelodyNotes` and `randomArpUseOctaveAdjustments` are direct phrase-generation inputs despite their names. Some controls in the old tab headers therefore belong with part settings.

Part transpose is locked to octave multiples within -36…36 for Bass, Chords and Arpeggio. Their quick-panel and inspector knobs use 12-semitone steps, and new scoped setting commands enforce those steps. Previously saved in-range values remain readable without discarding projects; the next knob edit selects an octave value. Melody's part knob additionally permits the 5- and 7-semitone offsets in each octave (the old Java melody set also included 4, which is excluded by the new requirement). A possible global preference to unlock all semitone values belongs to the later ExtraSettings stage. Transport/key transpose retains its existing semitone behavior.

The inspector remains the complete editor. A quick control and its inspector equivalent bind to the same value, validation, effective-value resolver, and mutation command. Shared musical controls must be available across every applicable role; Chords is the first implementation consumer, not their exclusive owner. Accent belongs only to Melody. A drum track owns one percussion note and one rhythm; the groove is the combined output of drum tracks. Global harmony, transport, playback/mix, and application preferences retain distinct homes.

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

For every role and settings scope, delay count/duration/velocity, part Seed/Clear seed, rhythm preview, and VGRID/custom-velocity toggle/grid appear only in the Inspector. The editable custom rhythm grid stays in quick Part settings as well as the Inspector. Both editors continue to resolve the same scoped settings and commands.

The **Part settings** panel sits inside the center column, immediately after arrangement and before score. It starts expanded. Its always-visible heading contains collapse/expand, role color, scope breadcrumb (for example `VERSE1 · Chords · C2`), inherited/customized status, Reset to inherited, and Open inspector. Collapsing it preserves context and values. Expansion is UI state, not a musical history entry.

Use two compact rows where space permits: fill + pattern selector/grid, then knobs for transpose, hits, span, repeat, shift, pause, swing, and velocity range. Length, Split, and Offset are inspector-only; inspector controls do not duplicate quick access. Rhythm is the first category, followed by Timing (Length, Split, Swing, Offset, Delays, and delay duration), with Hits in Rhythm. Seed and Clear seed precede the inspector categories. Add a small role-specific subset from the placement map. Show only supported controls; never ship active controls that have no generator effect. A role cell aggregates its member tracks: unequal values show Mixed, not the first track's value. A mixed pattern shows a summary until the user explicitly chooses a common pattern. At supported viewport widths of at least 700 CSS pixels, wrap or scroll the quick controls while leaving score usable. Portrait mobile compatibility is out of scope.

Tracks group headers show an editable boxed total beside the name plus Add/Reroll. The total remains editable when collapsed; separate Count/Generate N controls are removed. Commit on Enter or blur. Clamp the requested total to max(locked track count, requested total). Decreases remove unlocked tracks starting with the highest number and clean up their section presence/overrides; increases append generated tracks using the role policy; the same total does nothing. Each change commits once, preserves surviving tracks, and respects the 64-track project limit. Zero is allowed when no tracks are locked, including an empty project. Expansion exposes all supported track-generation rules directly above the track rows. The Inspector has no track-generation policy, reroll action, roll count or explanatory policy text. Header expansion and group selection are separate actions. Track rows contain compact instrument/percussion and MIDI-channel selectors, mute/solo, visibility, and optional reroll lock/action. Identity controls edit their own track without switching selection. The Inspector has no duplicate instrument/channel section. The row G toggle and GEN/Generate notes controls are removed. Arrangement presence determines score inclusion; mixer S/M controls determine page audibility. Randomization rules do not appear as controls in the central Part settings panel.

Do not replace the old removed mode-controls drawer with this panel: it edits the active musical context and does not change workspace routes or modes.

## State and generation design

State boundaries (use the current model's concrete field names):

- Project musical settings: harmony, tempo/key/mode, explicit global musical defaults.
- `roleDefaults[role]`: part defaults used by newly added tracks. Group edits explicitly apply a patch to current member tracks and update the defaults in one command, preserving independent identities.
- `tracks[id].partSettings`: independently stored base MIDI-generation settings. The current `generatorSettings` contains this kind of data; do not repurpose it into a randomization policy accidentally.
- `trackGenerationPolicies[role]`: serializable reroll options, bounds, and automatic triggers. Add per-track policy overrides only where required by existing behavior; track locks are a separate field.
- `section.rolePartOverrides[role]`: sparse musical patches for a section cell.
- `section.trackPartOverrides[trackId]`: sparse musical patches for an individual section part.
- Section `trackPresence`, chord/duration overrides, variations, and section-wide musical overrides stay separate from part settings and reroll policy.

For a track in a section, resolve **track base → section role patch → section track patch**. Role defaults initialize tracks; they are not another hidden layer that overwrites independent track bases. Section-wide tempo/harmony/swing rules form a separate generation context with explicitly documented precedence. Preserve patch omission: absent means inherit; zero and false are real overrides. Pattern grids/custom velocities replace complete arrays; do not merge arrays by index accidentally.

Example: C1 base transpose 0 and C2 base transpose 12; the section-2 Chords cell displays Mixed. Setting that cell to -12 adds a section-role patch affecting C1 and C2 there. Setting section-2/C2 to 24 adds a higher-priority track patch. Resetting C2 restores -12; resetting the role patch restores C1=0 and C2=12, while any explicit C2 track patch remains until reset separately. Editing the global tracks must not remove local patches. A newly added chord track inherits the section-role patch but no other track's local patch.

Sparse inheritance is the translation's design; Java stores customized lists of complete `InstPart` values on `Section`. Preserve the useful musical result and make propagation explicit. **Freeze effective settings** creates an explicit full snapshot that no longer follows globals; do not freeze unrelated fields on the first knob edit.

Keep overrides effective during regeneration and presence rerolls. Editing a cell automatically creates its patch; a user should not need to enable a hidden MANUAL flag. Use visible customized indicators and scoped reset/apply/copy commands to replace the useful old MANUAL/Apply workflow. Any later automatic reset-on-compose option must be explicit and default to preserving local edits.

Define commands separately: Regenerate MIDI (same part settings), Reroll track settings (policy chooses new base values), Reroll arrangement presence, and Reroll selected section part settings (policy writes local patches). Do not overload the existing arrangement REROLL, which currently randomizes presence. Rerolls never add/remove tracks; changing the role total is the separate membership command. Honor track locks and instrument locks; preserve track IDs, channels, and mix state.

Use explicit JavaRandom streams. Java's panel reroll code sometimes uses unseeded `new Random()`; those paths need a documented deterministic seed policy, not a claim of exact replay compatibility with an unrecorded Java run. Preserve seeded phrase-generator consumption order. Resolve each section's effective settings before generating its notes, and share that resulting arrangement output among playback, previews, score, and MIDI export.

Current `editedPhrase` is a track-wide replacement, including during section-scoped part edits. Keep manual notes when changing generator settings and show when they mask generated output; Restore generated notes is the explicit way back. Future section-owned editing requires an explicit section + track ownership model that retains existing track-wide replacements. Do not silently discard notes or present a masked section as regenerated.

All musical commands pass through ProjectService and commit immutable historical state. Knob drag and pattern painting preview during a gesture and commit once on release. Export/import/session restore must preserve policies, part settings, overrides, locks, and exact decimal seed strings. Validate values and references without version gates or migrations, per translation rules.

Knobs and native comboboxes reset to their default value on right-click through the normal scoped validation/history commands. Removing a sparse override is a separate inheritance action. Melody New blocks defaults to **25%**. Target mode defaults to **2**; all three modes are supported, and targets accept **-24..24**. Mode edits immediately reinterpret stored target lists; permitted Compose/Regenerate events prepare new lists. Basic chords only is omitted because its pitch consumers are legacy-only and it has no musical effect in the current block path. Java null chord-index pitches recover deterministically toward zero; fixture parity claims remain bounded as documented in the archive.

## Delivery stages still open

P6g/P6h are complete in the audited supported scope. Drums has Dynamic/Ghosts, Overrandomize and Follow Melody policies; Arpeggio has GUI pause baking/growth, later-track pattern/fill weighting and weighted directions/rotation; Melody creation writes its extended exception/lead values. The Bass source audit found no GUI baking/growth calls and no production Pause consumer, so no ineffective Bass control was added. See [the P6 completion record](__work_history/p6-creation-record.md) for deterministic streams, Java evidence and intentional differences. Existing automatic target preparation remains intact.

### P7 — Remaining workspace acceptance

#### P7c — Final polish and acceptance validation

Remaining: repeat the many-track/narrow-window usability and production browser review after P5c–P5f and P6g–P6h, and fix any resulting regressions. Keyboard navigation, mixed values, inspector navigation and the P7a/P7b copy/freeze/reset/global-copy workflows already have implementations and browser coverage; this is a completion check. Check both editors, expanded/collapsed headers, section ranges, manual-note ownership, history and persistence, including 30-track layouts at 1440/780px (viewport widths below 700px and portrait mobile compatibility are out of scope) and the arrangement zoom/device-scale matrix. Run selection/overview/playback and targeted resolver/reroll/export checks plus the production build; review screenshots and record final evidence.

### P8 — Deferred global behavior and ExtraSettingsGUI

Port remaining app/playback/device/export preferences separately. Musical settings from ExtraSettingsGUI remain in a later global musical-settings inspector, not automatically in a reroll header. See the deferred inventory. Java-specific soundbanks and device behavior require deliberate Web Audio/Web MIDI equivalents; Wails remains thin.

### P9 — Custom duration editors and behavior

Deferred from P5e by user request (2026-10-11). Implement Melody custom duration editing and the `melodyUseCustomDurations`, `melodyCustomDurationsRandomWeighting` and `melodyCustomDurationsStrictMode` consumers. Follow production block assembly, custom rests, overlap/gap/boundary cleanup, repeated-block weighting and strict pitch behavior. Keep duration templates distinct from manual-note replacements and generic rhythm masks. Shared section chord-duration/timing foundations are delivered by P5f; use their effective scheduling/export context where required.

Exit: deterministic Java comparisons, independent template arrays, applicable scope ownership, atomic gestures/history, persistence, manual masking, shared dependency/playback/MIDI behavior and responsive editor/browser validation.

### P10 — Drum-driven Melody rhythm accents

Deferred from P5e by user request (2026-10-11). Port `melodyRhythmAccents`, `melodyRhythmAccentsMode` and `melodyRhythmAccentsPocket` through the production drum-driven Melody postprocessor. Define section-local drum input and execution order explicitly alongside first-Melody dependency maps; prevent accidental dependency cycles. Keep these musical controls separate from existing part Accent and Melody/dependent-part Pattern flip.

Exit: Java-backed rhythm/accent comparisons, empty/disabled/manual source cases, independent section context, reproducible output and matching playback/MIDI, scopes/history/persistence, responsive controls and browser checks.

### P11 — Imported melody

Deferred from P5e by user request (2026-10-11). Add imported Melody handling for `useUserMelody`, `dropPane` and `userMelodyScaleModeSelect`, including file/import/editor workflow and production scale treatment. Preserve explicit ownership relative to generated tracks, existing manual phrases, section harmony and first-Melody consumers. Audit imported-source chord inference as part of this workflow while keeping the block generator separate from the excluded legacy algorithm.

Exit: representative import fixtures, explicit ownership/clear/reset behavior, source validation, history/JSON/session persistence, dependency grids and playback/MIDI parity, keyboard/device alternatives and browser acceptance.

## Separate future systems (deferred by user request)

- **Universal dynamic instrument links:** every instrument picker can select a source track ID and follow that track's current instrument, including subsequent rerolls/manual changes. Define chain/cycle handling, deleted sources, percussion compatibility, duplication, persistence and history. Skip the special Arp#1 Copy Melody Inst. rule; a one-time copy does not implement this system.
- **Individual global randomization locks:** a compact clickable **⍟** beside each randomizable main/global track control preserves that control during rerolls. Persist per-control lock state and support history and keyboard activation. The section **snowflake** continues to express inheritance/customization. These locks replace dedicated instrument-lock buttons and remain separate from whole-track locks.

These are not prerequisites for P8 and are not part of the current header simplification.

## Implementation lookup and validation

Paths are relative to `frontend/src/app/`: `core/project/` owns model, scope resolution, validation and immutable commands; `core/music/` owns framework-independent seeded generators and shared section dependencies; `core/audio/` and `core/music/midi-export.ts` consume the same generated output. `shared/workspace-ui.service.ts` owns selection; `shared/workspace-canvas.component.*` and scoped part editors own contextual UI; `features/edit/` owns manual editing.

Trace each Java field through UI → saved model/config → actual phrase or reroll consumer. Every newly active control needs audible/exported behavior, applicable scopes, validated imports, history and persistence. Preserve seeded draw order and exact long seeds. Distinguish helper fixtures from complete-phrase evidence and deliberate translation recoveries from Java parity. Use the [fixture regeneration guide](frontend/scripts/java/README.md), then run relevant functional/browser checks and the production build. Current warnings and prior test counts are historical evidence, not acceptance of new changes.
