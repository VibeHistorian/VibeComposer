package org.vibehistorian.vibecomposer.generation;

/** Independent chord properties; explicit manual edits are allowed, generation respects the mask. */
public final class ChordLock {
    public static final int ROOT = 1, QUALITY = 2, BASS = 4, DURATION = 8, ALL = 15;
    private int index, mask;
    public ChordLock() { }
    public ChordLock(int index,int mask) { setIndex(index); setMask(mask); }
    public int getIndex() { return index; }
    public void setIndex(int value) { if (value < 0) { throw new IllegalArgumentException("Negative chord lock index"); } index = value; }
    public int getMask() { return mask; }
    public void setMask(int value) { if (value < 0 || value > ALL) { throw new IllegalArgumentException("Invalid chord lock mask"); } mask = value; }
    public boolean matches(ChordSymbol original,ChordSymbol candidate) {
        return ((mask & ROOT) == 0 || original.root == candidate.root)
                && ((mask & QUALITY) == 0 || original.quality.equals(candidate.quality))
                && ((mask & BASS) == 0 || original.pitches(true)[0] % 12 == candidate.pitches(true)[0] % 12);
    }
}
