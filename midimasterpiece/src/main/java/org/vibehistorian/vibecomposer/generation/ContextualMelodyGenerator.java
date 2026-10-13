package org.vibehistorian.vibecomposer.generation;

import jm.constants.Pitches;
import jm.music.data.Note;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.Helpers.PhraseNote;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Section;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Adapts app settings and custom rhythms to the pure planner, without quota/avoid-note mutations. */
final class ContextualMelodyGenerator {
    private final GUIConfig config;
    private final MidiTiming timing;
    private final double articulation;
    ContextualMelodyPlanner.Result lastResult;
    HarmonyTimeline lastHarmony;

    ContextualMelodyGenerator(GUIConfig config, MidiTiming timing, double articulation) {
        this.config = config; this.timing = timing; this.articulation = articulation;
    }

    Map<Integer, List<Note>> generate(MelodyPart part, List<int[]> chords, List<int[]> roots,
            List<Double> durations, Section section, int offset, MidiUtils.ScaleMode mode) {
        List<int[]> realizedChords = new ArrayList<>(), realizedRoots = new ArrayList<>();
        List<Double> beats = new ArrayList<>();
        for (int repeat = 0; repeat < section.getMeasures(); repeat++) {
            for (int i = 0; i < chords.size(); i++) {
                // This is the same scale realization later used by chord/bass rendering.
                realizedChords.add(MidiUtils.transposeChord(chords.get(i), MidiUtils.ScaleMode.IONIAN.noteAdjustScale,
                        mode.noteAdjustScale, !config.isTransposedNotesForceScale()));
                realizedRoots.add(MidiUtils.transposeChord(Arrays.copyOf(roots.get(i), 1),
                        MidiUtils.ScaleMode.IONIAN.noteAdjustScale, mode.noteAdjustScale, true));
                beats.add(durations.get(i) / timing.quarterNote);
            }
        }
        HarmonyTimeline harmony = HarmonyTimeline.of(realizedChords, realizedRoots, beats);
        long seed = (long) part.getPatternSeedWithPartOffset() + offset;
        List<Integer> motifs = part.getMelodyPatternOffsets() == null || part.getMelodyPatternOffsets().isEmpty()
                ? Arrays.asList(1, 2, 1, 3) : part.getMelodyPatternOffsets();
        ContextualMelodyPlanner.Ending ending = config.getMelodyEnding();
        List<ContextualMelodyPlanner.Slot> slots;
        if (config.isMelodyUseCustomDurations() && part.getCustomDurationNotes() != null) {
            slots = customRhythm(part, durations, section, harmony, seed);
        } else {
            slots = ContextualMelodyPlanner.rhythm(harmony, part.getSpeed(), part.getPauseChance(), motifs,
                    seed ^ 0x72687974686dL, ending);
        }
        List<Integer> fill = part.getChordSpanFill().getPatternByLength(harmony.events().size(), part.isFillFlip());
        List<ContextualMelodyPlanner.Slot> filled = new ArrayList<>();
        for (ContextualMelodyPlanner.Slot slot : slots) {
            boolean rest = slot.rest || fill.get(harmony.at(slot.start).index) == 0;
            filled.add(new ContextualMelodyPlanner.Slot(slot.start, slot.duration, rest, slot.lockedPitch, slot.motif));
        }
        ContextualMelodyPlanner.Options options = new ContextualMelodyPlanner.Options(config.getMelodyRangeMin(),
                config.getMelodyRangeMax(), Math.min(12, 2 + part.getMaxBlockChange()), 32,
                config.getMelodyTension(), ending);
        ContextualMelodyPlanner.Result result = ContextualMelodyPlanner.generate(harmony,
                Arrays.stream(mode.noteAdjustScale).mapToInt(Integer::intValue).toArray(), filled, options, seed);
        Map<Integer, List<Note>> map = new LinkedHashMap<>();
        for (HarmonyTimeline.Event event : harmony.events()) { map.put(event.index, new ArrayList<>()); }
        int volume = config.isScaleMidiVelocityInArrangement() ? section.getVol(0) : 100;
        int low = Math.max(1, Math.min(127, part.getVelocityMin() * volume / 100));
        int high = Math.max(low, Math.min(127, part.getVelocityMax() * volume / 100));
        for (ContextualMelodyPlanner.PlannedNote planned : result.notes) {
            Random performance = new Random(seed ^ (planned.slot.start * 0xd1b54a32d192ed03L));
            double rhythm = planned.slot.duration / (double) HarmonyTimeline.TICKS_PER_BEAT * timing.quarterNote;
            int velocity = low + performance.nextInt(high - low + 1);
            Note note = new Note(planned.pitch < 0 ? Pitches.REST : planned.pitch + part.getTranspose(), rhythm, velocity);
            note.setDuration(rhythm * Math.min(1, articulation * (0.9 + performance.nextDouble() * 0.1)));
            map.get(harmony.at(planned.slot.start).index).add(note);
        }
        lastResult = result;
        lastHarmony = harmony;
        return map;
    }

    private List<ContextualMelodyPlanner.Slot> customRhythm(MelodyPart part, List<Double> durations,
            Section section, HarmonyTimeline harmony, long seed) {
        Map<Integer, List<PhraseNote>> patterns = new java.util.TreeMap<>();
        int patternId = part.getCustomDurationNotes().stream().filter(note -> note.getPitch() >= 0)
                .mapToInt(PhraseNote::getPitch).findFirst().orElse(0);
        for (PhraseNote original : part.getCustomDurationNotes()) {
            if (original.getPitch() >= 0) { patternId = original.getPitch(); }
            patterns.computeIfAbsent(patternId, ignored -> new ArrayList<>()).add(original.clone());
        }
        if (patterns.isEmpty()) { throw new IllegalArgumentException("Custom melody rhythm is empty"); }
        List<Integer> ids = new ArrayList<>(patterns.keySet());
        double multiplier = MidiGenerator.getBeatDurationMult(config,section) / timing.quarterNote;
        List<ContextualMelodyPlanner.Slot> result = new ArrayList<>();
        for (HarmonyTimeline.Event event : harmony.events()) {
            int index = event.index % durations.size();
            int selected = index % ids.size();
            if (config.isMelodyCustomDurationsRandomWeighting() && ids.size() > 1) {
                if (part.getCustomDurationChances() == null) { throw new IllegalArgumentException("Custom rhythm weights are missing"); }
                Integer[] weights = new Integer[ids.size()];
                for (int i = 0; i < ids.size(); i++) {
                    if (ids.get(i) < 0 || ids.get(i) >= part.getCustomDurationChances().size()) {
                        throw new IllegalArgumentException("Custom rhythm weight missing for pattern " + ids.get(i));
                    }
                    weights[i] = part.getCustomDurationChances().get(ids.get(i));
                }
                int[] cumulative = MelodyUtils.normalizedCumulativeWeights(weights);
                int roll = new Random(seed + 12 + index).nextInt(100);
                selected = 0;
                while (selected + 1 < cumulative.length && roll >= cumulative[selected]) { selected++; }
            }
            int id = ids.get(selected);
            org.vibehistorian.vibecomposer.Helpers.PhraseNotes pattern =
                    org.vibehistorian.vibecomposer.Helpers.PhraseNotes.fromPN(patterns.get(id));
            pattern.remakeNoteStartTimes(true);
            List<PhraseNote> ordered = pattern.getIterationOrder();
            long cursor = event.start;
            for (int i = 0; i < ordered.size(); i++) {
                PhraseNote note = ordered.get(i);
                long start = event.start + HarmonyTimeline.ticks(note.getStartTime() * multiplier);
                if (start >= event.end) { break; }
                long end = Math.min(event.end, start + HarmonyTimeline.ticks(note.getDuration() * multiplier));
                if (i + 1 < ordered.size()) {
                    end = Math.min(end,event.start + HarmonyTimeline.ticks(ordered.get(i + 1).getStartTime() * multiplier));
                }
                if (start < cursor) { throw new IllegalArgumentException("Overlapping custom rhythm at chord " + index); }
                if (start > cursor) { result.add(new ContextualMelodyPlanner.Slot(cursor,start - cursor,true,null,id)); }
                if (end > start) {
                    result.add(new ContextualMelodyPlanner.Slot(start,end - start,note.getDynamic() <= 0 || note.getPitch() < 0,null,id));
                }
                cursor = Math.max(start,end);
            }
            if (cursor < event.end) { result.add(new ContextualMelodyPlanner.Slot(cursor,event.end - cursor,true,null,id)); }
        }
        return result;
    }
}
