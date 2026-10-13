package org.vibehistorian.vibecomposer.generation;

import org.vibehistorian.vibecomposer.GUIConfig;
import java.util.*;

/** Opt-in bounded co-design. Fixed user melodies use harmonization instead. */
public final class MelodyHarmonyCoDesigner {
    public static final class Result {
        public final HarmonyTimeline harmony;
        public final ContextualMelodyPlanner.Result melody;
        public final int passes;
        public final boolean converged;
        private Result(HarmonyTimeline harmony,ContextualMelodyPlanner.Result melody,int passes,boolean converged) {
            this.harmony = harmony; this.melody = melody; this.passes = passes; this.converged = converged;
        }
    }
    private MelodyHarmonyCoDesigner() { }
    public static Result generate(GUIConfig config,HarmonyTimeline initial,List<ContextualMelodyPlanner.Slot> rhythm,
            ContextualMelodyPlanner.Options options,Map<Integer,String> chordLocks,long seed) {
        HarmonyTimeline harmony = initial; ContextualMelodyPlanner.Result melody = null;
        int[] scale = Arrays.stream(config.getScaleMode().noteAdjustScale).mapToInt(Integer::intValue).toArray();
        for (int pass = 0; pass < 2; pass++) {
            melody = ContextualMelodyPlanner.generate(harmony,scale,rhythm,options,seed);
            List<HarmonyHarmonizer.MelodyNote> notes = new ArrayList<>();
            for (ContextualMelodyPlanner.PlannedNote note : melody.notes) {
                if (note.pitch >= 0) { notes.add(new HarmonyHarmonizer.MelodyNote(note.slot.start,note.slot.duration,note.pitch,
                        note.role == ContextualMelodyPlanner.Role.CHORD && note.slot.start % 960 == 0 ? 1 : 0)); }
            }
            if (notes.isEmpty()) { return new Result(harmony,melody,pass + 1,true); }
            List<Double> beats = new ArrayList<>(); List<String> ids = new ArrayList<>();
            for (HarmonyTimeline.Event event : harmony.events()) { beats.add((event.end - event.start) / 960.0); ids.add(event.id); }
            HarmonyTimeline proposal = HarmonyHarmonizer.harmonize(config,notes,beats,chordLocks).withIdentities(ids);
            boolean same = true;
            for (int i = 0; i < harmony.events().size(); i++) { same &= harmony.events().get(i).symbol.equals(proposal.events().get(i).symbol); }
            if (same) { return new Result(harmony,melody,pass + 1,true); }
            if (pass == 1) { return new Result(harmony,melody,2,false); }
            harmony = proposal;
        }
        throw new IllegalStateException("Unreachable co-design state");
    }
}
