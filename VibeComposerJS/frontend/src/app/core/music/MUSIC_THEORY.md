# Chord and scale definitions

`harmony.ts` owns chord formulas; `scales.ts` owns ascending, distinct tonic-relative pitch classes. Musical definitions take precedence over mistakes in the Java implementation and its oracle fixtures. The Java random algorithm, seeds and unrelated draw order remain unchanged.

The definition audit corrected minor ninths to 1–♭3–5–♭7–9, Locrian's fourth to a perfect fourth, blues to the six-note minor blues collection, and both pentatonic scales to their five distinct notes. Thirteenth voicings retain their defining third, seventh and thirteenth; omitting the optional eleventh is an intentional voicing choice.

The malformed `indian` collection is replaced by Hon-kumoi-joshi (1–♭2–4–5–♭6), also catalogued as an “Indian ascending” scale, with its specific name displayed. The saved ID remains `indian`. “Indian” and “Turkish” alone do not identify a single universal equal-tempered scale. The distinct existing `turkish` and `whiskey` collections remain available with explicit custom labels. No traditional makam/raga behavior is implied.

Scale membership, score highlighting, melody candidates, arpeggio contours, MIDI import and manual phrase conversion use actual collection cardinality. Seven-slot progression schemas use the explicitly separate `harmonyScaleNotes` projection: absent diatonic degrees alias nearby collection tones, and thirds are stacked in those harmonic slots. This preserves the seven-slot project progression contract without padding the actual scale definition with duplicate or incorrect notes. The blues ♭5 remains a melodic passing tone rather than a mandatory chord root.

Saved scale IDs and named chord symbols remain readable. Existing named `m9` chords acquire the corrected formula; explicitly authored interval arrays retain their pitches. Regenerating music in a corrected scale intentionally changes output. Mode conversion between collections with different cardinalities maps proportional scale position, preserving the octave; this conversion can be lossy. Undo restores the original project exactly.

Settings → Behavior → Harmony includes the UI preference “Reinterpret harmony on Mode change”: Off, Global or All (default). Mode edits adapt explicit global chords for Global/All and explicit section chords for All when their effective mode changes. Generated and edited chords are treated alike. Degree-only slots already resolve through the current mode. Loading, undo and redo restore stored harmony without conversion.

`harmony-mode.ts` maps roots and chord tones through the old/new seven-slot harmonic collections, retaining octave offsets and inversions. Chromatic tones use the nearest old harmonic slot, preferring the lower pitch on ties. Resolving dominant qualities are retained when the next major/minor chord is a descending fifth away, including loop boundaries. Results match named chord formulas where possible and otherwise store exact intervals. Smaller collections can merge tones, so conversion is lossy; undo restores the complete previous state. Section mode overrides remain authoritative.

References used in the audit:

- [John Mizarolli: chord formulas](https://www.mizarolli.net/lessons/chord-formulas.html).
- [Josh Gottry: scales for improvisation](https://gottrypercussion.com/wp-content/uploads/2016/10/Theory_ImprovisationScales.pdf).
- [Hon-kumoi-joshi formula and diagrams](https://www.jazz-guitar-licks.com/pages/guitar-scales-modes/other-scales/hon-kumoi-joshi-scale-guitar-theory-with-diagrams.html).
- [Indian scale naming and ascending collection](https://www.pianoscales.org/indian.html).

`scripts/music-theory.test.cjs` tests the formulas independently of Java fixtures. The remaining Java parity suites exclude or explicitly correct the affected oracle values rather than preserving erroneous musical definitions.
