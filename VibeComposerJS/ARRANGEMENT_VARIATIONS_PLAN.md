# Context-aware arrangement variations

Design and delivery plan, started 2026-10-31. This elaborates the arrangement-variations commitment in [CONTEXTUAL_CONTROLS_PLAN.md](CONTEXTUAL_CONTROLS_PLAN.md). Translation, ownership and manual-phrase contracts remain authoritative in the linked project documents.

## Musical purpose

Choose a musical purpose first, then a small set of compatible changes that express it. Bounded seeded randomness selects among suitable alternatives. Avoid independent variation rolls on every parameter or every track.

Angular already derives normalized energy, tension and motion in `arrangement-intent.ts`, and uses these for contextual presence. Energy currently describes layer density, not a measured acoustic property. Buildups can have high tension while choruses have greater energy and lower tension. Java has some contextual section/transition rules, but its part variation selection mostly uses separate chance rolls. This planner intentionally extends Angular's arrangement behavior rather than reproducing those rolls.

Classify parameter effects by activity (audible event frequency), register, tension, weight, directional motion and novelty. Effects are tendencies, not universal monotonic relationships. More notes do not always make a stronger release. A lower register is not always appropriate for a quieter melody; bass may retain its low foundation at a climax. Use octave-safe register operations rather than arbitrary harmonic transposition when register work is added.

Role adapters must describe valid modes, bounds, affected settings and conflicts. Arp rate supplies a default hit count; explicit hits override it. Hits determine grid subdivision, not necessarily sounded-note count. Custom durations constrain melody speed effects. Custom patterns and dependency-driven rhythms need dedicated adapters rather than blind grid changes.

## First slice: engine foundation (original delivery)

Implemented as an explicit opt-in engine and project-command API, with no automatic Compose activation or popup yet:

- Serializable section variation policy (`chance`, `maxChanges`) separate from realized choices. Each choice has track identity, operation kind, signed amount and enabled state; the exact generation seed is stored as a decimal Java long.
- Framework-independent planning consumes derived section energy, tension and motion, actual presence, whole-track reroll locks, manual ownership and explicit patches.
- One choice per track; a shared budget of zero to four changes, default two. Stable salted identity streams keep variation randomness independent of phrase random streams and track traversal order.
- Conservative static catalogue: melody speed ±15 and ascending/descending direction; arp full-grid activity using adjacent subdivision levels; bass octave reinforcement; chord open/close space; supported cymbal full-grid pause adjustment. The latter two are arrangement heuristics, not claims of universal energy effects. No-op and incompatible choices are filtered.
- Resolution: track base → generated variation → explicit section role patch → explicit section track patch. Recheck rhythm eligibility using effective explicit settings after later edits. Never flatten choices into user patches.
- Shared `resolvePartTrack` integration covers phrase generation and melody dependency contexts. Existing manual whole phrases and range-edit replacement continue to own their notes. Presence remains independent.
- Historical commands reroll all sections or one section, enable/disable a track choice, and reset choices. The pure candidate builder can support popup drafts without publishing edits. Regeneration reuses saved choices.
- JSON/session decoding validates seeds, policies, role-operation compatibility, target identities, duplicate targets and bounds. Imports without variations remain unchanged. Section duplication copies choices; moving/changing a section retains choices. Track deletion/count reduction/preset replacement removes deleted references. Duplicating a track leaves its new identity unvaried until an explicit replan, preserving the section change budget.

API entry points: `ProjectService.randomizeArrangementVariations(sectionId?, policy?, seed?)`, `setArrangementVariationEnabled(sectionId, trackId, enabled)` and `resetArrangementVariations(sectionId?)`. Omitting seed creates a fresh 48-bit seed; passing an exact bigint makes planning reproducible. Existing Compose/presence actions do not invoke this API.

Limitations of this slice: no audible preview UI, arrangement badges, semantic gesture catalogue, neighbor lookahead, repeated-section identity, register/contour changes or within-section evolution. Saved operations are relative to current track settings, so a later base edit can alter their effective result. A disabled, absent or incompatible target is inert; explicit settings can mask a choice. Detailed effect/blocking explanations belong to the next slice.

## Follow-up: reviewable variations workflow

1. Delivered: shared inspection explains active, disabled, absent, overridden, incompatible, unchanged and manual-protected choices, with effective values and explicit override sources. Range edits protect their regions.
2. Delivered: the arrangement toolbar's ◇ button opens the whole-arrangement dialog; the selection inspector's Variations button opens section, section-role or section-track scope. Section headings count saved choices; track diamonds distinguish enabled/disabled saved choices from selection, presence, customization and manual markers. Accessible canvas labels explain the indicators; native inspector/dialog buttons and checkboxes provide keyboard/device alternatives. Reroll, toggle and Reset edit a detached draft; Apply validates the current project identity and all choices, then commits once; Cancel/Escape publishes nothing. No audible draft preview is provided.
3. Delivered: generation and presence-reroll switches are saved immediately as UI preferences in Settings → Behavior → Arrangement. The musical chance/budget policy remains project-owned and separate from realized section choices. Full arrangement replacement and insertion honor it; Manual arrangement Compose replans only when enabled. Regenerate and standalone presence rerolls retain choices. See the lifecycle below.
4. Delivered: type/order changes derive current intent and gesture immediately while retaining realized choices. The popup compares the saved plan's optional gesture and each operation's direction with current context, and offers explicit Reroll. Saved purpose records a realized planning decision, not an authoritative intent snapshot.
5. Integrate future per-control locks and links. Manual edits remain possible. Links must use the shared resolver; clarify destination lock semantics and reject cycles before activation.

### Reviewable workflow semantics

`arrangement-variation-inspection.ts` owns effect explanations; `arrangement-variation-draft.ts` builds immutable drafts independently of Angular. `ProjectService.applyArrangementVariationDraft` is the single history/persistence boundary. Any intervening project edit makes the dialog stale; close and reopen rather than overwriting newer state. No generator settings or presence are changed by inspecting or preparing variations.

Policy inputs govern future draft rerolls; the realized policy is stored when rerolled. Whole-arrangement Apply also saves the project's musical chance/budget policy; behavioral settings save immediately in Settings without Apply. Existing choices remain unchanged unless the user rerolls, toggles or resets them. For role/track scopes, existing out-of-scope choices survive reroll/reset and count toward the section budget. If they exhaust it, no new choice can be added. Resetting a whole section removes its variation state; scoped reset removes only selected targets. Whole-arrangement reroll visits existing sections without replacing their identities, order, lengths or customizations. It preserves presence unless the optional presence reroll is selected. Type/order changes keep saved choices; the dialog derives current intent and warns when a choice's direction differs from it. This is a directional assessment, not a saved historical intent snapshot or a full musical-quality score.

### Generation toggle and independent rerolls (delivered)

The controls preserve three independent operations: structure generation, presence orchestration and variation planning. The whole-arrangement dialog supplies variation-only reroll and optional combined presence/variation reroll; neither replaces structure.

- **Generate variations with arrangements** is an opt-in checkbox in Settings → Behavior → Arrangement. It saves immediately as the `generateArrangementVariations` UI preference, default false, outside project history and JSON. Turning it off preserves existing choices. The project stores only `arrangementVariationPolicy` (chance/budget), defaulting to 100% / two changes; imports validate and detach this musical policy without a schema-version gate.
- New arrangement generation (toolbar and automatic-structure Compose) uses the project policy when enabled. Disabled generation creates a replacement arrangement without variation state. Insertion plans only the new section, after generating its presence; existing section choices remain untouched.
- Manual arrangement Compose retains section order and customizations, rerolls presence as before, and replans variations against the final prepared tracks and presence only when enabled. Disabled generation retains existing realized choices in this mode. With Arrangement On Compose off, Compose leaves the arrangement's choices alone. Regenerate and standalone presence rerolls never replan variations. Explicit variation rerolls remain available even when generation is disabled.
- **Reroll presence too** is saved immediately in Settings → Behavior → Arrangement as the `rerollPresenceWithVariations` UI preference, default false. It persists across dialogs and sessions and affects the next whole-arrangement variations Reroll action. When selected, a separate seeded stream generates a complete detached presence map first; variation planning consumes that candidate. The dialog lists changed presence per section and explains effects using candidate presence. A later variation-only Reroll restores original presence in the draft. Reset variations keeps staged presence; Cancel discards both.
- `VariationDraftOptions` explicitly carries optional presence and musical policy to `applyArrangementVariationDraft`. Apply rejects stale projects and incomplete/invalid presence maps, preserves structure, and commits choices, presence and policy together in one historical command. Inspection and draft preparation publish nothing.
- Structure, orchestration and variations use independent streams without consuming phrase-generator draws. Realized presence and choices remain persisted and flow through shared score, dependency, playback and export resolution.

## Musical coordination: context and continuity (delivered)

`arrangement-variation-context.ts` derives variation planning context independently of the orchestration baseline. It reads preceding and following section energy/tension, section-family occurrence, actual present-track count, and section duration including local custom durations and beat multiplication. A verse after a peak thins; the same-energy verse approaching a buildup prepares a pulse. Falling tension into a high-energy section releases pressure while retaining/reinforcing weight. Low-energy endings resolve. The remaining purposes are establish, reinforce foundation and sustain.

Gestures choose compatible signed operations before seeded ranking. Undefined recommendations deliberately keep responsibilities unchanged. Anticipation favors one eligible arp/cymbal activity driver, with melody speed as a fallback and direction as a possible companion; it leaves bass weight and chord spacing alone. New plans choose at most one track per role, within the configured section budget. Quiet solo material retains activity rather than being slowed simply because energy is low. Sections lasting at most four beats get at most one change, accounting for choices retained outside a scoped reroll.

Ranking/chance streams use seed + section family + track identity, independently of phrase streams and traversal order. Hook families group chorus variants, Half Chorus and Climax; verses, buildups and recovery sections have their own families. Compatible hook returns with the same purpose and eligible tracks retain their selected targets/operations, even with different section IDs/types. Purpose, presence, base settings and explicit overrides can legitimately change eligibility or allocation. This preserves variation identity; it does not copy generated notes between sections or guarantee identical choices across different gestures.

Role/track rerolls receive the full orchestration separately from their selected targets, so selecting one track does not falsely classify the passage as a solo. Enabled, present activity choices retained outside a scope reserve the anticipation driver; unrelated choices are never rewritten. Whole manual phrases, absent/locked tracks, explicit overrides and incompatible modes retain the established protections. Range edits still own their note windows.

Realized plans optionally persist a validated `gesture`; imports without it remain valid and keep their choices. Moving, changing type or duplicating never silently replans notes. The popup shows current purpose, its reason, recurrence and any saved-purpose/direction mismatch. Reroll remains the explicit musical change boundary.

## Richer adapters: register, contour and controlled lifts (delivered)

Three additional operation kinds use existing shared musical consumers, preserving generator seeds and track identities:

| Operation | Effect and bounds | Protection |
| --- | --- | --- |
| `melody-register` | Shift track transpose by exactly ±12 semitones, never clamp into a partial octave. Resulting transpose must stay within ±24; declared range plus transpose must fit 24–96. Targets beyond ±7 are excluded. | Explicit section transpose/range fields block the operation. Manual phrases remain owned by their editors. |
| `arp-register` | Shift transpose by one octave within ±24 for a single-octave arp. | Authored contour/custom direction, octave adjustment and stretch configurations are excluded. Explicit section interpretation/register controls block the operation. |
| `arp-contour` | Chord-relative diatonic passing motion `[0,1,2,0]` for anticipation or `[2,1,0,0]` for resolution; rhythm, velocity and note-count paths remain unchanged. | Only ordinary full-grid, span-one arps without authored contour/direction, custom grid/durations or octave adjustments are eligible. Contour and chord-relative mode form one atomic operation: an explicit override of either blocks both. |

Octave operations preserve pitch class and the phrase outline rather than rebuilding targets, rhythm or pattern seed. These bounds constrain adapter inputs; project/section transposition still belongs to the established output resolver. Array values are compared by content for no-op filtering. The inspector explains blocked controls and current bounds/settings; authored patterns remain stored intact.

Stronger hook returns may add one positive register operation when their energy is at least .8, exceeds the first hook by at least .05, matches or exceeds the strongest preceding hook, and lasts more than four beats. Repeated returns at that established peak keep the same lift; lower returns do not continue climbing. The shift is always relative to the global track base, never accumulated from an earlier section.

When a compatible future lift exists, hook plans reserve one slot inside the existing change budget. At the default budget of two this means one early anchor and one later accent, rather than replacing an anchor to make room. Larger budgets retain their eligible anchor set and add at most one accent. A bass-weight anchor gets preference while reserving contrast so a release-to-foundation transition retains the bass identity where eligible. Different gestures/presence/overrides may still change other responsibilities. A one-change budget never adds a lift. Reserved slots may remain unused when chance, constraints or role allocation prevent an accent.

The planner uses an independent `hook-lift` stream for selecting the accent, leaving foundation rankings and phrase streams alone. Scoped rerolls preserve an existing out-of-scope register operation and account for it in the budget. Lift eligibility is rechecked at resolution after later base/section edits. The popup describes the lift and shows its effective transpose, while the serialized choice kind/amount, existing history, JSON/session and output paths carry the realization. No new behavioral preference is introduced; variation generation remains opt-in through Settings → Behavior → Arrangement.

## Additional role vocabulary: rhythm and anticipation (delivered)

`arrangement-rhythm-variations.ts` owns four further operations, using the existing rhythm masks and generation consumers rather than independent note postprocessing:

| Operation | Musical effect | Eligibility and bounds |
| --- | --- | --- |
| `arp-density` | Increase/decrease the sounded density of a built-in or Euclidean rhythm. | Non-full built-in grids select the nearest supported hit count that actually changes sounded-slot count; empty reductions are rejected. Euclid changes pulses by one on the existing grid, capped by Hits and never reducing below one. Existing full-Arp activity remains its own operation. |
| `drum-density` | Increase/decrease pulse density for the supported closed/pedal/open hats and ride. | Pitches 42/44/46/53 only; positive density is capped at 16 grid hits/pulses for closed hats and 8 for the others. Negative changes may reduce an authored denser grid. Kick, snare, crash and unsupported pitches are excluded. |
| `drum-ghosts` | Add quiet snare notes in grid rests for anticipation, or remove them while settling. | Pitches 38/40 only; at most eight grid slots with both sounded cells and rests. Existing anchor notes retain their timing, duration and velocity; the drum consumer supplies rest hits at half velocity. Full grids, minimum velocity below two and incompatible rhythms are excluded. |
| `chord-transition` | Raise/lower the existing chance of anticipating the next progression chord on late hits. | Change chance by up to 25 percentage points; positive changes stop at 75 and never lower an already higher authored chance. Require ordinary unjoined/unstrummed grids with both early and late sounded slots, and split between 50–87.5% of the chord. It does not add rhythm hits or rewrite shared harmony; voice count follows the destination chord in the existing consumer. |

These operations require span-one built-in/Euclidean grids without custom grids/durations, melody following, pattern inversion, active custom velocities, pauses or split exceptions. They leave rhythm identity, shift and seeds intact; density may change the subdivision count or Euclidean pulse count. All interpretation controls for each compound operation are protected as a unit against explicit section overrides, including explicit inherited-looking values. Authored/dependent material stays untouched. The actual application percussion catalogue determines supported instruments; this does not introduce another instrument pool or change track pitch.

Density and ghost operations participate in the single anticipation activity-driver allocation; chord anticipation is a harmonic-motion companion rather than another rhythmic driver. Preferred existing Arp/cymbal drivers rank ahead of snare ghosts, with the latter available as a fallback. Chord transitions rise during build-pulse and fall during release/resolve. Saved choices are resolved through the common track/phrase path, with existing manual ownership, section presence, locks, bounds, history and import validation intact. Inspector labels and effective parameter/source descriptions cover all four kinds.

## Contextual range and section capacity (delivered)

The section ceiling is now `max(6, ceil(project.tracks.length / 2))`: six for up to twelve tracks, seven for thirteen, fifteen for thirty, and thirty-two for the supported sixty-four-track maximum. It counts all project tracks, including absent tracks and targets outside a scoped reroll. The default musical budget remains two; chance, eligibility and short-section constraints may leave capacity unused. Popup bounds, project/section policy validation, imports, commands and drafts use the same ceiling. Choices still allow only one operation per track. Track deletion, role-count reduction and preset replacement prune removed identities, clamp saved policies and retain the first remaining choices up to the new ceiling in the same undoable transaction. Imports above the ceiling are rejected rather than silently trimmed.

Budgets up to four retain the conservative one-new-track-per-role allocation. Above four, planning first gives eligible roles an opportunity, then fills remaining capacity by the existing stable candidate ranking. Build-pulse still permits one activity driver across selected and retained choices; larger budgets do not authorize simultaneous rolls on every rhythmic layer. Hook lifts remain at most one and cannot share a target with another operation; at larger budgets they may share a role with another changed track.

`melody-range` expands or contracts the contextual melody search range around its authored centre: positive changes lower the minimum by two semitones and raise the maximum by two; negative changes do the reverse. Resulting width must be 12–36 semitones, declared endpoints must remain in 36–84, and endpoints plus track transpose must fit 24–96. Reject the whole operation when a bound fails; never clamp one endpoint. These are conservative adapter envelopes, not instrument-specific performance ranges. The existing generator applies tonic/section/project pitch transformations normally.

Recommend expansion for reinforce-foundation and contraction for thin/resolve when more than one track is present. Direction and activity retain their existing priorities, so contraction can be a fallback when those controls are owned or unavailable. Range changes rebuild contextual pitch choices rather than transposing an existing phrase; they make no additional random draws and do not change rhythm settings or seeds. A track still receives at most one operation, so range and octave lifts cannot stack. Explicit section range or transpose controls protect the operation atomically. Whole manual phrases and local replacements remain owned; range edits preserve their windows.

Planning, inspection and effective settings resolution receive the project's global Melody mode. The operation is inactive when contextual Melody is disabled, including saved choices after switching mode. Generation, dependency contexts, scoped editing/copying and output share this resolution. The inspector shows both effective range endpoints and marks incompatible mode/bounds normally. No new preference or automatic activation is introduced.

Next implementation: a discrete section-end event, beginning with a bounded pre-drop cut, using section-relative beat windows and shared pre-dependency note resolution. Specify onset/end clipping and manual-region exclusion first; validate short and nonuniform sections, repeat slots, dependencies and exports before adding fills. Continuous density/register ramps follow as a separate envelope consumer, not repeated static range patches.

## Follow-up: instrument profiles and audition

More detailed instrument profiles and musical audition across instrument pools/short and long forms remain separate work. Keep the current conservative envelope and opt-in generation defaults until broader musical validation supports changing them. Automated pitch/timing/output checks do not replace subjective musical-quality review.

Consider effect vectors and compatibility/exclusion groups only as the catalogue grows. The current operations remain static section settings; timed ramps, fills and boundary events are the next distinct consumer slice below.

## Later slice: ramps and boundary events

Static parameter patches cannot represent final-bar fills, progressive subdivision, beat-window contour lifts, short pre-drop cuts or section-end resolutions. These need dedicated consumers:

- Represent section-relative beat windows or normalized progress, resolved against actual section/chord durations, including short sections and nonuniform timing.
- Distinguish gradual parameter envelopes from discrete musical events. Define exact onset/end ownership, pickups/tails, clipping and behavior across repeated progression slots.
- Keep deterministic streams per event and preserve phrase identity outside its window. Apply rhythm-changing events before publishing dependency onset maps; do not postprocess only playback notes.
- Respect manual whole phrases and range edits. Define whether a manual region blocks an event or splits its eligible window. Never rewrite user notes implicitly.
- Share resolved events between score, dependent roles, loops, playback and MIDI/audio export; tempo/time mapping must agree. Cache against immutable project identity and avoid Angular scheduling dependencies.

Boundary fills and ramps should ship as separate validated slices after static variations are inspectable.

## Later slice: shared harmony and timing

Automatic key changes, chord substitutions and tempo changes affect shared musical context rather than just a track. Design their precedence, harmony dependencies, timing, channel/export consequences and explicit override behavior separately. Existing harmony selections must not become a second competing variation system. Coordinate with the global duration/swing work before exposing timing variations.

## Validation

Trace each feature from command/UI through saved state, effective resolution and actual musical consumers. Check exact-seed determinism and stream isolation, shared budgets, meaningful effect/eligibility, explicit override precedence, whole/range manual ownership, absent targets, import rejection, storage/history, duplication/deletion and dependent rhythms. Use multi-seed musical trend checks and audition fixtures for expanded catalogues; do not require every high-energy phrase to have more notes.

Run the relevant functional suites and production build for the engine slice. UI slices additionally require the relevant production browser suites, 30-track 1440/780px layouts, arrangement zoom/device-scale coverage and accessible interactions under the existing frontend test guide.

### First-slice verification, 2026-10-31

- Six focused variation tests pass: multi-seed deterministic budgets/order stability; absent/locked/manual/customized eligibility; override precedence and rhythm-mode changes; storage/history/duplication/deletion; malformed import rejection with diagnostics; shared phrase/dependency output and whole/range manual protection.
- Full unit aggregate: 531 passed on rerun. The first aggregate attempt failed a history JSON comparison; this did not reproduce in the full rerun. It remains a validation caveat rather than evidence of a consistently clean aggregate.
- TypeScript check and production build pass. The build reports the existing Pixi `@xmldom/xmldom` CommonJS optimization warning.
- Production browser aggregate: 24 passed, two failed. Both 30-track contextual workflows (1440/780px) fail the `P5f tempo` assertion at `scripts/contextual-workflows-browser.test.cjs:813`; the same failure reproduced in a focused rerun after the final build. That workflow expects the earlier section BPM edit to remain 87 after subsequent harmony edits. No baseline run was performed, so this is not classified as a proven pre-existing failure. The variation popup/automatic policy is not present or exercised by this suite.
- Local detailed logs: frontend `.angular/variation-unit-check.log`, `variation-browser-check.log`, `variation-contextual-recheck.log` and `variation-build-check.log` (generated validation artifacts, not source deliverables).

### Section-state follow-up, 2026-10-31

The two browser tempo failures were traced to consecutive section inspector events reading a stale Angular section input. Swing editing could replace the newly saved BPM with an older context before rendering refreshed that input. `SectionContextEditorComponent.apply` now resolves the current section by ID from project state before transforming it, and ignores deleted targets. No browser-test delays were added.

Two regression tests retain a stale input deliberately and cover consecutive context/duration edits, external chord edits, undo/redo, scope isolation and deleted targets. All 533 unit tests and the production build pass. Both original 30-track contextual browser workflows now pass at 1440px and 780px. The full browser aggregate was not repeated for this focused fix. Logs: frontend `.angular/section-state-unit-check.log`, `section-state-build-check.log` and `section-state-browser-check.log`.

### Reviewable workflow verification, 2026-10-31

- Full unit aggregate: 537 passed. After adding the canvas indicator regression and further scoped/malformed-draft assertions, the focused variation/overview suites passed all 45 checks.
- Production build passed, with the existing Pixi `@xmldom/xmldom` CommonJS warning.
- Full production browser aggregate: all 28 passed. New 30-track cases at 1440/780px cover draft reroll/toggle/reset, Cancel/Escape, focus return, section scope, one-step Apply/undo/redo, and unchanged order/presence. The existing arrangement matrix passed 45/32/32 cases at display scales 1/1.25/1.5; existing contextual workflows also passed.
- Local logs: frontend `.angular/variation-ui-unit.log`, `variation-ui-focused-unit.log`, `variation-ui-build.log` and `variation-ui-browser.log`. The initial focused browser attempt exposed a synthetic Escape event missing CDK's keyCode; the test now supplies the normal Escape key fields. Application close behavior required no change.

### Generation policy and combined reroll verification, 2026-10-31

- Full unit aggregate: all 542 passed. Additional coverage validates optional/detached project policy, exact-seed arrangement stream isolation, new-section planning, automatic and Manual Compose, retained choices on Regenerate/presence-only rerolls, and atomic rejection of incomplete presence maps. Combined candidates match shared phrase layouts and MIDI output.
- Production build passed with the existing Pixi `@xmldom/xmldom` CommonJS warning.
- Full browser aggregate: all 28 passed. The expanded 30-track 1440/780px cases cover generation on/off, preservation of authored choices when disabling, combined presence/variation preview and Cancel, presence reroll defaulting off, fixed section structure, and one-step Apply/undo/redo. Existing contextual, export, playback and arrangement/device-scale checks also passed.
- Local logs: frontend `.angular/variation-generation-unit.log`, `variation-generation-build.log`, `variation-generation-browser.log`.

### Arrangement behavior preference ownership, 2026-10-31

The generation and presence-reroll switches moved out of the variations popup into Settings → Behavior → Arrangement. Both save immediately in UI preferences, participate in preference import/export, and remain independent of project undo/import and popup Apply/Cancel. The project retains only the musical `arrangementVariationPolicy`; pure generation commands receive the generation switch explicitly from ProjectService. The popup reads the presence preference at each Reroll and keeps Apply for draft musical/presence changes and musical policy only. The toolbar keeps ◇ with a larger 24px glyph in the existing 29px icon button.

Validation: all 544 unit tests, the production build and all 28 browser tests passed. The 30-track 1440/780px cases verify the new settings subsection, immediate local-storage saves without project edits, switches absent from the popup, preference survival after Cancel, and next-action generation/presence behavior. Existing preference transfer, layout/device-scale, contextual, playback and export coverage passed. Build retains the existing Pixi `@xmldom/xmldom` CommonJS warning. Logs: frontend `.angular/arrangement-behavior-unit.log`, `arrangement-behavior-build.log`, `arrangement-behavior-browser.log`.

### Contextual gestures and continuity verification, 2026-10-31

- Full unit aggregate: 550 passed. New cases distinguish recovery/anticipation at the same section energy; test one activity driver over 50 seeds and stable hook anchors over 40 seeds; protect solo pulse; account for duration, scoped orchestration and retained drivers; validate saved purpose across import/move; and confirm activity changes reach the actual arp note consumer.
- Production build and all 28 aggregate browser cases passed, including purpose display/persistence at 1440/780px, existing arrangement/device-scale coverage, history, preferences and output workflows.
- Final review added tolerance at energy/tension delta thresholds so Verse 2 → Chorus 1 reliably counts as a quarter-scale rise. After that adjustment, all 21 focused variation tests, the fresh build and six affected production generation/variation browser cases passed. Full aggregates were not repeated for this isolated threshold correction.
- Local logs: frontend `.angular/variation-coordination-unit.log`, `variation-coordination-build.log`, `variation-coordination-browser.log`, `variation-coordination-final-unit.log` and `variation-coordination-final-browser.log`. The build retains the existing Pixi `@xmldom/xmldom` CommonJS warning.

### Register/contour adapters and controlled lifts verification, 2026-10-31

- Full unit aggregate: all 555 passed. New tests verify exact octave equivalence for both Melody modes and Arp across C Major, F♯ Dorian and B Natural minor; diatonic contour attacks with unchanged rhythm/velocity; atomic override protection; authored-pattern/range guards; and stable anchors with one non-stacking lift over 40 seeds. Locked/manual/customized targets retain normal anchor capacity. New choice kinds survive history/import and produce identical shared phrase/MIDI output after reload.
- Production build passed with the existing Pixi `@xmldom/xmldom` CommonJS warning.
- Full production browser aggregate: all 28 passed. The expanded 30-track 1440/780px fixture includes an early chorus and later climax, verifies the lift explanation and saved operation, and confirms that the bass anchor is retained with exactly one register lift. Existing settings/preferences, arrangement/device-scale, contextual, synthesis and export checks also passed.
- Local logs: frontend `.angular/variation-lifts-unit.log`, `variation-lifts-build.log`, `variation-lifts-browser.log`. This is automated behavioral/output evidence; subjective instrument-pool audition remains follow-up work.

### Rhythmic vocabulary verification, 2026-10-31

- Full unit aggregate: all 562 passed. New tests exercise built-in/Euclidean density against actual arp and drum output, instrument-specific limits and kick exclusions, exact snare-anchor retention with quiet ghosts, and late-only chord pitch changes across 30 seeds. They also cover authored/dependent-grid protection, explicit interpretation overrides, one activity driver plus a chord companion, retained-driver coordination, wrong-role rejection and history/import/shared phrase/MIDI round trips for each new kind.
- Production build passed with the existing Pixi `@xmldom/xmldom` CommonJS warning.
- Full production browser aggregate: all 28 passed. The 30-track 1440/780px fixture uses Euclidean arps and verifies the rhythm-density and chord-anticipation explanations alongside controlled lifts, history, settings and presence behavior. Existing arrangement/device-scale, contextual, synthesis and export suites also passed.
- Local logs: frontend `.angular/variation-rhythm-unit.log`, `variation-rhythm-build.log`, `variation-rhythm-browser.log`. These tests validate consumer behavior and persistence; subjective audition and timed events remain separate work.

### Contextual range and expanded capacity verification, 2026-10-31

- Final full unit aggregate: all 567 passed. New tests cover integer ceiling calculation, larger realized budgets, deterministic ordering, scoped capacity including retained choices, import rejection, and atomic deletion/role reduction/preset replacement through undo/redo. Thirty-seed checks preserve the single anticipation driver and one register lift at larger budgets. Range tests exercise atomic explicit protection, mode/bounds eligibility, positive and negative planning, actual contextual notes and dependency contexts across three keys/scales and ten seeds each, manual whole/local/range ownership, shared MIDI and reload/history behavior.
- Production build passed with the existing Pixi `@xmldom/xmldom` CommonJS warning.
- Final full production browser aggregate: all 28 passed. The expanded 30-track 1440/780px fixture verifies maximum fifteen, rejection above capacity, larger detached plans, and existing draft/history/presence/settings/lift workflows. Arrangement device-scale, contextual, synthesis and export coverage also passed.
- The new browser assertion initially sampled the disabled state before Angular rendered; it now waits for the actual state transition. A later unit run exposed the existing combined-section history test's random initial presence: setting an already absent melody to absent was a no-op, so Undo reverted the preceding type edit. That test now establishes present material before asserting the absent gesture and its undo; production history behavior was unchanged.
- Local logs: frontend `.angular/variation-range-unit.log`, `variation-range-build.log`, `variation-range-browser.log` and `variation-range-browser-focused.log`. Subjective audition, detailed instrument profiles and timed events remain follow-up work.
