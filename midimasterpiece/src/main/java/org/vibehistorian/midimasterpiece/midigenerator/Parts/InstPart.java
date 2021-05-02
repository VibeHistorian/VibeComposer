package org.vibehistorian.midimasterpiece.midigenerator.Parts;

import org.vibehistorian.midimasterpiece.midigenerator.Enums.ChordSpanFill;
import org.vibehistorian.midimasterpiece.midigenerator.Enums.RhythmPattern;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.InstPanel;

public abstract class InstPart {
	protected int instrument = 46;

	protected int hitsPerPattern = 8;
	protected int chordSpan = 1;
	protected ChordSpanFill chordSpanFill = ChordSpanFill.ALL;

	protected int chordNotesStretch = 3;
	protected boolean stretchEnabled = false;

	protected int pauseChance = 20;
	protected int exceptionChance = 5;
	protected boolean repeatableNotes = true;
	protected int patternRepeat = 2;

	protected int delay = 0;
	protected int transpose = 0;

	protected int velocityMin = 70;
	protected int velocityMax = 90;

	protected int order = 1;

	protected int patternSeed = 0;
	protected RhythmPattern pattern = RhythmPattern.RANDOM;
	protected int patternShift = 0;

	protected int midiChannel = 10;

	protected boolean muted = false;

	public void setFromPanel(InstPanel panel, int lastRandomSeed) {
		setInstrument(panel.getInstrument());

		setHitsPerPattern(panel.getHitsPerPattern());
		setChordSpan(panel.getChordSpan());
		setChordSpanFill(panel.getChordSpanFill());

		setChordNotesStretch(panel.getChordNotesStretch());
		setStretchEnabled(panel.getStretchEnabled());

		setPauseChance(panel.getPauseChance());
		setExceptionChance(panel.getExceptionChance());
		setRepeatableNotes(panel.getRepeatableNotes());
		setPatternRepeat(panel.getPatternRepeat());

		setTranspose(panel.getTranspose());
		setDelay(panel.getDelay());

		setVelocityMin(panel.getVelocityMin());
		setVelocityMax(panel.getVelocityMax());

		setPatternSeed((panel.getPatternSeed() != 0) ? panel.getPatternSeed() : lastRandomSeed);
		setPattern(panel.getPattern());
		setPatternShift(panel.getPatternShift());

		setMuted(panel.getMuteInst());

		setMidiChannel(panel.getMidiChannel());

	}

	public int getDelay() {
		return delay;
	}

	public void setDelay(int delay) {
		this.delay = delay;
	}

	public int getInstrument() {
		return instrument;
	}

	public void setInstrument(int instrument) {
		this.instrument = instrument;
	}

	public int getHitsPerPattern() {
		return hitsPerPattern;
	}

	public void setHitsPerPattern(int hitsPerPattern) {
		this.hitsPerPattern = hitsPerPattern;
	}

	public int getChordSpan() {
		return chordSpan;
	}

	public void setChordSpan(int chordSpan) {
		this.chordSpan = chordSpan;
	}

	public int getPauseChance() {
		return pauseChance;
	}

	public void setPauseChance(int pauseChance) {
		this.pauseChance = pauseChance;
	}

	public int getExceptionChance() {
		return exceptionChance;
	}

	public void setExceptionChance(int exceptionChance) {
		this.exceptionChance = exceptionChance;
	}

	public boolean isRepeatableNotes() {
		return repeatableNotes;
	}

	public void setRepeatableNotes(boolean repeatableNotes) {
		this.repeatableNotes = repeatableNotes;
	}

	public int getPatternRepeat() {
		return patternRepeat;
	}

	public void setPatternRepeat(int patternRepeat) {
		this.patternRepeat = patternRepeat;
	}

	public int getTranspose() {
		return transpose;
	}

	public void setTranspose(int transpose) {
		this.transpose = transpose;
	}

	public int getPatternSeed() {
		return patternSeed;
	}

	public void setPatternSeed(int patternSeed) {
		this.patternSeed = patternSeed;
	}

	public RhythmPattern getPattern() {
		return pattern;
	}

	public void setPattern(RhythmPattern pattern) {
		this.pattern = pattern;
	}

	public int getPatternShift() {
		return patternShift;
	}

	public void setPatternShift(int patternShift) {
		this.patternShift = patternShift;
	}

	public boolean isMuted() {
		return muted;
	}

	public void setMuted(boolean muted) {
		this.muted = muted;
	}

	public int getMidiChannel() {
		return midiChannel;
	}

	public void setMidiChannel(int midiChannel) {
		this.midiChannel = midiChannel;
	}

	public ChordSpanFill getChordSpanFill() {
		return chordSpanFill;
	}

	public void setChordSpanFill(ChordSpanFill cspanFill) {
		this.chordSpanFill = cspanFill;
	}

	public int getChordNotesStretch() {
		return chordNotesStretch;
	}

	public void setChordNotesStretch(int chordStretch) {
		this.chordNotesStretch = chordStretch;
	}

	public boolean isStretchEnabled() {
		return stretchEnabled;
	}

	public void setStretchEnabled(boolean stretchEnabled) {
		this.stretchEnabled = stretchEnabled;
	}


	public int getVelocityMin() {
		return velocityMin;
	}

	public void setVelocityMin(int velocityMin) {
		this.velocityMin = velocityMin;
	}

	public int getVelocityMax() {
		return velocityMax;
	}

	public void setVelocityMax(int velocityMax) {
		this.velocityMax = velocityMax;
	}
}
