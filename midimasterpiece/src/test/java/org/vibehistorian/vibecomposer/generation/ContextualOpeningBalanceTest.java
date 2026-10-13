package org.vibehistorian.vibecomposer.generation;

import org.junit.Test;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Section;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Controlled opening statistics, independent of changing progression-generation seeds. */
public class ContextualOpeningBalanceTest {
    @Test public void noteAndIntervalFamiliesGuideTwoNoteOpenings() {
        HarmonyTimeline.Event chord = HarmonyTimeline.fromSymbols(Collections.singletonList("C"),Collections.singletonList(4.0),
                new HarmonyTimeline.Context("C",0,MidiUtils.ScaleMode.IONIAN,true)).at(0);
        int[] scale = {0,2,4,5,7,9,11};
        List<Integer> weights = new ArrayList<>(Collections.nCopies(7,0));
        ContextualBlockCatalogue.Evaluator evaluator = new ContextualBlockCatalogue.Evaluator(1,0);
        assertEquals(0,evaluator.affinity(new int[] {72,72},chord,scale,weights,1,0,-1),0);
        weights.set(org.vibehistorian.vibecomposer.Enums.BlockType.NOTE.ordinal(),100);
        assertEquals(3,evaluator.affinity(new int[] {72,72},chord,scale,weights,1,0,-1),0);
        assertTrue(evaluator.affinity(new int[] {72,72},chord,scale,weights,1,0,-1)
                > evaluator.affinity(new int[] {72,71},chord,scale,weights,1,0,-1));
        weights.set(org.vibehistorian.vibecomposer.Enums.BlockType.NOTE.ordinal(),0);
        weights.set(org.vibehistorian.vibecomposer.Enums.BlockType.INTERVAL.ordinal(),100);
        int falling = 0, rising = 0;
        for (int seed = 0; seed < 32; seed++) {
            evaluator = new ContextualBlockCatalogue.Evaluator(seed,0);
            double down = evaluator.affinity(new int[] {72,71},chord,scale,weights,1,0,-1);
            double up = evaluator.affinity(new int[] {72,74},chord,scale,weights,1,0,-1);
            if (down > up) { falling++; }
            if (evaluator.affinity(new int[] {72,74},chord,scale,weights,1,0,1)
                    > evaluator.affinity(new int[] {72,71},chord,scale,weights,1,0,1)) { rising++; }
        }
        assertTrue("INTERVAL falling guides are inactive",falling >= 16);
        assertTrue("INTERVAL rising guides are inactive",rising >= 16);
    }

    @Test public void measureOpeningDirectionsWithCatalogueAndContourSettings() throws Exception {
        List<String> rows = new ArrayList<>(); rows.add("targets,direction,new_blocks,down,repeat,up,rising_three,unique_openings");
        String algorithm = "";
        for (boolean anchors : new boolean[] {false,true}) {
        for (MelodyUtils.NoteTargetDirection direction : new MelodyUtils.NoteTargetDirection[] {MelodyUtils.NoteTargetDirection.ANY,MelodyUtils.NoteTargetDirection.DESC}) {
            Map<Integer,List<String>> melodies = new HashMap<>();
            for (int chance : new int[] {0,25,100}) {
                GUIConfig config = new GUIConfig(); config.setContextualMelody(true); config.setMelodyRangeMin(48); config.setMelodyRangeMax(84);
                config.setNoteTargetDirectionChoice(direction); config.setMelodyNewBlocksChance(chance);
                config.setMelodyUseCustomDurations(false);
                MelodyPart part = new MelodyPart(); part.setOrderOffset(1); part.setSpeed(50); part.setStartNoteChance(100); part.setPauseChance(0);
                if (anchors) { part.setChordNoteChoices(Arrays.asList(0,2,2,4)); }
                Section section = new Section(); section.setMeasures(1);
                List<int[]> chords = new ArrayList<>(); for (String name : Arrays.asList("C","F","G7","C")) { chords.add(MidiUtils.mappedChord(name)); }
                int down = 0, repeat = 0, up = 0, rising = 0; Set<String> openings = new HashSet<>();
                List<String> signatures = new ArrayList<>();
                for (int seed = 0; seed < 64; seed++) {
                    part.setPatternSeed(seed);
                    ContextualMelodyGenerator generator = new ContextualMelodyGenerator(config,new MidiTiming(100),0.95);
                    generator.generate(part,chords,chords,Arrays.asList(4.0,4.0,4.0,4.0),section,0,MidiUtils.ScaleMode.IONIAN,true);
                    List<ContextualMelodyPlanner.PlannedNote> notes = generator.lastResult.notes; algorithm = generator.lastResult.algorithm;
                    signatures.add(notes.stream().map(note -> note.pitch + "@" + note.slot.start).collect(java.util.stream.Collectors.joining(",")));
                    for (int i = 0; i < notes.size(); i++) {
                        assertTrue(notes.get(i).pitch >= 48 && notes.get(i).pitch <= 84);
                        int prior = i == 0 ? notes.get(notes.size() - 1).pitch : notes.get(i - 1).pitch;
                        assertTrue("Leap or loop bound exceeded",Math.abs(notes.get(i).pitch - prior) <= 9);
                    }
                    int first = notes.get(0).pitch, second = notes.get(1).pitch, third = notes.get(2).pitch;
                    if (second < first) { down++; } else if (second == first) { repeat++; } else { up++; }
                    if (first < second && second < third) { rising++; }
                    openings.add(first + "/" + second + "/" + third);
                }
                assertEquals(64,down + repeat + up);
                assertTrue("Openings lost variety",openings.size() >= 16);
                if (direction == MelodyUtils.NoteTargetDirection.ANY) {
                    assertTrue("ANY lost descending openings",down >= 16);
                    assertTrue("ANY lost ascending openings",up >= 16);
                    assertTrue("Three-note rise dominates ANY",rising < 24);
                } else { assertTrue("DESC rarely descends",down >= 40); assertTrue("DESC favors rising openings",up <= 8); }
                melodies.put(chance,signatures);
                String row = (anchors ? "0/2/2/4" : "none") + "," + direction + "," + chance + "," + down + "," + repeat + "," + up + "," + rising + "," + openings.size();
                rows.add(row); System.out.println("Opening profile: " + row);
            }
            for (int[] pair : new int[][] {{0,25},{25,100}}) {
                int changed = 0;
                for (int seed = 0; seed < 64; seed++) { if (!melodies.get(pair[0]).get(seed).equals(melodies.get(pair[1]).get(seed))) { changed++; } }
                System.out.println("New Blocks " + pair[0] + " -> " + pair[1] + ": " + changed + "/64 melodies changed (targets=" + anchors + ", " + direction + ")");
                assertTrue("New Blocks has little effect on whole melodies",changed >= 16);
            }
        }
        }
        Files.write(Paths.get("target","opening-profile-" + algorithm + ".csv"),rows,StandardCharsets.UTF_8);
    }
}
