# Contextual controls and melody buildout

Status: implementation in progress, 2026-10-08. This phase extends [DAW_REDESIGN_PLAN.md](DAW_REDESIGN_PLAN.md). The attached legacy and translated screenshots inform placement; Java source determines behavior. See [CONTROL_PLACEMENT_MAP.md](CONTROL_PLACEMENT_MAP.md) for old control names and lookup paths.

### UI checkpoint 1 — Selection and contextual panel shell

Implemented the P2 selection foundation ahead of melody: header-only section ranges, section-role/section-track settings targets, middle-click and keyboard I presence toggles, and distinct selection visuals. Header ranges remain separate from settings targets. Added the default-expanded between-canvas panel with breadcrumbs, current-value summaries, and an inspector link. The local inspector provides explicit presence editing and navigation to global track settings. Section-local musical controls remain read-only until P3 wires patches to generation; no local action writes global generator values implicitly.

Validation: 34 overview, selection, and playback tests passed; production Angular build passed (existing Pixi CommonJS dependency warning). Ready for user UI testing. Melody, header reroll-policy controls, quick musical editors, and section patches remain outstanding. The batch intentionally pauses here before those additions.

### UI checkpoint 2 — Editable inherited part settings

Implemented the P3 patch foundation and quick editing for the currently supported role generators. Section role and track patches are validated, sparse, immutable, and resolved before generating section notes. The shared editor in the between-canvas panel and inspector supports the existing role controls plus pitched transpose/velocity bounds and part note length. Mixed values are aggregated across effective track settings. Reset is available per field and per selected local scope; role edits/resets preserve track exceptions. Customized cells and track buttons carry an asterisk.

Patches survive project JSON/session restore, undo/redo, section/track duplication, and presence rerolls. Track deletion removes associated track patches. Velocity bounds are checked against every effective track/section combination, including inherited bounds; invalid edits/resets leave valid settings intact and show feedback. Default pitched velocity bounds remain 69–89, preserving the current translated generators' seeded output until bounds are explicitly changed. Percussion transpose and velocity-range controls are deferred; percussion groove/swing/length controls work now.

Manual restoration refinement: editing a section field back to its inherited value now removes that field's override. For track scope, inheritance includes the cell patch; for cell scope, the value must match every current member's global base (higher-priority track exceptions remain intact). Empty patches are removed, so customization markers and reset actions disappear when the last override is cleared. A mixed global group cannot be cleared merely by choosing one member's value. Cleanup applies only to manually edited fields; Freeze/Copy All snapshots and untouched explicit values remain pinned. Global edits and imports do not silently normalize existing snapshots. Covered by three additional tests for defaults, mixed scopes, history and snapshot ownership (71 functional tests and production build pass).

Saved track-wide manual phrases remain explicit replacements: generator patches are retained but masked by those notes. Both panels identify affected tracks, and the local inspector offers an explicitly whole-track Restore generated notes action. Section-local manual phrase ownership remains a later editor slice. Generated bass octave intervals are applied inside their resolved section; saved manual notes are no longer doubled implicitly. Secondary-track seed derivation is now applied once in arrangement generation, matching the editor; existing secondary tracks can therefore produce different notes from the previous twice-hashed arrangement path.

Validation: 42 tests passed, including real resolver/service/generator/parsed-MIDI export tests and existing overview/selection/playback checks; production build passed with the existing Pixi CommonJS warning. Ready for UI testing; melody, the full pattern/fill engine, and header reroll policies remain outstanding.

### UI checkpoint 3 — Melody role foundation (P1a)

Added the fifth role with explicit Java type correspondence: Melody 0, Bass 1, Chords 2, Arpeggio 3, Drums 4. Melody appears first in Tracks, arrangement, score, editor tabs, mixer and MIDI export, using a red theme. New projects include M1 on free pitched channel 4; the existing defaults remain Bass 1, Chords 2, Arpeggio 3 and Drums 10. Existing saved projects retain their original tracks and channels; a missing melody default/mix is decoded as an optional default, without a schema gate or injected track.

This checkpoint deliberately delivers the role shell allowed by P1, **not automatic melody generation**. The UI marks Melody as manual notes, exposes no inactive musical knobs, and hides/guards Restore generated notes. A fixed `algorithm: 'block'` identity is serialized; legacy algorithms and unsupported melody parameters are rejected. The actual current block generator and its irregular transpose choices (octaves plus 5/7 offsets) remain the next P1 slice, with Java fixed-seed validation still required before generation is enabled.

Added explicit Edit notes actions so empty tracks can open the piano roll. Manual melody notes participate in section presence, pitched audio, instrument/channel/mix controls, score/overview rendering, duplication, history, session/JSON restore, and MIDI export. The local inspector names its note-edit action **Edit notes for entire track**, keeping section phrase ownership deferred. Melody probabilities come from `Arrangement.defaultSections` in the Java app; stored probabilities for the other roles remain intact.

Add now works for every empty role using its stored defaults. A role may have zero tracks; only the final project track is protected from removal. Empty role selections keep their own context rather than falling back to bass settings. Adding Melody does not change other roles' phrase seed derivation or fixed part seed offsets.

UI review: in an existing project use Melody’s + button, select M1, choose Edit notes, add notes and Apply. Check red notes in score/overview, section inclusion with middle-click, playback/MIDI, the mixer, duplicate/delete/undo, and save/reload. Also delete a role’s last member and add it back. Pause at this checkpoint before the block generator port.

Validation: 54 functional tests passed, including real service/editor/history/session and parsed-MIDI tests for Melody plus pitched-audio/canvas checks. The Chromium regression with five arrangement rows passed at 80%, 100%, 125% and 150% page zoom combined with 100%, 125% and 150% device scale; production build passed with the existing Pixi CommonJS warning. Ready for UI testing.

### UI checkpoint 4 — Apply and freeze workflows (P7a)

Delivered this independent P7 slice ahead of the larger block-melody port. The current block skeleton depends on rhythm, block selection and note-processing helpers; automatic Melody remains unavailable until a coherent path has Java fixed-seed evidence. P7a uses the existing supported musical consumers and patch resolver, so its actions are immediately testable for Bass, Chords, Arpeggio and Drums.

Both the between-canvas panel and inspector now provide the same local workflow actions. **Freeze effective settings** snapshots every supported musical field of the selected track into its section-track patch. **Freeze current tracks** does the same independently for every current member of a cell, preserving mixed values and track exceptions; it does not collapse the group into a representative value. Existing cell patches stay intact. Newly added tracks still inherit the cell. This freezes part values, not notes, seed, harmony, instruments, presence or track membership.

Compact panel refinement: reset and freeze are icon buttons centered in the panel header (freeze uses the arrangement customization marker at a larger size). The full cell-and-tracks reset remains a text button and is hidden when there are no local overrides. After a | separator, **Copy to Section(s) (n)** and **Copy All to Section(s) (n)** show the destination count, exclude the source, and disable at zero. Labels use Sections only when n > 1. Hover text explains each action; there is no destination list or workflow description below the header. Feedback uses a compact status marker with hover text and accessible announcement. At narrow panel widths the actions wrap into a centered header row.

**Copy to Section(s)** merges only the source scope's explicit fields into the same destination layer, preserving unrelated fields and higher-priority track exceptions. **Copy All to Section(s)** writes full source-effective snapshots into the corresponding current tracks at each destination; it explicitly replaces those tracks' existing musical overrides. Mixed cell values remain independent by track ID. Presence, manual phrases, mix and track identity remain intact. No global scope is written by either action.

The existing Reset to inherited action resets only the selected layer. **Reset cell + track overrides** is the explicit whole-cell counterpart to freezing all current members: it removes the role patch and all current member track patches in one action. Global values and unrelated role/track patches stay intact. Import/session/history use the existing patch representation; no new persisted freeze flag or schema gate was needed. Copy/freeze retain valid imported semitone values, while new transpose knob edits still enforce octave steps.

Copy buttons also disable when their action would leave all destination patches unchanged. The signal-cached preview shares the mutation's candidate builder and compares only part settings, without generating notes or decoding the project. Equality ignores property order but preserves the difference between inherited and explicit values: Copy All remains enabled when it would create a meaningful frozen snapshot of currently inherited values. No-op actions do not create checkmarks; feedback is dismissible and is hidden when the source, destinations or project change. Two additional tests cover copy availability, undo/redo, field ordering and feedback (62 functional tests and production build pass).

Every action validates the complete candidate before committing. A conflict at one destination rejects all destinations. Each successful action is one undo entry; no-op/rejected actions preserve redo. Deleted scopes/destinations and unavailable/empty roles cannot receive snapshots. Source selection and header ranges stay separate; feedback is cleared by context/project changes. The shared actions wrap at narrow widths, and the between-canvas panel keeps its existing scroll limit.

UI review: select destination headers (Shift for a range), then click a source cell or track. Check the header copy buttons' counts and singular/plural labels. Try Copy with an existing destination track exception, then Copy All; compare the displayed settings, score/playback, undo/redo and save/reload. Freeze a mixed cell, change global controls, and verify the current tracks retain their values while a newly added track inherits. Reset cell + track overrides restores inheritance for all current members. Pause here for UI review.

Validation: 60 functional tests pass, including atomic range conflicts, mixed snapshots, one-entry history, stale targets, session/JSON restore, preserved manual notes and parsed MIDI output. Production Angular build passes with the existing Pixi CommonJS warning. The Chromium arrangement regression passes at 80%, 100%, 125% and 150% page zoom combined with 100%, 125% and 150% device scale. Copy back to globals, broader pattern controls, reroll policies and the block melody engine remain outstanding.

### UI checkpoint 5 — Shared fill controls (P4a)

Added the MIDI-affecting **Fill** selector and **Fill flip** checkbox at the front of the shared quick editor and inspector for Bass, Chords, Arpeggio and Drums. Both use the same commands and resolver at global role, global track, section role and section track scope. Defaults are ALL and false; omission stays omission on import. Melody remains manual-only, with no unsupported controls exposed. This is the fill slice of P4; the full rhythm pattern/grid, hits/span/repeat, pause/split and reroll policies remain separate work.

The pure `core/music/chord-span-fill.ts` ports `ChordSpanFill.getPatternByLength(length, flipped)`. ALL, ODD, EVEN, F1–F4, F12/F23/F34, HALF1 and HALF2 are available. Java ODD plays zero-based odd slots (the second/fourth/etc. displayed chords); EVEN plays the first/third/etc. HALF1 plays the first floor(n/2) slots and HALF2 plays the remainder, including the extra slot on odd lengths. Flip complements the mask; flipped ALL is silence. Hover descriptions make those semantics explicit.

Fill operates over the effective progression, repeating with that progression inside a section. Section-specific chords determine that section's fill length; it does not use the song's absolute bar number or alter section presence. Notes from excluded chords are omitted without shortening the timeline. Bass matches the Java consumer's early chord skip before shared dynamics/variation draws. Chords skip before chord-local dynamics; arp and drums retain draws for excluded chords, matching their rest-based consumers. Default ALL/false leaves the existing translated generator output intact. The current simplified role engines remain partial Java translations; mask parity does not claim full generator parity.

All four scopes support mixed values, explicit false, per-field reset, cell exceptions, Copy/Copy All, freeze, duplication, undo/redo, session/JSON and shared score/preview/playback/MIDI output. Track-wide manual notes continue to mask generator controls and remain intact. Validation rejects invalid fill names/types at all scopes and on import. No schema gate or reroll policy was added.

UI review: select a Chords or Arpeggio cell and try ODD/EVEN and Flip, then HALF1/HALF2 with an odd-length progression. Confirm the timeline keeps its length and the other cells retain their values. Try an individual track exception, reset, Copy/Copy All, Freeze, undo/redo and save/reload. Repeat on Bass and Drums; ALL plus Flip should silence generated notes while section inclusion remains unchanged. The panel keeps its compact header and adds only the two musical controls below it. Pause here for UI review.

Validation: 68 functional tests pass. New coverage includes 264 masks generated by compiling the actual Java enum with the fixture harness in `frontend/scripts/java/`, role-specific random consumption, default-output preservation, odd-length repetition, scoped resolution, history/session/import, retained manual notes and parsed MIDI. The production build passes with the existing Pixi CommonJS warning. The Chromium zoom/scrollbar regression passes across the existing 80–150% page zoom and 100–150% device scale matrix.

### UI checkpoint 6 — Chord static rhythm controls (P4b)

Added Chords **Hits** (1–32), **Shift** (0–8), and **Pattern flip** in the shared quick panel and inspector. Added `one-six` (Java ONESIX) beside the existing full/half/tresillo/sparse/single rhythm choices, and relabeled the picker Rhythm pattern. Existing stored rhythm names remain usable: full maps to FULL, half to ALT, sparse to ONEPER4, and the others to their Java equivalents. This slice affects Chords only; no inactive placeholders appear for Bass, Arpeggio, Drums or Melody.

The new framework-independent `core/music/rhythm-pattern.ts` matches `RhythmPattern.getPatternByLength`: repeat the eight-slot base to cover Hits, rotate that padded list right by Shift, then trim to Hits. Flip complements the resulting rhythm mask. Hits counts subdivisions, not sounded notes: a five-hit pattern divides each four-beat chord into five slots, with notes only at enabled slots. Note length remains a percentage of slot duration. Chord fill independently suppresses entire progression slots; rhythm flip does not invert chord fill. Random velocity is consumed for each grid subdivision, including rests, and default Hits 8/Shift 0/Flip false preserves previous timing, notes and seeded output.

All four settings scopes support the controls, with mixed cells, higher-priority track exceptions, manual return to inherited defaults, copy/freeze, history/session/JSON, track duplication and shared score/preview/playback/MIDI. Imported projects may omit the new optional fields without a schema gate. Invalid bounds/types and unsupported CUSTOM/EUCLID values are rejected. Manual notes remain explicit generator replacements. Custom grids/velocities, Euclidean generation, repeat, cross-chord span/join, and the other roles' pattern consumers remain later slices; no full Java chord-generator parity is claimed.

UI review: select a Chords cell, choose half or one-six, try Hits 3/5/16, Shift 1 and Pattern flip. Confirm only that cell changes, then try a track exception, Fill + Pattern flip, copy/freeze, undo/redo and save/reload. Flipped full produces silence without changing section length or inclusion. Returning a field to its inherited value removes its manual override. Pause at this checkpoint for UI testing.

Validation: 76 functional tests pass. The five new tests cover 192 masks emitted from the actual compiled Java enum plus their 192 flipped complements, default timings, seeded velocity consumption, changed note durations/onsets, dense grids, scoped resolution, inheritance, history/session/JSON, copied snapshots, retained manual notes and parsed MIDI. Production build passes with the existing Pixi CommonJS warning. The Chromium arrangement zoom regression passes across the existing 80–150% page zoom and 100–150% device scale matrix.

### UI checkpoint 7 — Chord Euclidean rhythm and preview (P4c)

Added `euclid` to Chords Rhythm pattern and a **Pulses** knob (0–32, default 4), shown for Euclidean or mixed rhythm selections. Pulses is the requested number of sounded subdivisions before flip. It is capped by Hits during generation but retained in settings, so reducing and restoring Hits does not silently change a second field. This is a deliberate scalar representation of Java's positive-cell count in `comboPanel.getTruePattern()` / `InstPart.customPattern`; Java does not store a separate Pulses field. Custom painting and velocities remain a later P4 slice.

`core/music/rhythm-pattern.ts` ports `RhythmPattern.makeEuclideanPattern()` grouping and rotation. Shift rotates the grouped result before truncation, as in Java; Pattern flip complements it. The shared `chordRhythmMask()` feeds generated notes and a compact, role-colored rhythm preview in both editors. The preview shows Mixed when active rhythm inputs differ across tracks; unused mixed Pulses does not hide a static pattern preview. Fill can suppress entire chords independently. Existing static rhythms, default seeded output and note durations stay intact. Velocity draws still occur for every subdivision, including rests. Zero Pulses is silence before flip; Pulses ≥ Hits produces a full grid before flip.

Global role/track and section role/track settings use the same validation, inheritance, mixed values, manual return to inherited values, copy/freeze, duplication, undo/redo, session/JSON, preview, playback and MIDI pipeline. Melody remains manual-only. No custom grids, velocities, repeat/span or reroll policies are exposed yet, and full Java chord-generator parity is not claimed.

UI refinements since checkpoint 6: controls in Part settings and the inspector inherit the role accent; per-track section checkboxes use their own role color. Duplicate override/reset/freeze/clear/copy controls now live only in the central panel. Add section appears only for section-header context. Its initial displayed selection is synchronized with the stored type, and part choice options use the same explicit selection binding.

UI review: select a Chords cell, choose euclid, and try Hits 8/Pulses 3, Hits 5/Pulses 2, Shift and Pattern flip. Check the preview against generated notes, then combine with Fill. Try Pulses 0 and Pulses greater than Hits, restore Hits, and verify the requested count stays intact. Check a track exception, mixed values, copy/freeze, undo/redo and save/reload. Pause here for UI testing.

Validation: 81 functional tests pass, including all 5,040 production Java masks and flipped complements, seeded velocity consumption, generation timings, section isolation, parsed MIDI, scoped mixed values, snapshots, import/session, history and manual-note preservation. Production build passes with the existing Pixi CommonJS warning. The Chromium arrangement regression passes across the existing 80–150% page zoom and 100–150% device scale matrix.

### Compact control refinement

Part controls now use a shared `ControlHeadingComponent`: left-aligned name and an always-present snowflake at the right. Inherited local values show a grey, inactive snowflake; explicit local fields show the role accent and reset just that field on click. Global values show a grey marker with a Global setting tooltip because there is no parent layer to restore. Both Part settings and the inspector use these per-field markers; duplicate bulk workflow buttons remain only in the central header. Reset preserves track exceptions and the existing history flow.

Knobs show their numeric value in the center instead of a code, with no value row by default. Mixed values show an em dash; full values, units and mixed status remain in tooltips and accessible slider text. `CompactKnobComponent.showValueBelow` / `PartControl.showValueBelow` opts into a full value row for future long values. Choice controls share the same heading; checkboxes sit below their name and align left. Labels remain linked to their inputs, and reset buttons are separate from labels. Validation: production build and all 81 existing functional tests pass. Pause for UI review before adding further controls.

### Arrangement density refinement

The overview now derives a minimum horizontal scale from the largest role's track count and the shortest section. Every cell has enough width for at most two track-button rows, keeping buttons within their role row and leaving a separate note-preview area. All sections still share a proportional beat axis; horizontal scrolling appears when needed. Ctrl-wheel zoom-out cannot cross the fit floor, and zoom-in starts immediately from the effective scale. The automatic floor is separate from deliberate user zoom, so removing tracks or widening the viewport can restore the fitted view without overwriting user zoom. Canvas allocation stays viewport-sized, and hit testing/playhead use the same effective beat mapping.

Validation: production build and 84 functional tests pass. New cases exercise 1–64 tracks, shortest-section fitting, cell containment, note clearance, hit testing, zoom anchoring, track removal and resizing. Chromium regression coverage includes dense cells and transitions back to sparse cells across page zoom and device scaling.

### Score grid density refinement

The score's regular time grid now requires at least 8 CSS pixels between lines. Zooming out progressively removes sixteenth and eighth subdivisions, then beat detail, and eventually shows spaced multiples of bars. Zooming in restores those divisions. Lines remain aligned to the song's beat origin while scrolling, preserve their existing hierarchy colors, and cover the note and velocity areas. Section boundaries remain visible independently of the regular-grid threshold. Note positions, hit testing, playback and editing behavior are unchanged. Validation: production build and 85 functional tests pass, including a renderer-level check of spacing, progressive detail, scrolled alignment and section markers.

### UI checkpoint 8 — Custom chord rhythm grid (P4d)

Added `custom` to Chords Rhythm pattern and an editable **Custom grid** in the shared quick panel and inspector. The grid shows Hits subdivisions of the audible, shifted/flipped pattern. Click or drag to paint notes/rests; Enter or Space toggles a focused subdivision. A gesture previews locally and commits one complete array on release, producing one undo entry. Escape, pointer cancellation, lost capture, and changed project/context discard pending painting. Fast drags paint intervening subdivisions. Editing a mixed grid never chooses a representative track: **Use full grid** explicitly sets a common grid; mixed Hits/Shift/Flip must also be resolved before painting.

The stored `customPattern` is an optional 32-cell binary array, defaulting to all notes. `InstPart.getFinalPatternCopy()` rotates the full custom list, and `ChordPhraseGenerator` then takes Hits and applies flip. The pure TypeScript consumer follows that order. Painting maps audible subdivisions back into the unshifted, unflipped stored grid. Lowering Hits retains hidden cells, and changing rhythm retains the custom array. This deliberately omits Java widget-specific automatic repetition of painted cells; hidden cells remain independently stored instead. Static and Euclidean behavior, fill masking, seeded dynamics and manual-note replacements remain intact. Custom velocities, MELODY1, span/repeat, pause/split and the other roles' grids remain later slices.

Array values replace complete inherited arrays. Content equality supplies mixed-value aggregation, manual return to inherited values, snapshot/copy availability and per-field reset. Validation requires exactly 32 binary integer cells and copies incoming arrays, with no schema gate. Global role/track and section role/track settings, track exceptions, freeze/copy, duplication, history, session/JSON, score/playback and MIDI all use the same settings pipeline.

UI review: select a Chords cell or track, choose custom, and click/drag the grid. Try Hits 3/5/32, Shift, Pattern flip and Fill; reduce and restore Hits to check retained cells. Check Enter/Space, Escape during painting, a mixed cell, per-field reset, Copy/Copy All, Freeze, undo/redo and save/reload. Pause here for UI review.

Validation: 90 functional tests pass. New coverage includes 1,152 masks from compiled production Java `InstPart`/`ChordPart`, plus flipped complements, retained random draws for rests, section isolation, parsed MIDI timing, mixed arrays, scope inheritance, atomic painting history, cancellation/stale gestures, import validation and session reload. Production Angular build passes with the existing Pixi CommonJS warning. The existing Chromium arrangement regression passes across the 80–150% page zoom and 100–150% device scale matrix.

### Rhythm catalogue consolidation

`core/music/rhythm-patterns.ts` is the shared definition of rhythm identities, Java static names and masks. The Chords picker, model type, import validation and generator use the complete catalogue; Bass derives its currently supported subset with explicit exclusions and adds `alternating` for its generated-duration behavior. Bass and Chords share static masks. Arpeggio pitch direction and drum groove presets retain their separate musical meanings until their shared rhythm consumers are ported. Removed the competing `CHORD_RHYTHMS` definitions and inline picker/validator lists.

Validation: 91 functional tests pass, including catalogue acceptance at all four scopes, import and generation. The production build passes. A new Chromium regression loads the actual built Angular app, verifies both chord pickers contain the full catalogue including custom, selects custom, edits a slot, and checks both grids and persisted settings. Run `npm run build` before `npm run test:rhythm:browser`. The source and rebuilt UI expose custom; the user's previous missing option was not reproduced in the new bundle.

### Quick control and mixer refinement

Knobs use vertical pointer dragging (up increases, down decreases), with local preview and one history commit on release. Horizontal motion does not alter values. Wheel scrolling adjusts values using Java's roughly range/20 coarse increments, respecting stepped controls such as octave transpose; Shift uses finer increments. Escape, pointer cancellation/lost capture and changed value/bounds cancel pending drags. Native keyboard range controls remain accessible. All workspace, inspector, transport, mixer and editor comboboxes share `WheelSelectDirective`: wheel down chooses the next enabled option, wheel up the previous, with Java-style wraparound. Ctrl/Meta-wheel retains browser zoom, and handled wheel input does not scroll its parent.

Track rows include 34px-wide mini Pan and Volume knobs immediately before M/S. Their 10px readouts show `100% L` through `C` to `100% R`, and `-0.0` through negative decibels to `-Inf`. Volume uses `dB` in its 18px center label instead of repeating the unit below; tooltips and accessible values retain the unit. Volume remains the existing 0–100 linear gain setting; the readout uses 20×log10(volume/100), so 50% is -6.0 dB. These controls edit the same track mix as the mixer popup and audition drag previews through AudioPlaybackService without project/history mutations. Release commits one action; cancellation restores the stored mix. Mute uses bright lemon yellow and Solo bright lime green through shared theme tokens, both in track rows and mixer, distinct from drum gold and chord green.

Validation: 94 functional tests and production build pass. The built-app Chromium regression verifies wheel selection in both editors, mini-pan scrolling, vertical volume drag preview/commit, persisted mixer values, exact M/S highlight colors, row fit, and unchanged selection. The existing Pixi CommonJS build warning remains.

### UI checkpoint 9 — Custom chord velocities (P4e)

Added a **Custom velocities** checkbox and **Velocity grid** to the shared quick editor and inspector for Chords, at all four global/section and role/track scopes. Each displayed subdivision has a vertical 0–127 slider and numeric readout. Native dragging and keyboard adjustment preview locally and commit one complete array on change/release; wheel adjusts by six, or one with Shift. Escape, pointer cancellation, blur and changed context discard pending previews. Mixed arrays/Hits show a summary; **Use default velocities** explicitly chooses a common 79 grid rather than taking one member's values. Random Min/Max controls are hidden while custom velocities are enabled and return when disabled.

`useCustomVelocities` is a deliberate explicit counterpart of Java's non-null `customVelocities`. It defaults false, leaving existing seeded output unchanged. Disabling retains the array; changing Hits retains hidden cells. The shared `velocity-pattern.ts` supplies defaults, validation and effective subdivision selection. Default 79 is the midpoint of Java's default 69–89 range, not a value automatically recomputed after later Min/Max edits. Both arrays and enable flags support sparse inheritance, explicit false, content-based mixed values, per-field reset, copy/freeze, duplication, history, session and JSON. Imports may omit both fields, without a schema gate.

The Java consumer takes the first Hits custom velocities without rotating them by rhythm Shift or complementing them by Flip. These values replace random dynamics rather than being clamped to Min/Max. The TypeScript consumer follows that order; rhythm/fill still determine which hits play. Custom mode bypasses random velocity draws, while random mode retains its existing draw consumption on rests. Zero velocities are retained in settings but omitted from generated audible notes and MIDI note-ons. Saved manual notes continue to replace generated phrases. Arrangement-volume scaling, span/join, strum dynamics and the other roles' velocity grids remain later slices; this does not claim full Java chord-generator parity.

UI review: select Chords, enable Custom velocities, vary individual sliders, then combine with custom/static/Euclidean patterns, Shift, Flip and Fill. Try zero, keyboard arrows and wheel/Shift-wheel, undo/redo, disabling/re-enabling, reducing/restoring Hits, track exceptions and mixed cells, per-field reset, Copy/Copy All, Freeze, save/reload, playback and MIDI. Pause at this checkpoint for UI review.

Validation: 100 functional tests pass. Six new tests cover 84 Java stored-array/scaling-helper fixture cases, boundary/zero velocities, timing and random-mode preservation, scoped resolution/mixed values, one-entry slider history and stale cancellation, snapshots/duplication, session/JSON and parsed MIDI velocity values. The fixture invokes current production part and scaling-helper source; its subdivision loop mirrors the audited chord consumer and is not a full phrase fixture. The Angular production build and built-app Chromium regression pass; the latter verifies both velocity grids, local preview before commit, shared values after commit, wheel adjustment, hidden random bounds and retained values after disable/re-enable. Existing Pixi CommonJS warning remains.

## Product contract

Keep the single workspace, persistent transport, arrangement and score. Add Melody first in every role ordering: **0 Melody (red), 1 Bass (cyan), 2 Chords (green), 3 Arpeggio (pink), 4 Drums (gold)**. Numeric types are explicit Java correspondence, not array positions to be inferred elsewhere. Only the current block-based melody algorithm is in scope.

Separate two kinds of settings:

- **Part settings:** values consumed when producing MIDI, including probability values such as pause, note variance, and split chance. Put frequent controls in the panel between arrangement and score, and the complete set in the inspector.
- **Track generation settings:** rules used to choose/change part settings when rerolling tracks, including randomize-pattern toggles, length bounds, maximum split, and hits constraints. Put frequent rules directly in each expanded Tracks group header; expose the full set in a clearly named inspector section.

Classification follows the consumer of a value, not its name or whether it uses randomness. For example, `exceptionChance` determines generated notes; `randomArpMaxExceptionChance` chooses that parameter on a track reroll. `randomArpCorrectMelodyNotes` and `randomArpUseOctaveAdjustments` are direct phrase-generation inputs despite their names. Some controls in the old tab headers therefore belong with part settings.

Part transpose is locked to octave multiples within -36…36 for Bass, Chords and Arpeggio. Their quick-panel and inspector knobs use 12-semitone steps, and new scoped setting commands enforce those steps. Previously saved in-range values remain readable without discarding projects; the next knob edit selects an octave value. When Melody arrives, only its part knob additionally permits the 5- and 7-semitone offsets in each octave (the old Java melody set also included 4, which is excluded by the new requirement). A possible global preference to unlock all semitone values belongs to the later ExtraSettings stage. Transport/key transpose retains its existing semitone behavior.

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
