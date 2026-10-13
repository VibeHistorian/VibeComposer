# Contextual melody and harmony: parameter guide

Java app, updated 2026-10-13. This describes current behavior, including controls whose implementation is still limited. See [implementation and usage](OVERHAUL_IMPLEMENTATION.md) for setup and validation.

**Contextual Melody** is in **Extra Settings → Melody**: ON for fresh launches, OFF when an older loaded config omits the setting. Saved choices are preserved. Harmony strategy, Smooth voices, and Co-design are in **Extra Settings → Chords**. Melody phrase controls are in the main melody settings; activity and performance controls are on each melody instrument panel. Some controls require Full mode.

Melody style and harmony strategy are independent: JAZZ melody does not automatically select jazz substitutions, and MODAL melody does not automatically generate a modal progression. Fixed/custom harmony and locks take priority over stylistic preferences.

MIDI range controls describe planned pitches before track/key transposition. Harmony voicing bounds apply to the chord plan; individual bass/arp/chord rendering can use other registers.

## 1. Combo choices and switches

Each row explains one choice, or closely related choices. Switches are included here because they determine which numeric controls apply.

| Control | Choice | What it does |
| --- | --- | --- |
| Melody form | STATEMENT_VARIATION | Alternates statement and continuation purposes. Repeated motif IDs encourage recurring rhythms and gestures. |
| Melody form | QUESTION_ANSWER | Alternates question and answer purposes. Questions receive a slightly higher rest probability; CLOSED can replace the final purpose with closing. This is a lightweight phrase template. |
| Melody form | SENTENCE | Assigns continuation purpose to every third phrase, alongside the ordinary statement/continuation schedule. It is not yet a complete classical sentence model. |
| Melody form | CONTINUOUS | Currently uses the same purpose schedule as STATEMENT_VARIATION; it has no separate continuous-form policy yet. |
| Melody ending | OPEN | Does not require a final tonic or a loop connection. With contextual melody enabled, FUNCTIONAL harmony also allows half-cadence and deceptive phrase alternatives. |
| Melody ending | CLOSED | Gives the final phrase a closing rhythm. The last sounding note must be tonic when the final chord contains tonic; otherwise it remains compatible with the supplied harmony. FUNCTIONAL favors tonic-ending phrase alternatives. Explicit chord endpoints still win. |
| Melody ending | LOOP | Scores the final-to-first melodic interval and applies the leap bound across that join. FUNCTIONAL can also choose half-cadence or deceptive alternatives. This does not force every harmony strategy to end on tonic. |
| Melody style | TONAL | Prefers chord stability and stepwise resolution, including dominant tendency-tone motion. Dissonances need an allowed role and resolution. |
| Melody style | MODAL | Keeps compatibility and ornament checks, but removes the functional dominant-resolution bonus. It does not turn off harmonic constraints. |
| Melody style | JAZZ | With Tension above zero, allows a conservative set of scale-compatible extensions as stable melody notes. Also enables eligible tritone alternatives in the chordlet editor. It is not a comprehensive jazz grammar. |
| Note Target Mode / direction | ANY | Uses a rising-and-falling register arc within each phrase. Direction remains a preference. |
| Note Target Mode / direction | ASC | Biases the phrase's preferred register upward over time; individual notes may still descend. |
| Note Target Mode / direction | DESC | Biases the preferred register downward over time; individual notes may still ascend. |
| Harmony strategy | CURRENT | Uses the historical progression generator. Contextual melody can still run over its finalized harmony. Legacy chord formula/parallel-spice controls apply to this strategy. |
| Harmony strategy | FUNCTIONAL | Selects seeded phrase structures with varied openings, preparation, and dominant, leading-tone, or plagal arrivals. Ending intent and fixed endpoints condition those choices. Minor V7 retains its raised leading tone. |
| Harmony strategy | FIFTHS | Follows the diatonic descending-fifths cycle, including its diatonically adjusted tritone link. Free endpoints permit different starting positions. A fixed start or end determines the phase; incompatible locks can require an interruption. |
| Harmony strategy | POP_LOOP | Chooses a familiar four-degree loop schema and scores its final-to-first harmonic join. It is a schema-based strategy, not unrestricted pop-style generation. |
| Harmony strategy | MODAL | Favors a retained tonal center and nearby vamp chords instead of requiring dominant closure. Allow Chord Repeats affects how stationary it can become. |
| Number of chords | 4 / 8 / RANDOM | Requests four or eight harmonic events, or a seeded choice among 4, 5, 6, and 8. More chords do not automatically mean longer melodic phrases. |
| First / Last chord | `?` / explicit chord | `?` leaves that endpoint free. An explicit chord fixes its definition. For maximum FIFTHS variety, leave both endpoints free. |
| Chord-span fill, per melody panel | ALL | Enables melody throughout all harmonic events. |
| Chord-span fill | EVEN / ODD | EVEN enables positions 1, 3, 5…; ODD enables 2, 4, 6… in the current implementation. These names follow the internal zero-based convention. |
| Chord-span fill | F1 / F2 / F3 / F4 | Enables that position in each group of four harmonic events. |
| Chord-span fill | F12 / F23 / F34 | Enables those two positions in each group of four. |
| Chord-span fill | HALF1 / HALF2 | Enables the first or second half of the event list. Fill Flip complements the selected mask. Masking is based on attack position, not an absolute prohibition on notes sustaining across a change. |
| Contextual Melody | ON / OFF | Selects contextual or compatibility melody generation. Legacy Mode takes precedence if a loaded config enables both. Unused legacy melody controls are hidden while contextual generation is active. |
| Chromatic approaches | ON / OFF | ON permits eligible weak chromatic approach notes resolving by semitone. OFF removes that option, but does not remove chromatic tones required by the actual chord. |
| Use custom durations | ON / OFF | ON uses a supplied, nonempty rhythm; null or empty input falls back to regular generated rhythm. OFF always generates rhythm. Random weighting remains available for supplied rhythm patterns. |
| Smooth voices | ON / OFF | ON optimizes chord register and continuity within Voice Low/High, respecting explicit bass constraints. OFF retains the prepared voicings. It changes voicing, not the intended chord identity. |
| Co-design | ON / OFF | ON permits at most two generated-melody/harmony refinement passes; it requires contextual melody, Legacy Mode off, and a planned harmony strategy. OFF keeps ordinary one-way generation. Attached user melodies remain fixed. |
| Allow Chord Repeats | ON / OFF | ON makes adjacent same-root chords less costly; OFF penalizes them more strongly. This is a preference, not a ban overriding hard locks. |
| Spice: Force Scale | ON / OFF | ON restricts newly added color tones to the active scale while preserving existing chord tones. OFF permits supported chromatic colors; it does not authorize arbitrary root/quality swaps. |
| Spice: 9th/13th | ON / OFF | ON allows eligible extended colors, also including supported altered 9th/11th suffixes. Force Scale can still filter them. OFF excludes these larger colors. |
| Spice: Dim/Aug/6th | ON / OFF | Filters eligible automatic color suffixes. It does not remove a structurally required diatonic diminished chord from FIFTHS. |
| Chordlet Alternatives | CONTEXTUAL | Collects and ranks the currently eligible operations below, subject to destination, property locks, and important attached melody notes. |
| Chordlet Alternatives | APPLIED_DOMINANT | Proposes the dominant seventh of the following chord, such as E7 before Am. Requires a following destination. |
| Chordlet Alternatives | MIXTURE | Proposes borrowed minor iv before tonic, such as Fm before C. This currently covers that specific mixture case. |
| Chordlet Alternatives | TRITONE_SUBSTITUTION | With JAZZ selected, substitutes an eligible resolving dominant, such as Db7 for G7 before C. |
| Chordlet Alternatives | SUSPENSION | Proposes a suspended-third chord before a same-root chord with a major third. The next chord supplies the resolution. |

## 2. Variable parameters: low, middle, and high

Values below illustrate effects, not recommended presets. Except for Speed's explicit thresholds, low/middle/high are points on the parameter's range, not three separate modes. Musical legality and other settings can limit the audible difference.

| Parameter | Low | Middle | High | Scope / interaction |
| --- | --- | --- | --- | --- |
| Speed, −100…100 | **Below 35:** base notes of 1 or 2 beats. | **35…74:** 0.5 or 1 beat. | **75…100:** 0.25 or 0.5 beat. | Generated rhythm only. Pauses, closure, splitting, boundaries, and retries modify these values. Does not change BPM. |
| Melody Tension, 0…100 | **0:** excludes non-chord ornament roles and jazz extensions. | **About 35–50:** allows eligible dissonances with a moderate penalty. | **100:** removes that exposure penalty. | Higher values do not guarantee more ornaments or relax preparation/resolution, range, or locks. |
| Melody Low MIDI, 0…127 | **48:** permits a lower melodic floor. | **60:** floor at middle C. | **72:** raises the floor by an octave. | A hard lower bound before track/key transposition; must not exceed Melody High. Raising it also shifts the preferred register. |
| Melody High MIDI, 0…127 | **72:** restricts upper reach. | **84:** permits another octave above that. | **96:** permits a still higher register. | A hard upper bound. A wider range permits options; it does not require large leaps. |
| Phrase Bars, 1…16 | **1:** frequent phrase resets and arrivals. | **2–4:** longer recurring units. | **8–16:** fewer, longer phrase units. | Phrase span = Phrase Bars × Beats per Bar. Independent of chord count; the final phrase may be shorter. |
| Beats per Bar, 1…12 | **2–3:** shorter grouping for the same Phrase Bars. | **4:** familiar four-beat grouping. | **6–12:** longer grouping. | Changes phrase span and metric scoring; it does not rewrite the MIDI time signature or implement every compound-meter accent pattern. |
| Max Block Change, 0…7 | **0:** small leap allowance. | **3–4:** more interval freedom. | **7:** permits wider jumps. | Actual bound in semitones is `min(24, 2 + Max Block Change + Block Jump)`. The normal GUI permits a combined maximum of 13. |
| Block Jump, 0…4 | **0:** no extra leap allowance. | **2:** adds two semitones. | **4:** adds four semitones. | Adds to the bound above; it does not set an independent jump probability. Defaults of 7 and 1 give a 10-semitone bound. |
| Max. Dir. Changes, 1…6 | **1:** penalizes frequent phrase reversals sooner. | **2–3:** tolerates more contour turns. | **6:** tolerates many turns before extra penalty. | A soft contour-complexity threshold, not a strict maximum number of reversals. |
| Pause chance, 0…100 | **0:** little generated silence. | **About 50:** more gaps between attacks. | **100:** most eligible interior slots rest. | Generated rhythm preserves phrase endpoints; QUESTION adds eight percentage points to interior rest probability. Fill masks also create rests. |
| Start%, 0…100 | **0:** eligible nonfinal arrival attacks become rests. | **50:** some arrival attacks are omitted. | **100:** preserves those attacks. | Generated rhythm only; this is not simply a probability for the first note of each chord. |
| Split Long%, 0…100 | **0:** no optional subdivision. | **50:** some generated notes at least one beat long split in half. | **100:** splits all eligible generated slots. | Does not subdivide supplied custom rhythms. Harmony-boundary fallback can still split generated rhythm independently. |
| Lead To Chords%, 0…100 | **0:** disables chance-based anticipation proposals. | **50:** makes more eligible boundary approaches available. | **100:** all eligible slots may propose anticipation. | Only eligible weak notes of at most half a beat ending at a chord boundary qualify; the next note must repeat the anticipated pitch. Not a percentage of all notes. |
| New Blocks%, 0…100 | **0:** full catalogue preference. | **50:** half-strength catalogue preference. | **100:** no catalogue preference. | Other musical scoring and recurring gestures remain. High values do not guarantee newly invented motifs. |
| Active block-type weights, 0…100 | **0:** removes that family's affinity bonus. | **50:** reduced bonus. | **100:** full bonus. | Active families are SCALEY, NEIGHBORY, ARPY, CHORDY, WAVY. Weights are preferences, not percentages of output notes. See the inactive INTERVAL/NOTE caveat below. |
| Velocity Min / Max, 0…127 | Lower bounds produce softer notes. | A moderate interval gives some dynamic variation. | Higher bounds produce louder notes. | Dynamics stay within the effective interval after arrangement volume scaling. Min/Max also control how much accent headroom exists. |
| Accent, 0…100 | **0:** no optional arrival velocity boost. | **50:** some phrase arrivals receive a boost. | **100:** every eligible arrival may receive a boost. | Boost is capped by Velocity Max; a zero-width velocity interval leaves no headroom. Does not add new pitches. |
| Length, 25…200% | **50:** shorter, detached articulation. | **100:** keeps the generated performance duration. | **150–200:** longer articulation and possible overlap. | Does not change rhythm values. Harmony validation can shorten unsafe tails; a suspension is capped at its rhythmic duration. |
| Swing%, 0…100 | **Below 50:** shifts eligible subdivisions toward reverse swing. | **50:** neutral swing. | **Above 50:** increases forward swing. | A performance effect, not a new rhythm template. Extreme timing can conflict with harmonic placement. |
| Melody track transpose | **−12:** one octave lower. | **0:** unchanged. | **+12:** one octave higher. | Applied outside the planner's range/lock coordinates. Other intervals change pitch classes relative to the accompaniment. |
| Voice Low MIDI, 0…127 | **36:** permits lower chord voices. | **48:** a higher bass floor. | **60:** restricts voicings to a higher floor. | Smooth voices only. Must be at or below Voice High, with enough room for all required tones and bass constraints. |
| Voice High MIDI, 0…127 | **72:** restricts the top register. | **84:** more upper-register room. | **96:** still more room. | Raises the ceiling; it does not force the top voice upward. Narrow/impossible ranges report a conflict. |
| Spice, 0…100 | **0:** no automatic added color. | **About 35–50:** color is attempted on some eligible chords. | **100:** color is attempted on every eligible unlocked chord. | If no extension satisfies the filters, the chord stays unchanged. Explicit endpoints/full or quality locks can prevent coloring. |
| 8 Chords Similarity%, 0…100 | **0:** no recurrence reward. | **50:** moderate reward for the same planned chord name four events earlier. | **100:** stronger recurrence reward. | Applies from the fifth event onward, including longer progressions. A preference during planning, not forced copying afterward. |
| Seed, compose / part | Any low numeric value selects one deterministic result. | Another value selects another result. | A larger value selects another result. | Seed magnitude has no low-to-high musical meaning. Keep seed and settings fixed to reproduce a result; part order and section context also matter. |

## 3. How older controls are used now

| Older control or action | Contextual meaning / current limitation |
| --- | --- |
| Speed | Controls generated rhythmic units with the thresholds above, rather than merely steering the old block-expansion process. Supplied rhythm takes precedence. |
| Max Block Change / Block Jump | Together define a realized semitone leap bound. They no longer mean abstract changes between block endpoints. |
| Note Targets | Soft scale-degree preferences at phrase arrivals, indexed by the arrival's harmony event. In the normalized key, `0=C4`, `2=E4` in major / `Eb4` in minor, `7=C5`. Harmony/range can override a preference; use Anchors for a hard pitch lock. |
| Pattern list / offsets, e.g. `1,2,1,3` | Repeated IDs encourage recurring phrase rhythms and interval gestures. IDs are identities, not density amounts. Negative IDs share rhythm with their positive counterpart and favor inverted catalogue shapes. |
| SCALEY / NEIGHBORY weights | Favor catalogue scale-motion / neighbor-return shapes. Actual note roles still require appropriate harmonic support and resolution. |
| ARPY / CHORDY weights | Favor shapes realized from actual chord members, including inversion/starting-note context, instead of assuming generic scale thirds always belong to the chord. |
| WAVY weight | Favors the catalogue's turning/up-and-down gestures, subject to leap and role checks. |
| INTERVAL / NOTE weights | Currently have **no affinity effect** in the contextual search: it scores three-note gestures, while these catalogue entries contain only two / one notes. Interval motion and repeated notes can still arise from the general planner. |
| New Blocks% | Reduces catalogue influence as it rises. It does not operate the old random block-invention routine. |
| Lead To Chords% | Controls eligible anticipation proposals; it no longer shifts arbitrary notes into the next chord indiscriminately. |
| Tonic / Chord / Mode note targets, key emphasis, Replace Near Chord Notes | Old quota/repair passes are bypassed and their controls hidden. Stability and dissonance roles are evaluated during search instead. |
| Max Exc. / Note Exc.% / Fill Pauses / Doubled Rhythm% / Flex | Unused by contextual generation and hidden. Tension, phrase planning, explicit rhythm, and constraints supply the new behavior. |
| Alt. Pattern | Not consumed by the contextual rhythm planner and hidden. Its stored value can still be used by GUI actions that generate a new Pattern list; the list itself remains meaningful. |
| Old Note Target Mode selector / Target Note Variation / Directions from Progression | Not consumed by the contextual pitch planner and hidden. Existing GUI target-randomization actions can still consult stored legacy values when replacing the Note Targets list. The visible ANY/ASC/DESC choice is used directly by the contextual planner. |
| Drum Rhythm Accents post-process | The old pitch/rest splitter is bypassed and its group hidden. The per-melody Accent control remains active as a velocity effect. |
| Humanize Notes | Final unchecked timing jitter is bypassed for contextual melody tracks, preventing changes after role validation. The global setting still affects accompaniment. |
| Custom-duration strict-mode checkbox | Hidden for contextual melody. Supplied nonempty rhythms keep their authored timing; invalid supplied input is reported. Empty input uses ordinary generation. Random pattern weighting is still supported. |
| Chord Formula / Aeolian parallel-spice chance | Compatibility progression controls; not used by FUNCTIONAL, FIFTHS, POP_LOOP, or MODAL planning. Use typed Alternatives for contextual quality/function changes. |
| Flatten Voicing% / Spicy Voicing / Flatten Progressively | Older voicing preprocessing still exists. Smooth voices can replace its register/inversion choices afterward; these controls do not select the new functional phrase or fifth-cycle structure. |
| Respice / S | Adds eligible color while preserving root, core quality, inversion/bass, and locks. Functional substitutions belong to Alternatives. |
| Chordlet locks / local reroll | Ctrl+click locks the whole event; Locks can preserve root, exact quality, bass, or duration separately. Alt+click rerolls an unlocked event using both joins. Harmonization/co-design conservatively freeze pitch-property-locked events completely. |
| Anchors / Reroll span | Anchors accepts hard `beat=MIDI` locks, e.g. `0=72,4=76`. Reroll span accepts `startBeat,endBeat` at existing note boundaries and affects the next regeneration. It requires a previous in-memory plan for the same harmony revision; unrelated notes/rhythm are preserved. |

Attached **fixed user MIDI** follows a preservation path: generation knobs do not rewrite its pitches/rhythm, and swing/length/automatic transition repair are bypassed. Explicit track/key transpose and delay effects still apply. Explicit custom harmony remains fixed; otherwise contextual generation can harmonize around the authored notes. The editor's **Harmonize** action changes harmony without rewriting the melody.

Implementation references: [melody adapter](src/main/java/org/vibehistorian/vibecomposer/generation/ContextualMelodyGenerator.java), [phrase planner](src/main/java/org/vibehistorian/vibecomposer/generation/MelodyPhrasePlan.java), [pitch planner](src/main/java/org/vibehistorian/vibecomposer/generation/ContextualMelodyPlanner.java), [harmony planner](src/main/java/org/vibehistorian/vibecomposer/generation/HarmonicProgressionPlanner.java), [chordlet alternatives](src/main/java/org/vibehistorian/vibecomposer/generation/HarmonyOperations.java).
