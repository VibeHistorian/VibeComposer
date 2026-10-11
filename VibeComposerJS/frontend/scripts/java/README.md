# Java translation fixtures

Fixtures in [../fixtures](../fixtures/) compare TypeScript helpers and supported complete phrases against production Java. Detailed completed checkpoint coverage, parity boundaries, deliberate recoveries and older manual compilation commands are archived in [the Java fixture record](../../../__work_history/java-fixtures-record.md). The [active backlog](../../../CONTEXTUAL_CONTROLS_PLAN.md) defines remaining translation work.

## Regeneration

Run from the repository root with the existing `midimasterpiece/target/classes` and dependency JAR. Scripts compile the current production consumers ahead of bundled classes; stale classes must not become the oracle. Override `-Jdk` if needed.

```powershell
& 'VibeComposerJS/frontend/scripts/java/generate-melody-extended-fixtures.ps1' -Jdk 'C:/Java/jdk-24.0.2' -Modes
```

| Script (in this directory) | Options / output |
| --- | --- |
| [generate-melody-extended-fixtures.ps1](generate-melody-extended-fixtures.ps1) | No switch: part articulation. `-Globals`, `-Effects`, `-Targets`, `-Catalogue`, `-Emphasis`, `-Tonal`, `-Harmony`, `-Modes`: corresponding complete Melody phrase matrices. Use one selection switch per run. |
| [generate-melody-target-fixtures.ps1](generate-melody-target-fixtures.ps1) | No switch: extended target helpers. `-Modes`: target preparation/starting notes for all three modes. |
| [generate-melody-catalogue-fixtures.ps1](generate-melody-catalogue-fixtures.ps1) | Static block catalogue/helper random-state fixtures. |
| [generate-melody-flip-fixtures.ps1](generate-melody-flip-fixtures.ps1) | Melody dependency-map flip fixtures. |
| [generate-p5-fixtures.ps1](generate-p5-fixtures.ps1) | Arpeggio/drum complete phrases and same-section Melody onset maps. |
| [generate-drum-reroll-fixtures.ps1](generate-drum-reroll-fixtures.ps1) | Production drum-kit pitch selection. |

Other helper/core/policy fixtures use the Java fixture classes in this directory; their exact `javac`/`java` commands remain in the archived record. Run TypeScript regression checks after regeneration and review changed fixtures rather than treating regeneration alone as validation.

## Evidence boundaries

Helper matches do not establish complete phrase or full-song parity. Retain each matrix's fixed harmony/timing context and disabled branches when making comparisons. Complete mode-0 phrases avoid Java's negative-pitch abort; the mode matrices disable Split long/Lead chords where Java's pitch-range helper can throw. TypeScript's documented null-index/empty-range recoveries are tested separately, not claimed as matching successful Java output. Section variations, inference and future P9–P11 behavior need their own evidence.
