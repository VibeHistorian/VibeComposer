# Context-aware arrangement variations

Design and delivery plan, started 2026-10-31. This elaborates the arrangement-variations commitment in [CONTEXTUAL_CONTROLS_PLAN.md](CONTEXTUAL_CONTROLS_PLAN.md). Translation, ownership and manual-phrase contracts remain authoritative in the linked project documents.

## Musical purpose

Choose a musical purpose first, then a small set of compatible changes that express it. Bounded seeded randomness selects among suitable alternatives. Avoid independent variation rolls on every parameter or every track.

Angular already derives normalized energy, tension and motion in `arrangement-intent.ts`, and uses these for contextual presence. Energy currently describes layer density, not a measured acoustic property. Buildups can have high tension while choruses have greater energy and lower tension. Java has some contextual section/transition rules, but its part variation selection mostly uses separate chance rolls. This planner intentionally extends Angular's arrangement behavior rather than reproducing those rolls.

Classify parameter effects by activity (audible event frequency), register, tension, weight, directional motion and novelty. Effects are tendencies, not universal monotonic relationships. More notes do not always make a stronger release. A lower register is not always appropriate for a quieter melody; bass may retain its low foundation at a climax. Use octave-safe register operations rather than arbitrary harmonic transposition when register work is added.

Role adapters must describe valid modes, bounds, affected settings and conflicts. Arp rate supplies a default hit count; explicit hits override it. Hits determine grid subdivision, not necessarily sounded-note count. Custom durations constrain melody speed effects. Custom patterns and dependency-driven rhythms need dedicated adapters rather than blind grid changes.

## First slice: engine foundation

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

1. Add a shared inspection result explaining active, disabled, overridden, incompatible and manual-protected effects. Show effective changes and their sources without treating variation state as selection or customization.
2. Build the arrangement popup and accessible canvas indicators. Inspect, toggle, reroll and reset in a local draft; Apply validates current project/target state and commits once; Cancel publishes nothing. Include keyboard/device alternatives and preserve cell/track selection behavior.
3. Introduce explicit Compose/new-arrangement variation policy only alongside these visible controls. Persist project policy separately from section realized choices; specify manual-arrangement Compose, inserted sections, regeneration, reset and whole-arrangement replacement. Presence rerolls preserve choices and inactive targets.
4. After type/order changes, derive the new intent immediately but retain realized choices. Show context mismatch and offer explicit replan; do not silently rewrite music during a move. Avoid saving intent as an authoritative stale snapshot.
5. Integrate future per-control locks and links. Manual edits remain possible. Links must use the shared resolver; clarify destination lock semantics and reject cycles before activation.

## Follow-up: musical coordination

Extend planner context with next-section energy/tension, repeated-section occurrence, section length, track capabilities and actual orchestration. A verse after a climax should settle differently from a verse approaching a buildup.

Add explicit gestures such as establish, thin, build pulse, lift register, reinforce foundation, release tension and resolve. Allocate changes by musical responsibility: one driver builds anticipation while anchors maintain stability. Let sparse solo material carry more activity than an equivalent melody in a dense ensemble. Preserve repeated chorus identity and permit a controlled later lift, rather than independently reinventing every chorus.

Expand role adapters with bounded register/range changes, arp contours, rhythm-aware density, chord transitions and instrument-aware drum behavior. Protect recognizable hooks and custom patterns. Consider effect vectors and compatibility/exclusion groups only as the catalogue grows; do not build an optimizer before there are enough operations to justify it.

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
