package org.vibehistorian.vibecomposer.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Serialized beat=pitch locks and bounded local rerolls; unrelated notes remain exact. */
public final class MelodyConstraints {
    private MelodyConstraints() { }
    public static Map<Long,Integer> parse(String text) {
        Map<Long,Integer> locks = new TreeMap<>();
        if (text == null || text.trim().isEmpty()) { return locks; }
        for (String term : text.split(",")) {
            String[] pair = term.trim().split("=");
            if (pair.length != 2) { throw new IllegalArgumentException("Pitch locks use beat=MIDI, e.g. 0=72,4=76"); }
            long tick = HarmonyTimeline.ticks(Double.parseDouble(pair[0].trim())); int pitch = Integer.parseInt(pair[1].trim());
            if (pitch < 0 || pitch > 127 || locks.put(tick,pitch) != null) { throw new IllegalArgumentException("Invalid or repeated pitch lock"); }
        }
        return locks;
    }
    public static List<ContextualMelodyPlanner.Slot> apply(List<ContextualMelodyPlanner.Slot> rhythm,String text,boolean strict) {
        Map<Long,Integer> locks = parse(text); List<ContextualMelodyPlanner.Slot> result = new ArrayList<>();
        for (ContextualMelodyPlanner.Slot slot : rhythm) {
            List<Long> boundaries = new ArrayList<>(); boundaries.add(slot.start);
            for (long tick : locks.keySet()) {
                if (tick > slot.start && tick < slot.start + slot.duration) {
                    if (strict) { throw new IllegalArgumentException("Pitch lock has no authored rhythm attack at tick " + tick); }
                    boundaries.add(tick);
                }
            }
            boundaries.add(slot.start + slot.duration);
            for (int i = 0; i + 1 < boundaries.size(); i++) {
                long start = boundaries.get(i); Integer pitch = locks.remove(start);
                if (pitch == null) { pitch = slot.lockedPitch; }
                if (strict && slot.rest && pitch != null) { throw new IllegalArgumentException("Pitch lock conflicts with authored rest at tick " + start); }
                result.add(new ContextualMelodyPlanner.Slot(start,boundaries.get(i + 1) - start,slot.rest && pitch == null,pitch,slot.motif,slot.intent));
            }
        }
        if (!locks.isEmpty()) { throw new IllegalArgumentException("Pitch lock outside melody: " + locks.keySet()); }
        return result;
    }

    public static List<ContextualMelodyPlanner.Slot> rerollLocks(List<ContextualMelodyPlanner.Slot> rhythm,
            ContextualMelodyPlanner.Result previous,HarmonyTimeline harmony,String window) {
        if (window == null || window.trim().isEmpty()) { return rhythm; }
        String[] parts = window.split(",");
        if (parts.length != 2) { throw new IllegalArgumentException("Reroll window uses startBeat,endBeat"); }
        long start = HarmonyTimeline.ticks(Double.parseDouble(parts[0])), end = HarmonyTimeline.ticks(Double.parseDouble(parts[1]));
        if (start >= end || end > harmony.length()) { throw new IllegalArgumentException("Reroll window outside melody"); }
        if (previous == null) { throw new IllegalArgumentException("Generate a contextual melody before a local reroll"); }
        if (previous.harmonyRevision != harmony.revision) { throw new IllegalArgumentException("Harmony changed; clear the reroll window and regenerate before a local edit"); }
        boolean hasStart = start == 0, hasEnd = end == harmony.length();
        for (ContextualMelodyPlanner.PlannedNote note : previous.notes) {
            hasStart |= note.slot.start == start; hasEnd |= note.slot.start == end;
        }
        if (!hasStart || !hasEnd) { throw new IllegalArgumentException("Reroll window must follow existing note boundaries"); }
        List<ContextualMelodyPlanner.Slot> result = new ArrayList<>();
        for (ContextualMelodyPlanner.PlannedNote note : previous.notes) {
            if (note.slot.start >= start) { break; }
            result.add(locked(note));
        }
        for (ContextualMelodyPlanner.Slot slot : rhythm) {
            long from = Math.max(start,slot.start), to = Math.min(end,slot.start + slot.duration);
            if (from < to) { result.add(new ContextualMelodyPlanner.Slot(from,to - from,slot.rest,slot.lockedPitch,slot.motif,slot.intent)); }
        }
        for (ContextualMelodyPlanner.PlannedNote note : previous.notes) {
            if (note.slot.start >= end) { result.add(locked(note)); }
        }
        return result;
    }
    private static ContextualMelodyPlanner.Slot locked(ContextualMelodyPlanner.PlannedNote note) {
        return new ContextualMelodyPlanner.Slot(note.slot.start,note.slot.duration,note.pitch < 0,
                note.pitch < 0 ? null : note.pitch,note.slot.motif,note.slot.intent);
    }
}
