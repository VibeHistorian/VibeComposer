# Contextual generation translation

Contextual Melody defaults to **off**, selected in **Settings > Melody**. Harmony generation defaults to **Legacy** (`CURRENT`), selected in **Settings > Behavior**. Functional, Fifths, Pop loop and Modal use seeded phrase planning. Random chooses one of the five concrete strategies using the generation seed, so the same seed reproduces the choice.

The chord progression's Generate action applies the selected harmony strategy to the primary or section progression. Changing the selection alone preserves the current chords. Melody generation works independently over the resulting harmony, including custom chord qualities. Settings are saved with the musical project and support undo/redo and Java XML import.

Contextual controls currently live with the global Melody controls: MIDI range, tension, ending, form, style, chromatic approaches, phrase bars and beats per bar. Range randomization applies on Compose and retains the range on Regenerate. Existing speed, activity, target, contour, catalogue and performance controls feed contextual planning. Controls bypassed by the selected melody algorithm are hidden; editing quick settings exposes dormant controls. Anchors and Reroll span are intentionally excluded.

- TODO: Translate custom melody durations when the app implements them. Contextual generation currently generates its rhythm.
- TODO: Move Blocks (the seven catalogue block percentages), Phrase Bars, Beats per Bar, Pattern Effect and MIDI/Chord Target Mode to **Settings > Melody**.
- TODO: Move New Blocks%, Note Target Mode, Form and Purpose from global scope to **track scope**.
