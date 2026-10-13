package org.vibehistorian.vibecomposer.generation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Phrase purpose and arrival times are independent of the chord attack grid. */
public final class MelodyPhrasePlan {
    public enum Form { STATEMENT_VARIATION, QUESTION_ANSWER, SENTENCE, CONTINUOUS }
    public enum Purpose { STATEMENT, CONTINUATION, QUESTION, ANSWER, CLOSING }
    public static final class Phrase {
        public final String id;
        public final long start, end, climax;
        public final Purpose purpose;
        public final int motif;
        private Phrase(String id, long start, long end, Purpose purpose, int motif) {
            this.id = id; this.start = start; this.end = end; this.purpose = purpose; this.motif = motif;
            this.climax = start + (end - start) * 5 / 8;
        }
    }
    public final Form form;
    public final int beatsPerBar;
    public final List<Phrase> phrases;
    public final List<Long> arrivals;
    private MelodyPhrasePlan(Form form, int beatsPerBar,List<Phrase> phrases, List<Long> arrivals) {
        this.beatsPerBar = beatsPerBar;
        this.form = form; this.phrases = Collections.unmodifiableList(phrases);
        this.arrivals = Collections.unmodifiableList(arrivals);
    }

    public static MelodyPhrasePlan create(HarmonyTimeline harmony, Form form, int beatsPerBar,
            int barsPerPhrase, List<Integer> motifs, ContextualMelodyPlanner.Ending ending) {
        if (beatsPerBar < 1 || beatsPerBar > 12 || barsPerPhrase < 1 || barsPerPhrase > 16 || motifs.isEmpty()) {
            throw new IllegalArgumentException("Invalid phrase dimensions or motif list");
        }
        long span = beatsPerBar * (long) barsPerPhrase * HarmonyTimeline.TICKS_PER_BEAT;
        List<Phrase> phrases = new ArrayList<>(); List<Long> arrivals = new ArrayList<>();
        for (long start = 0; start < harmony.length(); start += span) {
            int index = phrases.size(); long end = Math.min(harmony.length(),start + span);
            Purpose purpose = form == Form.QUESTION_ANSWER ? (index % 2 == 0 ? Purpose.QUESTION : Purpose.ANSWER)
                    : form == Form.SENTENCE && index % 3 == 2 ? Purpose.CONTINUATION
                    : index % 2 == 0 ? Purpose.STATEMENT : Purpose.CONTINUATION;
            if (end == harmony.length() && ending == ContextualMelodyPlanner.Ending.CLOSED) { purpose = Purpose.CLOSING; }
            phrases.add(new Phrase(harmony.at(start).id + "/phrase/" + (start - harmony.at(start).start),start,end,purpose,motifs.get(index % motifs.size())));
            arrivals.add(start);
            if (end - start > 2 * HarmonyTimeline.TICKS_PER_BEAT) { arrivals.add(end - HarmonyTimeline.TICKS_PER_BEAT); }
        }
        return new MelodyPhrasePlan(form,beatsPerBar,phrases,arrivals);
    }

    public Phrase at(long tick) {
        for (Phrase phrase : phrases) { if (tick >= phrase.start && tick < phrase.end) { return phrase; } }
        throw new IllegalArgumentException("Outside phrase plan: " + tick);
    }

    /** Multiple bounded candidates permit pitch/rhythm co-design without moving a custom rhythm. */
    public List<ContextualMelodyPlanner.Slot> rhythm(int speed, int pauseChance, long seed, int variant) {
        List<ContextualMelodyPlanner.Slot> result = new ArrayList<>();
        long unit = speed >= 75 ? 240 : speed >= 35 ? 480 : 960;
        for (Phrase phrase : phrases) {
            Random random = new Random(seed ^ (0x9e3779b97f4a7c15L * Math.abs(phrase.motif)) ^ variant);
            long cursor = phrase.start; int index = 0;
            while (cursor < phrase.end) {
                long duration = Math.min(phrase.end - cursor,unit * (random.nextInt(4) == 0 ? 2 : 1));
                if (phrase.purpose == Purpose.CLOSING && phrase.end - cursor <= 2 * unit) { duration = phrase.end - cursor; }
                boolean rest = index > 0 && cursor + duration < phrase.end && random.nextInt(100)
                        < pauseChance + (phrase.purpose == Purpose.QUESTION ? 8 : 0);
                ContextualMelodyPlanner.Intent intent = !rest && phrase.purpose == Purpose.CONTINUATION
                        && cursor % 960 == 0 && random.nextInt(100) < 12
                        ? ContextualMelodyPlanner.Intent.APPOGGIATURA : ContextualMelodyPlanner.Intent.AUTO;
                result.add(new ContextualMelodyPlanner.Slot(cursor,duration,rest,null,phrase.motif,intent));
                cursor += duration; index++;
            }
        }
        return Collections.unmodifiableList(result);
    }

    public boolean arrival(ContextualMelodyPlanner.Slot slot) { return arrivals.contains(slot.start); }

    /** Last retry splits only unplanned harmonic overlaps; it does not silently relax pitch/range locks. */
    public static List<ContextualMelodyPlanner.Slot> splitAtChanges(List<ContextualMelodyPlanner.Slot> rhythm,
            HarmonyTimeline harmony) {
        List<ContextualMelodyPlanner.Slot> result = new ArrayList<>();
        for (ContextualMelodyPlanner.Slot slot : rhythm) {
            long end = slot.start + slot.duration, cursor = slot.start;
            while (cursor < end) {
                long next = Math.min(end,harmony.at(cursor).end);
                result.add(new ContextualMelodyPlanner.Slot(cursor,next - cursor,slot.rest,slot.lockedPitch,slot.motif,slot.intent));
                cursor = next;
            }
        }
        return result;
    }
}
