package org.vibehistorian.midimasterpiece.midigenerator;

public abstract class InstPart {
	protected int instrument = 46;
	
	protected int hitsPerPattern = 8;
	protected int chordSpan = 1;
	protected ChordSpanFill chordSpanFill = ChordSpanFill.ALL;
	
	protected int pauseChance = 70;
	protected int exceptionChance = 5;
	protected boolean repeatableNotes = true;
	protected int patternRepeat = 2;
	
	protected int delay = 0;
	protected int transpose = 0;
	
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
		
		setPauseChance(panel.getPauseChance());
		setExceptionChance(panel.getExceptionChance());
		setRepeatableNotes(panel.getRepeatableNotes());
		setPatternRepeat(panel.getPatternRepeat());
		
		setTranspose(panel.getTranspose());
		setDelay(panel.getDelay());
		
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
}
