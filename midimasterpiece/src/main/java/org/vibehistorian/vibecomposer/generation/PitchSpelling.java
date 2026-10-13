package org.vibehistorian.vibecomposer.generation;

import org.vibehistorian.vibecomposer.MidiUtils;

/** MIDI equivalence and musical spelling are separate representations. */
public final class PitchSpelling {
    private static final String[] LETTERS = {"C","D","E","F","G","A","B"};
    private static final int[] NATURAL = {0,2,4,5,7,9,11};
    private PitchSpelling() { }
    public static String letter(int letter,int pitchClass) {
        int index = Math.floorMod(letter,7), delta = Math.floorMod(pitchClass - NATURAL[index] + 6,12) - 6;
        StringBuilder text = new StringBuilder(LETTERS[index]);
        for (int i = 0; i < Math.abs(delta); i++) { text.append(delta < 0 ? "b" : "#"); }
        return text.toString();
    }
    public static String chordTone(ChordSymbol chord,int pitch) {
        int interval = Math.floorMod(pitch - chord.root,12), rootLetter = MidiUtils.CHORD_FIRST_LETTERS.indexOf(chord.rootName.substring(0,1));
        int step = interval == 0 ? 0 : interval == 1 || interval == 2 ? 1 : interval == 3 || interval == 4 ? 2
                : interval == 5 ? 3 : interval == 6 || interval == 7 || interval == 8 ? 4 : interval == 9 ? 5 : 6;
        if (interval == 3 && chord.quality.contains("#9")) { step = 1; }
        if (interval == 6 && chord.quality.contains("#11")) { step = 3; }
        return letter(rootLetter + step,Math.floorMod(pitch,12));
    }
    public static String inScale(int pitch,MidiUtils.ScaleMode mode) {
        int pc = Math.floorMod(pitch,12);
        for (int i = 0; i < mode.noteAdjustScale.length; i++) { if (Math.floorMod(mode.noteAdjustScale[i],12) == pc) { return letter(i,pc); } }
        return MidiUtils.SEMITONE_LETTERS.get(pc);
    }
}
