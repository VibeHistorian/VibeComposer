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
    private final Map<String,ContextualMelodyPlanner.Result> cache;
    private MelodyGenerationSettings settings;

    ContextualMelodyGenerator(GUIConfig config, MidiTiming timing, double articulation) {
        this(config,timing,articulation,new java.util.HashMap<>());
    }
    ContextualMelodyGenerator(GUIConfig config,MidiTiming timing,double articulation,Map<String,ContextualMelodyPlanner.Result> cache) {
        this.config = config; this.timing = timing; this.articulation = articulation;
        this.cache = cache;
    }

    ContextualMelodyGenerator(GUIConfig config,MidiTiming timing,double articulation,MelodyGenerationSettings settings) {
        this(config,timing,articulation,settings.contextualCache);
        this.settings = settings;
    }

    Map<Integer, List<Note>> generate(MelodyPart part, List<int[]> chords, List<int[]> roots,
            List<Double> durations, Section section, int offset, MidiUtils.ScaleMode mode) {
        return generate(part,chords,roots,durations,section,offset,mode,false);
    }
    Map<Integer,List<Note>> generate(MelodyPart part,List<int[]> chords,List<int[]> roots,List<Double> durations,
            Section section,int offset,MidiUtils.ScaleMode mode,boolean alreadyRealized) {
        return generate(part,chords,roots,durations,section,offset,mode,alreadyRealized,null);
    }
    Map<Integer,List<Note>> generate(MelodyPart part,List<int[]> chords,List<int[]> roots,List<Double> durations,
            Section section,int offset,MidiUtils.ScaleMode mode,boolean alreadyRealized,HarmonyTimeline finalized) {
        List<int[]> realizedChords = new ArrayList<>(), realizedRoots = new ArrayList<>();
        List<Double> beats = new ArrayList<>();
        for (int repeat = 0; repeat < section.getMeasures(); repeat++) {
            for (int i = 0; i < chords.size(); i++) {
                // This is the same scale realization later used by chord/bass rendering.
                realizedChords.add(alreadyRealized ? chords.get(i) : MidiUtils.transposeChord(chords.get(i), MidiUtils.ScaleMode.IONIAN.noteAdjustScale,
                        mode.noteAdjustScale, !config.isTransposedNotesForceScale()));
                realizedRoots.add(alreadyRealized ? Arrays.copyOf(roots.get(i),1) : MidiUtils.transposeChord(Arrays.copyOf(roots.get(i), 1),
                        MidiUtils.ScaleMode.IONIAN.noteAdjustScale, mode.noteAdjustScale, true));
                beats.add(durations.get(i) / timing.quarterNote);
            }
        }
        String owner = part.getOrderOffset() + "/section/" + config.getActualArrangement().getSections().indexOf(section) + "/" + offset;
        List<String> ids = new ArrayList<>();
        List<String> persistedIds = config.getChordEventIds();
        for (int i = 0; i < beats.size(); i++) { ids.add(owner + "/repeat/" + (i / chords.size()) + "/event/"
                + (persistedIds.size() == chords.size() && !section.isCustomChordsEnabled() ? persistedIds.get(i % chords.size()) : i % chords.size())); }
        HarmonyTimeline harmony = HarmonyTimeline.of(realizedChords, realizedRoots, beats).withIdentities(ids)
                .withContext(new HarmonyTimeline.Context("C",0,mode,config.getMelodyStyle() != ContextualMelodyPlanner.Style.MODAL));
        if (finalized != null) {
            if (finalized.events().size() != chords.size()) { throw new IllegalArgumentException("Canonical harmony differs from section events"); }
            harmony = finalized.repeat(section.getMeasures());
        }
        long seed = (long) part.getPatternSeedWithPartOffset() + offset;
        if (settings != null && settings.canRandomizeTargets(part.getOrder())
                && part.getContextualRerollWindow().trim().isEmpty()) {
            Map<Integer,List<Integer>> targets = settings.getOrCreateTargetNotes();
            if (!targets.containsKey(part.getOrder())) {
                targets.put(part.getOrder(),ContextualMelodyRandomizer.targets(harmony,mode,
                        config.getMelodyRangeMin(),config.getMelodyRangeMax(),config.getNoteTargetDirectionChoice().direction,
                        config.getMelodyEnding(),seed));
            }
            part.setChordNoteChoices(new ArrayList<>(targets.get(part.getOrder())));
        }
        List<Integer> motifs = part.getMelodyPatternOffsets() == null || part.getMelodyPatternOffsets().isEmpty()
                ? Arrays.asList(1, 2, 1, 3) : part.getMelodyPatternOffsets();
        ContextualMelodyPlanner.Ending ending = config.getMelodyEnding();
        MelodyPhrasePlan phrases = MelodyPhrasePlan.create(harmony,config.getMelodyForm(),config.getMelodyBeatsPerBar(),
                config.getMelodyPhraseBars(),motifs,ending);
        ContextualMelodyPlanner.Options options = new ContextualMelodyPlanner.Options(config.getMelodyRangeMin(),
                config.getMelodyRangeMax(),Math.min(24,2 + part.getMaxBlockChange() + part.getBlockJump()),32,config.getMelodyTension(),ending);
        options.phrases = phrases; options.style = config.getMelodyStyle(); options.chromaticApproaches = config.isMelodyChromaticApproaches();
        options.blockWeights = new ArrayList<>(config.getMelodyBlockTypePreference());
        options.newBlocksChance = config.getMelodyNewBlocksChance();
        options.anchorOctaveFlexible = true;
        options.maxDirectionChanges = config.getMelodyMaxDirChanges();
        options.anticipationChance = part.getLeadChordsChance();
        options.direction = config.getNoteTargetDirectionChoice().direction;
        Map<Long,Integer> preferences = new java.util.HashMap<>();
        if (part.getChordNoteChoices() != null && !part.getChordNoteChoices().isEmpty()) {
            for (long tick : phrases.arrivals) {
                int choice = part.getChordNoteChoices().get(harmony.at(tick).index % part.getChordNoteChoices().size());
                preferences.put(tick,60 + 12 * Math.floorDiv(choice,mode.noteAdjustScale.length)
                        + mode.noteAdjustScale[Math.floorMod(choice,mode.noteAdjustScale.length)]);
            }
        }
        options.anchorPreferences = preferences;
        boolean custom = config.isMelodyUseCustomDurations() && part.getCustomDurationNotes() != null
                && !part.getCustomDurationNotes().isEmpty();
        List<ContextualMelodyPlanner.Slot> slots;
        if (custom) {
            slots = customRhythm(part, durations, section, harmony, seed);
        } else {
            slots = phrases.rhythm(part.getSpeed(),part.getPauseChance(),seed ^ 0x72687974686dL,0);
        }
        List<Integer> fill = part.getChordSpanFill().getPatternByLength(harmony.events().size(), part.isFillFlip());
        ContextualMelodyPlanner.Result result = null; IllegalArgumentException failure = null;
        for (int attempt = 0; attempt < (custom ? 1 : 3); attempt++) {
            List<ContextualMelodyPlanner.Slot> candidate = attempt == 0 ? slots : attempt == 1
                    ? phrases.rhythm(part.getSpeed(),part.getPauseChance(),seed ^ 0x72687974686dL,1)
                    : MelodyPhrasePlan.splitAtChanges(slots,harmony);
            if (!custom && part.getSplitChance() > 0) {
                List<ContextualMelodyPlanner.Slot> split = new ArrayList<>();
                for (ContextualMelodyPlanner.Slot slot : candidate) {
                    Random splitRandom = new Random(seed ^ slot.start ^ 0x73706c6974L);
                    if (!slot.rest && slot.duration >= 960 && splitRandom.nextInt(100) < part.getSplitChance()) {
                        long half = slot.duration / 2;
                        split.add(new ContextualMelodyPlanner.Slot(slot.start,half,false,slot.lockedPitch,slot.motif,slot.intent));
                        split.add(new ContextualMelodyPlanner.Slot(slot.start + half,slot.duration - half,false,null,slot.motif,slot.intent));
                    } else { split.add(slot); }
                }
                candidate = split;
            }
            List<ContextualMelodyPlanner.Slot> filled = new ArrayList<>();
            for (ContextualMelodyPlanner.Slot slot : candidate) {
                boolean rest = slot.rest || fill.get(harmony.at(slot.start).index) == 0;
                if (!custom && phrases.arrivals.contains(slot.start) && slot.duration + slot.start < harmony.length()
                        && new Random(seed ^ slot.start ^ 0x7374617274L).nextInt(100) >= part.getStartNoteChance()) { rest = true; }
                filled.add(new ContextualMelodyPlanner.Slot(slot.start,slot.duration,rest,slot.lockedPitch,slot.motif,slot.intent));
            }
            try {
                filled = MelodyConstraints.apply(filled,part.getContextualPitchLocks(),custom);
                filled = MelodyConstraints.rerollLocks(filled,cache.get(owner),harmony,part.getContextualRerollWindow());
                result = ContextualMelodyPlanner.generate(harmony,Arrays.stream(mode.noteAdjustScale).mapToInt(Integer::intValue).toArray(),
                        filled,options,seed + part.getContextualRerollSeed());
                break;
            } catch (IllegalArgumentException invalid) { failure = invalid; }
        }
        if (result == null) { throw failure; }
        Map<Integer, List<Note>> map = new LinkedHashMap<>();
        for (HarmonyTimeline.Event event : harmony.events()) { map.put(event.index, new ArrayList<>()); }
        int volume = config.isScaleMidiVelocityInArrangement() ? section.getVol(0) : 100;
        int low = Math.max(1, Math.min(127, part.getVelocityMin() * volume / 100));
        int high = Math.max(low, Math.min(127, part.getVelocityMax() * volume / 100));
        for (ContextualMelodyPlanner.PlannedNote planned : result.notes) {
            Random performance = new Random(seed ^ (planned.slot.start * 0xd1b54a32d192ed03L));
            double rhythm = planned.slot.duration / (double) HarmonyTimeline.TICKS_PER_BEAT * timing.quarterNote;
            int velocity = low + performance.nextInt(high - low + 1);
            if (phrases.arrivals.contains(planned.slot.start) && performance.nextInt(100) < part.getAccents()) {
                velocity = Math.min(high,velocity + Math.max(1,(high - low) / 4));
            }
            Note note = new Note(planned.pitch < 0 ? Pitches.REST : planned.pitch + part.getTranspose(), rhythm, velocity);
            note.setDuration(rhythm * Math.min(1, articulation * (0.9 + performance.nextDouble() * 0.1)));
            map.get(harmony.at(planned.slot.start).index).add(note);
        }
        lastResult = result;
        cache.put(owner,result);
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
