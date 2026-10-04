# Generator Refactor Map

This map records the current ownership boundaries from `GENERATOR_REFACTOR_PLAN.md`.

## Generation flow

`MidiGenerator` coordinates one generation run. It prepares the progression, plans arrangement sections, requests instrument phrases, assembles score parts, builds the score, and publishes the result.

```text
MidiGenerator
├── ChordProgressionGenerator       progression and key-change algorithms
├── SectionGenerationPlanner        section variation, substitution, and presence decisions
├── MelodyGenerator                 melody algorithm selection and result coordination
│   ├── MelodyBlockSkeletonGenerator
│   ├── LegacyMelodySkeletonGenerator
│   ├── MelodyExpansion
│   └── MelodyChordInference
├── MelodyPhraseBuilder             melody phrase rendering for a section
├── BassPhraseGenerator             bass pattern generation
├── ChordPhraseGenerator            chord pattern generation
├── ArpPhraseGenerator              arpeggio pattern generation
├── DrumPhraseGenerator             drum pattern generation
├── InstPhraseGenerator             shared phrase result and variation support
└── MidiScoreBuilder                track assignment, combination, and score post-processing
```

`PhrasePatternUtils` and `MidiGeneratorUtils` contain phrase operations shared by generation paths. They receive run or timing values where behavior depends on the active generation settings.

## State ownership

| State | Owner | Lifetime |
| --- | --- | --- |
| GUI config, progression, custom chord inputs, generated chord names, melody patterns, section state, and generated measure length | `MidiGenerator` | One generator run |
| Beat lengths, start delay, melody duration choices | `MidiTiming`, held by `MidiGenerator` | One generator run; immutable |
| Global note duration multiplier and drum track collapse option | `MidiGenerator.RunOptions` copied to the generator; multiplier passed directly to phrase generators | One generator run |
| User melody and target-note choices | `MelodyGenerationSettings` prepared by the melody GUI | Compose settings, passed into a run |
| Melody algorithm intermediate outputs | `MelodyGenerationState` | One melody generator |
| Chord names shown by GUI consumers between generation runs | `GeneratedChordState` | Application session snapshot |
| Timing used by GUI operations on the active MIDI editor | `ApplicationSessionState` | Active editor snapshot |
| Undo manager | `ApplicationSessionState` | Application session |

## Compatibility surface

`MidiGenerator` still exposes generation entry points used by GUI, compose, and offline workflows. They delegate to focused collaborators and remain until those callers move. Progressions are exposed as defensive snapshots, while current package-level orchestration fields are retained for tightly coupled generation collaborators.

`MelodyGenerator` remains the melody facade and run-state bridge. Its package-level result fields are consumed by `MidiGenerator` orchestration. Its former forwarding methods between skeleton generation and expansion have been removed. `MelodyExpansion` receives timing, duration, section, progression, and scale inputs directly. The block and legacy skeleton generators receive timing and duration settings at construction, and progression durations, generated chord names, and scale per generation call; neither retains a `MidiGenerator` reference. `MelodyChordInference` receives section, user-chord, duration, and progression inputs directly, then publishes inferred progression and user-melody outputs through `RunStateUpdater` callbacks at their original points in the flow.

`MelodyPhraseBuilder` receives the run's `MidiTiming` directly. `MelodyGenerator.getTiming()` has been removed.

The active editor timing snapshot is intentionally shared with static GUI and MIDI editing utilities that operate without a generator parameter. Generation algorithms use the immutable timing object held by their own run.

## Verification status

`mvn compile` and `mvn -Dtest=GeneratorRegressionTest test` pass after the duration-state migration and the melody collaborator API cleanup. The regression test confirms that generated MIDI remains byte-for-byte unchanged. Phase 7 is complete; remaining `MidiGenerator` entry points have active callers and remain as the compatibility facade.
