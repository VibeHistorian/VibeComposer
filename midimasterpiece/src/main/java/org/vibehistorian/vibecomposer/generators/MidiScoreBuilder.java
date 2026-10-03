package org.vibehistorian.vibecomposer.generators;

import jm.music.data.Score;
import jm.music.tools.Mod;
import org.vibehistorian.vibecomposer.Components.ShowAreaBig;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.Helpers.PartExt;
import org.vibehistorian.vibecomposer.JMusicUtilsCustom;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.BassPart;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;

import java.util.List;
import java.util.Random;

/** Assigns generated instrument parts to score tracks and applies score-level processing. */
final class MidiScoreBuilder {

    @FunctionalInterface
    interface SequenceTrackAssigner {
        void assign(int instrument, int panelOrder, int trackNumber);
    }

    private final GUIConfig gc;
    private final List<Integer> partPadding;
    private final boolean collapseDrumTracks;
    private final double noteMultiplier;
    private final SequenceTrackAssigner sequenceTrackAssigner;

    MidiScoreBuilder(GUIConfig gc, List<Integer> partPadding, boolean collapseDrumTracks,
                     double noteMultiplier, SequenceTrackAssigner sequenceTrackAssigner) {
        this.gc = gc;
        this.partPadding = partPadding;
        this.collapseDrumTracks = collapseDrumTracks;
        this.noteMultiplier = noteMultiplier;
        this.sequenceTrackAssigner = sequenceTrackAssigner;
    }

    void build(int mainGeneratorSeed, long systemTime, boolean logPerformance, Score score,
               List<PartExt> melodyParts, List<PartExt> chordParts, List<PartExt> arpParts,
               List<PartExt> bassParts, List<PartExt> drumParts,
               boolean allowCombination, boolean transposeBCA) {
        int trackCounter = 1;
        int lastPartTrackCount = 1;
        for (int i = 0; i < melodyParts.size(); i++) {
            MelodyPart part = gc.getMelodyParts().get(i);
            if (!part.isMuted() && gc.isMelodyEnable()) {
                score.add(melodyParts.get(i));
                melodyParts.get(i).setTrackNumber(trackCounter);
                assignSequenceTrack(0, part, trackCounter++);
                if (allowCombination && gc.isCombineMelodyTracks()) {
                    for (int j = i + 1; j < gc.getMelodyParts().size(); j++) {
                        assignSequenceTrack(0, gc.getMelodyParts().get(j), -1);
                    }
                    break;
                }
            } else {
                trackCounter += padSingle(score, 0, trackCounter - lastPartTrackCount);
                assignSequenceTrack(0, part, -1);
            }
        }
        if (!transposeBCA) {
            Mod.transpose(score, gc.getTranspose());
        }
        trackCounter += padScoreParts(score, 0, trackCounter - lastPartTrackCount);
        lastPartTrackCount = trackCounter;

        for (int i = 0; i < bassParts.size(); i++) {
            BassPart part = gc.getBassParts().get(i);
            if (!part.isMuted() && gc.isBassEnable()) {
                score.add(bassParts.get(i));
                bassParts.get(i).setTrackNumber(trackCounter);
                assignSequenceTrack(1, part, trackCounter++);
            } else {
                trackCounter += padSingle(score, 0, trackCounter - lastPartTrackCount);
                assignSequenceTrack(1, part, -1);
            }
        }
        trackCounter += padScoreParts(score, 1, trackCounter - lastPartTrackCount);
        lastPartTrackCount = trackCounter;

        for (int i = 0; i < chordParts.size(); i++) {
            ChordPart part = gc.getChordParts().get(i);
            if (!part.isMuted() && gc.isChordsEnable()) {
                score.add(chordParts.get(i));
                chordParts.get(i).setTrackNumber(trackCounter);
                assignSequenceTrack(2, part, trackCounter++);
            } else {
                trackCounter += padSingle(score, 0, trackCounter - lastPartTrackCount);
                assignSequenceTrack(2, part, -1);
            }
        }
        trackCounter += padScoreParts(score, 2, trackCounter - lastPartTrackCount);
        lastPartTrackCount = trackCounter;

        for (int i = 0; i < arpParts.size(); i++) {
            ArpPart part = gc.getArpParts().get(i);
            if (!part.isMuted() && gc.isArpsEnable()) {
                score.add(arpParts.get(i));
                arpParts.get(i).setTrackNumber(trackCounter);
                assignSequenceTrack(3, part, trackCounter++);
            } else {
                trackCounter += padSingle(score, 0, trackCounter - lastPartTrackCount);
                assignSequenceTrack(3, part, -1);
            }
        }
        trackCounter += padScoreParts(score, 3, trackCounter - lastPartTrackCount);

        if (transposeBCA) {
            Mod.transpose(score, gc.getTranspose());
        }

        for (int i = 0; i < drumParts.size(); i++) {
            DrumPart part = gc.getDrumParts().get(i);
            if (!allowCombination || !collapseDrumTracks) {
                score.add(drumParts.get(i));
            }
            if (!part.isMuted() && gc.isDrumsEnable()) {
                assignSequenceTrack(4, part, trackCounter);
                drumParts.get(i).setTrackNumber(trackCounter);
                if (allowCombination && collapseDrumTracks) {
                    score.add(drumParts.get(i));
                    for (int j = i + 1; j < gc.getDrumParts().size(); j++) {
                        assignSequenceTrack(4, gc.getDrumParts().get(j), -1);
                    }
                    break;
                }
            } else {
                assignSequenceTrack(4, part, -1);
            }
            if (!allowCombination || !collapseDrumTracks) {
                trackCounter++;
            }
        }
        if (!allowCombination || !collapseDrumTracks) {
            trackCounter += padScoreParts(score, 4, trackCounter - lastPartTrackCount);
        }

        if (logPerformance) {
            LG.i("Added to score, at: " + (System.currentTimeMillis() - systemTime));
        }
        LG.d("Added parts to score.., allow combo: " + allowCombination);
        Random random = new Random(mainGeneratorSeed + 999);
        long humanizerRandSeed = random.nextLong();
        for (Object object : score.getPartList()) {
            PartExt part = (PartExt) object;
            if (part == null || part.isFillerPart()) {
                continue;
            }
            int noteColorIndex = ShowAreaBig.getIndexForPartName(part.getTitle());
            boolean isDrum = noteColorIndex == 4;
            boolean shouldRandomize = (isDrum && gc.getHumanizeDrums() > 0)
                    || (!isDrum && gc.getHumanizeNotes() > 0);
            if (shouldRandomize) {
                int partOrder = ShowAreaBig.getPartOrderForPartName(part.getTitle());
                double divisor = noteColorIndex == 2 ? 30000.0 : 10000.0;
                random.setSeed(humanizerRandSeed + noteColorIndex * 1000 + partOrder);
                JMusicUtilsCustom.humanize(part, random,
                        isDrum ? noteMultiplier * gc.getHumanizeDrums() / divisor
                                : noteMultiplier * gc.getHumanizeNotes() / divisor,
                        isDrum);
            }
        }
    }

    private void assignSequenceTrack(int instrument, org.vibehistorian.vibecomposer.Parts.InstPart part,
                                     int trackNumber) {
        sequenceTrackAssigner.assign(instrument, part.getOrder(), trackNumber);
    }

    private int padScoreParts(Score score, int part, int trackCount) {
        if (paddable(part, trackCount)) {
            int tracksToPad = partPadding.get(part) - trackCount;
            LG.d("Padding: " + part + ", #: " + tracksToPad);
            for (int i = 0; i < tracksToPad; i++) {
                score.add(PartExt.makeFillerPart());
            }
            return tracksToPad;
        }
        return 0;
    }

    private int padSingle(Score score, int part, int trackCount) {
        if (paddable(part, trackCount)) {
            LG.d("Padding Single: " + part + ", #: 1");
            score.add(PartExt.makeFillerPart());
            return 1;
        }
        return 0;
    }

    private boolean paddable(int part, int trackCount) {
        return gc.isPartEnabled(part) && partPadding.size() > part
                && trackCount < partPadding.get(part);
    }
}
