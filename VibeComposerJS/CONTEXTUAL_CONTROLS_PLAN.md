# Contextual controls: future work

Scope revised 2026-10-28. Only the items under Planned work are implementation commitments. Other Java differences below are outside this plan and must not be promoted into work without a new scope decision.

Use [translation rules](TRANSLATION_RULES.md), [workspace contracts](DAW_REDESIGN_PLAN.md), the [frontend entry map](frontend/AGENTS.md), and the [project ownership guide](frontend/src/app/core/project/README.md) for enduring implementation contracts. [CONTROL_PLACEMENT_MAP.md](CONTROL_PLACEMENT_MAP.md) owns Java symbol lookups, Melody setting scopes and exclusions. Do not duplicate those rules or completed checkpoint narratives here.

## Planned work

The following groups describe scope, not a mandatory delivery order. Define interactions between timing, variations, section instruments, locks and links before implementing their shared resolution paths.

### Harmony: spice policies and first/last chord

- Add spice chance, allowed spice families, parallel spice chance, force-to-scale behavior and slash-chord chance, following their actual Java harmony consumers.
- Add first- and last-chord constraints to progression generation. Define how constraints interact with the selected harmony strategy, short progressions and explicit Chordlet edits.
- Store these as project harmony-generation settings, separate from track reroll policies. Explicit chord editing must remain an intentional user action.
- Validate seeded generation, saved policy values, history and the resulting shared harmony used by dependent parts and exports.

### Global swing and beat duration multiplier

- Add project musical settings for global swing and beat duration multiplier, with an explicit enable/inherit model where needed.
- Define precedence against part swing and section timing overrides, including each role's musical exceptions. Specify whether any Apply action writes scoped values or changes a resolved project setting; do not mix those semantics.
- Apply timing once through shared resolution. Score boundaries, dependent rhythms, playhead, loops, playback, tempo maps and MIDI/audio export must agree.

### Context-aware sidechaining

- Redesign pattern sidechaining for Angular's active project, role, track and section contexts. Define the musical transformation from the Java consumer before designing its controls.
- Provide explicit source and destination selection, with a visible scope summary. A section action must use same-section resolved source material and affect only its chosen destinations.
- Define handling for absent, muted, empty and manual sources, local overrides and incompatible roles. Preserve unrelated settings and manual-note ownership.
- Specify whether an operation writes a rhythm patch or establishes a continuing dependency. Coordinate continuing dependencies with parameter linking and reject dependency cycles.
- Preview changes before one historical Apply; Cancel must discard the draft. Java's All/Tab layout and automatic-on-Compose option are not requirements.

### Arrangement variations

- Define supported section-wide and section-part variations and their actual musical consumers. Keep variation-generation chances separate from realized choices and track presence.
- Add indicators in the arrangement canvas and a popup for inspecting, enabling, disabling and rerolling variations in the selected context.
- Distinguish variation state from selection, presence, customized settings and manual phrases. Give indicators accessible labels and keyboard/device alternatives without changing cell/track selection behavior.
- Define precedence against explicit musical overrides and manual phrases, and the effect of Compose, Regenerate, presence rerolls, duplication and reset.
- Persist realized choices and policies; use deterministic streams and shared resolved output for score, dependencies, playback and export. Popup Apply commits once; Cancel publishes nothing.

### Global humanization and MIDI padding

- Add global humanization with defined timing/duration bounds, deterministic seeding and explicit interaction with part humanization, swing, section boundaries and manual notes. Prevent repeated application across consumers.
- Add configurable MIDI padding based on Java's output-track padding behavior. Define target role track counts, ordering and metadata; padding must not create project tracks or change audible arrangement content.
- Place humanization with global musical settings and padding with MIDI export settings. Define their persistence ownership and defaults explicitly.
- Verify matching humanized notes across score/playback/export, and inspect exported MIDI structure for padding, empty roles and projects exceeding the requested padding counts.

### Instrument pool presets

- Add named, configurable instrument pools that users can select for instrument choices and rerolls. Pool presets contain instrument membership, not track settings or manual phrases.
- Support creating, editing, deleting, importing and exporting pools, plus resetting to app defaults. Define reset behavior for custom pools and current selections before implementation.
- Validate instruments and percussion compatibility, empty/duplicate entries, name collisions, deleted presets and invalid imports. Specify how current track instruments outside a selected pool are displayed and treated on the next reroll.
- Separate the reusable preset library from project references or snapshots so exported projects have explicit, reproducible pool membership on another installation. Define history for project selection and independent persistence for the library.
- Preserve deterministic selection from a stable pool order and honor locks and links.
- Later, ship optional importable genre pools such as EDM and Pop through the same format. Shipping those collections is a later extension, not a prerequisite for the editor.

### Section-specific instrument identity

- Add a sparse instrument override for a section and track, with explicit inheritance, reset, copy and duplication behavior. Keep instrument identity separate from musical part patches and track presence.
- Provide contextual editing with a visible indication of the effective instrument and its source. A section edit must not silently change the global track instrument.
- Resolve instruments at section boundaries for playback and export. Handle overlapping notes and pickup/tail notes by their owning section; avoid program changes retimbreing another active voice on a shared MIDI channel.
- Define channel allocation/export strategy, percussion compatibility, source deletion and interaction with pool presets, parameter links and randomization locks.

### Per-control randomization locks

- Add a compact clickable **⍟** beside each randomizable global track control. A lock preserves that parameter during applicable rerolls and automatic Compose randomization; ordinary manual edits remain possible.
- Persist lock state with project history and provide keyboard activation and accessible state labels. Keep whole-track locks and section inheritance/customization distinct.
- Replace dedicated instrument-lock buttons with the instrument parameter's lock while preserving clear semantics.
- Define lock granularity for combined controls and arrays, copy/duplication behavior, and precedence with linked values. Locking a linked destination must have an explicit meaning rather than silently breaking or freezing its link.

### General parameter linking

- Allow a compatible parameter to follow another parameter by stable source identity. Instrument choice is one supported use case, not a separate instrument-only system.
- Define eligible parameter types, source/destination scopes and compatibility rules. Establish direct value-following first; transformations or cross-type mappings require separate design.
- Show the resolved value and link source, provide link/unlink actions, and specify whether unlinking keeps the effective value or restores local inheritance.
- Propagate source edits and rerolls through a shared resolver. Define interactions with section overrides, pool restrictions, manual notes and per-control locks.
- Handle chains, reject cycles, and specify missing/deleted sources, duplication and import validation. Resolve links deterministically without reactive update loops or extra history entries for derived values.
- Persist link definitions, preserve independent track identities, and make score, generators, playback and exports consume the same effective values.

## Other remaining differences: no implementation planned

These are scope boundaries, not a deferred delivery queue. Existing exclusions and Angular-specific contracts documented in the linked owners above remain authoritative.

| Family | Difference remaining outside the planned scope |
| --- | --- |
| Harmony generation | Java repeat policy, chord formula and long-progression similarity controls; primary/global custom chord durations and Java N/S/Dd shortcuts; copy-generated-chords preference. |
| Melody custom durations | Optional non-strict duration reassignment and MIDI transfer inside the duration popup. Dedicated Java duration-oracle evidence remains a validation gap, not a claim of full parity. |
| Drum-driven Melody accents | `melodyRhythmAccents`, mode and pocket postprocessing. Do not fold these into part Accent or implicitly add them to sidechaining. |
| Imported Melody source | Java's dedicated imported-Melody source/scale policy and imported-source chord inference. Any new use of imported/manual notes as a harmony source needs its own scope decision. |
| Other global musical/output rules | Arpeggio-affects-BPM, global note-length and swing-unit multipliers, MIDI stretch, arrangement MIDI velocity scaling, and Java global force-scale rules. |
| Automatic/batch behavior | Compose timing randomization, configurable Compose BPM bounds, ordered transpose generation, automatic sidechaining, pattern-pause policy, transpose batch actions and automatic reset of customized settings on Compose. Dedicated section recompose/replace actions are also outside scope. |
| Other preferences | Java playback-position/pause/start/snap/preview options, configurable MIDI CC/channel-copy behavior, external soundbank loading, velocity-value/pattern presentation options, regeneration-history policy, timestamp preference and permissive out-of-range values. |
| Instrument policy beyond presets | Java-specific banned-instrument/all-instrument/reinitialization switches need not be reproduced as separate controls. Design preset membership and reset behavior directly. |

## Validation for future changes

For each slice, trace UI → saved model → effective resolver → musical/output consumer. Define defaults, scope precedence, manual ownership, reset/copy behavior, validation, history and JSON/session persistence before exposing an active control. Keep random streams explicit and distinguish Java comparisons from intentional Angular behavior.

Run the relevant checks from the [frontend test guide](frontend/scripts/README.md). Build production assets before affected browser suites. Review both editors, expanded/collapsed headers, mixed scopes, section ranges, keyboard/device alternatives and history/persistence. Include 30-track layouts at 1440/780px and the arrangement zoom/device-scale matrix when changes affect workspace layout. Record fresh evidence for each delivered slice; do not treat old test counts as acceptance.
