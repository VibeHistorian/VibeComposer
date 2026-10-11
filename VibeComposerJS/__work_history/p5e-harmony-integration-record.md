# P5e: generated Melody harmony integration

Delivered 2026-10-11 for the current block generator and existing one-chord-per-four-beat scheduling model. The [preceding consumer audit](p5e-harmony-inference-audit.md) records the production call chain and separate imported-source branch. Variable section harmony/durations/key/tempo context remains P5f; custom Melody durations, drum rhythm accents and imported Melody remain P9–P11.

## Delivered behavior

- `melody-generator.ts` publishes a copied pitch/rhythm skeleton before expansion, repair, pauses, key/scale conversion, swing and feedback. The snapshot removes the transpose that TypeScript applies during assembly because Java applies part transpose after its skeleton stage. Publication consumes no random draws and does not change the generated phrase.
- `melody-chord-inference.ts` reproduces eighth-note weighting, the top four pitch classes, production HashMap chord order (`C,F,G,Em,Dm,Bdim,Am`), rank-two selection and circle tie preference. Skeleton rests retain Java remainder semantics and match no candidate chord.
- `project-harmony.ts` derives base and inferred contexts from immutable projects. Inference uses the first stored Melody track's base settings, independent of arrangement presence, section part patches, later repairs and manual phrases. Its metadata identifies the source track and reports missing/disabled sources. The inferred progression preserves primary first/last roots and Chordlet metadata, replaces interior chords and uses ordinary flattening even when progressive voicing is selected globally.
- Canonical roots and voiced chords remain separate. Chords/Arpeggio consume voiced chords; Bass consumes unsquished roots; Melody consumes its canonical root and repair arrays. Sounding arrays retain the original Chordlet tones, carrying canonical octave moves onto them. This preserves chromatic roots/spices when inverse mode conversion merges two canonical pitches; each original tone is matched once.
- `harmony-voicing.ts` implements ordinary/progressive flattening with a dedicated full-long project-seed stream, Java's average epsilon, skipped nonzero inversion draws and large-chord gating. Flatten voicing defaults to 100%; Flatten large chords and Progressive voicing default to false. Per-part open voicing remains a separate downstream operation.
- The section inspector exposes **Use Melody harmony** for generated pitched roles and **Swap Melody** for Melody alone with seed offset 100. Both can be selected together. Explicit section degree harmony takes precedence; missing/disabled inference falls back to base harmony without changing stored choices. Selection edits preserve independent sparse role/track patches and other sections.
- The detailed inspector exposes **Global harmony · all tracks and sections**, with Flatten voicing %, Flatten large chords and Progressive voicing. Controls have actual shared phrase consumers and use ProjectService validation/history/session/JSON commands. The voicing knob resets to 100% on right-click and commits a gesture once. Harmony settings are absent from part patches and track-reroll policies.
- Manual `editedPhrase`, including an empty replacement, retains audible note ownership and the existing Melody dependency grid. Inferred metadata can use its hidden generated base skeleton; the inspector labels that source and shows when manual notes remain active. Restoring generated notes remains explicit. Imported-source inference is not enabled by manual editing.
- Playback scheduling, score, arrangement previews and MIDI use the same `layOutTrackPhrase` section context. Generated Melody dependency maps use the selected Melody harmony and seed offset; inference itself always uses base harmony, preventing dependency cycles. The legacy role layout helper also resolves alternate section harmony.

## Intentional translation boundaries

Java can replace an explicit section progression when Swap Chords is enabled and counts match. The translation preserves explicit section harmony instead. Java's short inferred progression can duplicate its one-chord endpoint; one/two-chord progressions retain base roots/voicings here. Java marks explicit inversion syntax, including `.0`, whereas this model stores an inversion value: only nonzero inversions protect the base voicing and skip a flatten draw. Inferred flattening protects no inversion slots, as production does.

The source follows stored track order and the translation's existing deterministic track-ID seed derivation; this is not a claim of Java panel-order/unseeded-reroll replay. Reordering/removing the first Melody changes the source explicitly; a disabled first track does not redirect inference to the next track. Manual-note replacements remain distinct from P11 imported sources. Automatic section-variation chances and the independent Skip N-1 variation are outside this explicit harmony-selection slice.

Sounding Chordlets retain their absolute edited pitches across transport key/mode changes, including chromatic tones that have no unique inverse-scale representation. New keyed generation checks also exposed missing diatonic enharmonic lookup (for example E♯ in F♯); `getPitchClass` now resolves those spellings and repeated accidentals.

## Evidence

`scripts/java/generate-harmony-inference-fixtures.ps1` compiles production inference/skeleton/voicing consumers ahead of bundled classes and creates `scripts/fixtures/harmony-inference.java.json`:

| Matrix | Evidence |
| --- | --- |
| 256 inference maps | Empty/rest-only slots, frequency ties/circle decisions and rhythm weighting with fractional notes and chord duration limits |
| 192 progression voicings | 0/25/67/100% chance, ordinary/progressive flattening, large chords, protected inversions and four exact long seeds |
| 96 generated base skeletons and alternate progressions | Four long seeds, four speeds, static/generated block mixtures, pattern effects, doubled rhythm, pitch exceptions and 0/5/7/-12 part transpose |
| 192 complete inferred Melody phrases | Same supported C-major base matrix, using inferred roots/voicings with seed offsets 0 and 100; pitches, onsets, lengths and velocities match Java |

The complete phrase oracle is bounded to C major and fixed four-beat chord durations, with custom templates, embellishment/part variations, split and lead branches disabled. Existing complete Melody fixture matrices continue to cover their supported articulation/target branches independently. Helper evidence does not establish complete Java song parity for arbitrary section variations or future timing/import behavior.

Functional checks cover both section choices and combined selection, explicit section precedence, sparse overrides, unchanged other sections, first/missing/disabled/absent/manual sources, short progressions, atomic invalid commands/imports, undo/redo, exact-long JSON/session replay, independent root arrays, chromatic Chordlets in four keys and both modes, and direct equality between the actual playback schedule, score notes and parsed MIDI output across all roles.

Production browser coverage extends the existing 30-track workflows at 1440/780/390px with project harmony editing, undo/redo, right-click knob reset, keyboard section selection, independent section choices, source labeling and fitted controls. Screenshots are written to `frontend/.angular/contextual-harmony-{width}.png` alongside the existing workspace/role evidence. The harness bounds local-debugger fetches and handles signal-terminated browser cleanup.

Validation: `npm run test:parts` **182/182**, `npm run test:playback` **21/21**, `npm run test:selection` **13/13**, `npm run test:overview` **19/19**, and `npm run test:contextual:browser` **3/3** passed. Reviewed all three harmony screenshots: section choices, global controls and source/progression labels fit at each width. `npm run build` passed; warnings remain for the initial bundle budget (642.61 kB versus 500 kB) and Pixi's `@xmldom/xmldom` CommonJS dependency. `git diff --check` passed.
