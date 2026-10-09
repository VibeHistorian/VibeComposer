package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import java.util.*;

/** Complete supported phrases through production block skeleton, expansion and phrase builder. */
public class MelodyCoreFixture {
    public static void main(String[] args) {
        org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.OFF);
        org.apache.logging.log4j.core.config.Configurator.setLevel("org.vibehistorian.vibecomposer.LG", org.apache.logging.log4j.Level.OFF);
        StringBuilder json = new StringBuilder("[\n");
        String[] seeds = { "42", "-2147483648", "9223372036854775807", "9007199254740993" };
        String[] keys = { "C", "D", "A\\u266d", "F\\u266f" };
        int[] transpositions = { 0, 2, 8, 6 };
        int[] speeds = { -100, 0, 50, 100 };
        boolean first = true;
        for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
            for (int sample = 0; sample < 20; sample++) {
                int chordCount = new int[] { 1, 3, 4, 7, 8 }[sample % 5];
                int[] structure = sample % 3 == 0 ? new int[] { 1, 2, 1, 3 }
                        : sample % 3 == 1 ? new int[] { 1, -1, 2, -2 } : new int[] { 0, 0, 3 };
                int[] targets = sample % 2 == 0 ? new int[] { 0, 2, 2, 4 } : new int[] { -3, 1, 5 };
                int notesSeedOffset = sample % 4 == 0 ? 100 : 0;
                int explicitSeed = sample == 19 ? 123456789 : 0;
                MelodyPart part = new MelodyPart();
                part.setOrder(1); part.setOrderOffset(1);
                part.setPatternSeed(explicitSeed == 0 ? (int) Long.parseLong(seeds[seedIndex]) : explicitSeed);
                part.setMelodyPatternOffsets(list(structure)); part.setChordNoteChoices(list(targets));
                part.setSpeed(speeds[sample % 4]); part.setMaxBlockChange(sample % 8);
                part.setBlockJump(sample % 5); part.setPatternFlexible(sample % 2 == 0);
                part.setDoubledRhythmChance(0); part.setMaxNoteExceptions(0); part.setNoteExceptionChance(0);
                part.setSplitChance(0); part.setLeadChordsChance(0); part.setStartNoteChance(100);
                part.setFillPauses(sample % 3 == 1); part.setPauseChance((sample % 5) * 20);
                part.setAccents(sample % 3 == 0 ? 0 : 100);
                part.setVelocityMin(80); part.setVelocityMax(105);
                part.setTranspose(new int[] { 0, 5, 7, -12 }[sample % 4]);
                part.setNoteLengthMultiplier(sample % 2 == 0 ? 150 : 75);
                part.setSwingPercent(sample % 4 == 0 ? 66 : 50);
                part.setChordSpanFill(sample % 5 == 0 ? ChordSpanFill.ODD : ChordSpanFill.ALL);
                part.setFillFlip(sample == 18);

                GUIConfig config = new GUIConfig();
                config.setMelodyParts(Collections.singletonList(part));
                config.setMelodyLegacyMode(false); config.setMelodyNewBlocksChance(100);
                config.setMelodyBlockTargetMode(2); config.setMelodyPatternEffect(2);
                config.setMelodyMaxDirChanges(2); config.setMelodyEmphasizeKey(false);
                config.setMelodyArpySurprises(false); config.setMelodyUseCustomDurations(false);
                config.setMelodyTonicNoteTarget(0); config.setMelodyChordNoteTarget(0); config.setMelodyModeNoteTarget(0);
                config.setMelodyReplaceAvoidNotes(0); config.setMelodyFillPausesPerChord(false);
                config.setMelodyBasicChordsOnly(false); config.setScaleMidiVelocityInArrangement(false);
                config.setScaleMode(sample % 2 == 0 ? MidiUtils.ScaleMode.IONIAN : MidiUtils.ScaleMode.AEOLIAN);
                Section section = new Section("VERSE1", 1, 100, 100, 100, 100, 100);
                MidiGenerator midi = new MidiGenerator(config);
                midi.setProgressionDurations(Collections.nCopies(chordCount, 4.0));
                MelodyGenerator melody = new MelodyGenerator(config, midi);
                // Root-only progression is the supported P1b context; chord-driven targeting is deferred.
                List<int[]> roots = new ArrayList<>();
                for (int chord = 0; chord < chordCount; chord++) roots.add(new int[] { 60 });
                MelodyPhraseBuilder builder = new MelodyPhraseBuilder(config, melody, midi.getTiming(),
                        request -> false, request -> {},
                        (phrase, swing) -> midi.swingPhrase(phrase, swing, 1), request -> {}, result -> {});
                Phrase phrase = builder.build(part, roots, roots, Collections.nCopies(chordCount, 4.0), notesSeedOffset,
                        section, Collections.emptyList(), false, Collections.emptyList(), 0, 0,
                        transpositions[seedIndex], null);
                if (!first) json.append(",\n"); first = false;
                json.append("{\"seed\":\"").append(seeds[seedIndex]).append("\",\"key\":\"").append(keys[seedIndex])
                        .append("\",\"scale\":\"").append(sample % 2 == 0 ? "major" : "natural-minor")
                        .append("\",\"chordCount\":").append(chordCount).append(",\"notesSeedOffset\":").append(notesSeedOffset)
                        .append(",\"settings\":{\"algorithm\":\"block\",\"speed\":").append(part.getSpeed())
                        .append(",\"chordNoteChoices\":").append(Arrays.toString(targets))
                        .append(",\"melodyPatternOffsets\":").append(Arrays.toString(structure))
                        .append(",\"maxBlockChange\":").append(part.getMaxBlockChange()).append(",\"blockJump\":").append(part.getBlockJump())
                        .append(",\"patternFlexible\":").append(part.isPatternFlexible()).append(",\"fillPauses\":").append(part.isFillPauses())
                        .append(",\"pauseChance\":").append(part.getPauseChance()).append(",\"accents\":").append(part.getAccents())
                        .append(",\"velocityMin\":80,\"velocityMax\":105,\"transpose\":").append(part.getTranspose())
                        .append(",\"noteLengthMultiplier\":").append(part.getNoteLengthMultiplier())
                        .append(",\"swingPercent\":").append(part.getSwingPercent())
                        .append(",\"chordSpanFill\":\"").append(part.getChordSpanFill().name())
                        .append("\",\"fillFlip\":").append(part.isFillFlip()).append(",\"patternSeed\":").append(explicitSeed)
                        .append("},\"notes\":[");
                double time = 0;
                boolean firstNote = true;
                for (Object entry : phrase.getNoteList()) {
                    Note note = (Note) entry;
                    if (note.getPitch() >= 0) {
                        if (!firstNote) json.append(','); firstNote = false;
                        json.append('[').append(note.getPitch()).append(',').append(time + note.getOffset())
                                .append(',').append(note.getDuration()).append(',').append(note.getDynamic()).append(']');
                    }
                    time += note.getRhythmValue();
                }
                json.append("]}");
            }
        }
        System.out.println(json.append("\n]"));
    }

    private static List<Integer> list(int[] values) {
        List<Integer> result = new ArrayList<>();
        for (int value : values) result.add(value);
        return result;
    }
}
