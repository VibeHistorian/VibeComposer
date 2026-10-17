package org.vibehistorian.vibecomposer.generation;

import org.junit.Test;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Section;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class ContextualMelodySpeedTest {
    private static final long BEAT = HarmonyTimeline.TICKS_PER_BEAT;

    private HarmonyTimeline harmony(boolean functional, String... names) {
        return HarmonyTimeline.fromSymbols(Arrays.asList(names),Collections.nCopies(names.length,4.0),
                new HarmonyTimeline.Context("C",0,MidiUtils.ScaleMode.IONIAN,functional));
    }

    private MelodyPhrasePlan plan(HarmonyTimeline harmony, ContextualMelodyPlanner.Ending ending) {
        return MelodyPhrasePlan.create(harmony,MelodyPhrasePlan.Form.STATEMENT_VARIATION,4,2,
                Arrays.asList(1,2,1,3),ending);
    }

    @Test public void speedGraduallyIncreasesDensityAndFastNotesAcrossFormerPlateaus() {
        MelodyPhrasePlan plan = plan(harmony(true,"C","F","G7","C"),ContextualMelodyPlanner.Ending.OPEN);
        int[] speeds = {-100,0,25,34,35,50,60,74,75,85,100};
        double previousDensity = 0, previousFast = 0;
        double density74 = 0, fast74 = 0;
        for (int speed : speeds) {
            long count = 0, fast = 0;
            for (int seed = 0; seed < 1024; seed++) {
                List<ContextualMelodyPlanner.Slot> slots = plan.rhythm(speed,0,seed * 0x9e3779b97f4a7c15L,0);
                count += slots.size();
                fast += slots.stream().filter(slot -> slot.duration == BEAT / 4).count();
            }
            double density = count / (1024.0 * 16), share = fast / (double) count;
            System.out.printf("Speed %4d: %.3f attacks/beat, %.4f sixteenth share%n",speed,density,share);
            assertTrue("Density did not increase at " + speed,density > previousDensity);
            assertTrue("Sixteenths did not increase at " + speed,share > previousFast);
            if (speed == 74) { density74 = density; fast74 = share; }
            if (speed == 75) {
                assertTrue("Old 75% density cliff returned",density - density74 < 0.05);
                assertTrue("Old 75% sixteenth cliff returned",share - fast74 < 0.02);
            }
            previousDensity = density; previousFast = share;
        }
    }

    private double fastShare(HarmonyTimeline harmony, long cursor, boolean arrival, boolean continuation) {
        Random random = new Random(91057);
        int fast = 0;
        for (int i = 0; i < 20000; i++) {
            if (ContextualRhythmDuration.choose(harmony,0,cursor,harmony.length(),arrival,continuation,random)
                    <= BEAT / 2) { fast++; }
        }
        return fast / 20000.0;
    }

    @Test public void slowRhythmsUseMetricPhraseAndFunctionalContext() {
        HarmonyTimeline held = harmony(true,"C","C");
        double plain = fastShare(held,BEAT,false,false);
        assertTrue("Slow ornament vocabulary disappeared",plain > 0);
        assertTrue(fastShare(held,BEAT,false,true) > plain);
        assertTrue(fastShare(held,BEAT / 2,false,false) > plain);
        assertTrue(fastShare(held,BEAT,true,false) < plain);
        double change = fastShare(harmony(true,"C","F"),3 * BEAT,false,false);
        assertTrue("Chord approaches should encourage short notes",change > fastShare(held,3 * BEAT,false,false));
        assertTrue("Resolving dominant should add forward motion",
                fastShare(harmony(true,"G7","C"),3 * BEAT,false,false)
                        > fastShare(harmony(false,"G7","C"),3 * BEAT,false,false));
    }

    private String signature(List<ContextualMelodyPlanner.Slot> slots) {
        StringBuilder result = new StringBuilder();
        for (ContextualMelodyPlanner.Slot slot : slots) {
            result.append(slot.start).append('/').append(slot.duration).append('/').append(slot.rest)
                    .append('/').append(slot.intent).append(';');
        }
        return result.toString();
    }

    @Test public void rhythmIsRepeatableCoversOddBoundariesAndPreservesClosingArrival() {
        HarmonyTimeline harmony = HarmonyTimeline.fromSymbols(Arrays.asList("C","G7","C"),
                Arrays.asList(2.3,3.2,4.0),new HarmonyTimeline.Context("C",0,MidiUtils.ScaleMode.IONIAN,true));
        MelodyPhrasePlan plan = plan(harmony,ContextualMelodyPlanner.Ending.CLOSED);
        for (int speed : new int[] {-100,0,34,35,50,74,75,100}) {
            for (int variant = 0; variant < 2; variant++) {
                List<ContextualMelodyPlanner.Slot> slots = plan.rhythm(speed,100,42,variant);
                assertEquals(signature(slots),signature(plan.rhythm(speed,100,42,variant)));
                long cursor = 0;
                for (ContextualMelodyPlanner.Slot slot : slots) {
                    assertEquals(cursor,slot.start);
                    assertTrue(slot.duration > 0);
                    cursor += slot.duration;
                }
                assertEquals(harmony.length(),cursor);
                for (long arrival : plan.arrivals) {
                    assertTrue("Skipped phrase arrival",slots.stream().anyMatch(slot -> slot.start == arrival));
                }
                ContextualMelodyPlanner.Slot last = slots.get(slots.size() - 1);
                assertFalse(last.rest);
                assertEquals(BEAT,last.duration);
            }
        }
    }

    @Test public void publicPlannerRhythmAlsoRespondsWithinFormerSpeedBands() {
        HarmonyTimeline harmony = harmony(true,"C","G7","C");
        long slower = 0, faster = 0;
        for (int seed = 0; seed < 256; seed++) {
            slower += ContextualMelodyPlanner.rhythm(harmony,50,0,Arrays.asList(1,2),seed,
                    ContextualMelodyPlanner.Ending.CLOSED).size();
            faster += ContextualMelodyPlanner.rhythm(harmony,74,0,Arrays.asList(1,2),seed,
                    ContextualMelodyPlanner.Ending.CLOSED).size();
        }
        assertTrue(faster > slower);
    }

    @Test public void completeGeneratorRetainsDensityTrendAndPitchBounds() {
        GUIConfig config = new GUIConfig();
        config.setContextualMelody(true); config.setMelodyUseCustomDurations(false);
        config.setMelodyEnding(ContextualMelodyPlanner.Ending.CLOSED);
        config.setMelodyRangeMin(60); config.setMelodyRangeMax(84);
        MelodyPart part = new MelodyPart();
        part.setOrderOffset(1); part.setStartNoteChance(100); part.setPauseChance(0); part.setSplitChance(0);
        Section section = new Section(); section.setMeasures(1);
        List<int[]> chords = Arrays.asList(MidiUtils.mappedChord("C"),MidiUtils.mappedChord("F"),
                MidiUtils.mappedChord("G7"),MidiUtils.mappedChord("C"));
        long previousCount = 0;
        for (int speed : new int[] {-100,0,25,50,60,74,85,100}) {
            part.setSpeed(speed);
            long count = 0;
            for (int seed = 0; seed < 16; seed++) {
                part.setPatternSeed(seed);
                ContextualMelodyGenerator generator = new ContextualMelodyGenerator(config,new MidiTiming(100),0.95);
                generator.generate(part,chords,chords,Collections.nCopies(4,4.0),section,0,MidiUtils.ScaleMode.IONIAN,true);
                long cursor = 0;
                for (ContextualMelodyPlanner.PlannedNote note : generator.lastResult.notes) {
                    assertEquals(cursor,note.slot.start);
                    assertTrue(note.pitch >= 60 && note.pitch <= 84);
                    cursor += note.slot.duration;
                }
                assertEquals(16 * BEAT,cursor);
                count += generator.lastResult.notes.size();
            }
            assertTrue("Pitch planning lost the speed response at " + speed,count > previousCount);
            previousCount = count;
        }
    }
}
