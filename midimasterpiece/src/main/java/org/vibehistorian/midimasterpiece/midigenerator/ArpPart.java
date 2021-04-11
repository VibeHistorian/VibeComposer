package org.vibehistorian.midimasterpiece.midigenerator;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "arpPart")
@XmlType(propOrder = {})
public class ArpPart {
	private int instrument = 46;
	
	private int hitsPerPattern = 8;
	private int chordSpan = 1;
	
	private int pauseChance = 70;
	private int exceptionChance = 5;
	private boolean repeatableNotes = true;
	private int patternRepeat = 2;
	
	private int transpose = 0;
	
	private int order = 1;
	
	private int patternSeed = 0;
	private RhythmPattern pattern = RhythmPattern.RANDOM;
	private int patternShift = 0;
	
	private boolean muted = false;
	
	public ArpPart() {
		
	}
	
	public ArpPart(int instrument, int hitsPerPattern, int chordSpan, int pauseChance,
			int exceptionChance, boolean repeatableNotes, int patternRepeat, int transpose,
			int order, int patternSeed, RhythmPattern pattern, int patternShift, boolean muted) {
		super();
		this.instrument = instrument;
		this.hitsPerPattern = hitsPerPattern;
		this.chordSpan = chordSpan;
		this.pauseChance = pauseChance;
		this.exceptionChance = exceptionChance;
		this.repeatableNotes = repeatableNotes;
		this.patternRepeat = patternRepeat;
		this.transpose = transpose;
		this.order = order;
		this.patternSeed = patternSeed;
		this.pattern = pattern;
		this.patternShift = patternShift;
		this.setMuted(muted);
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
	
	@XmlAttribute
	public int getOrder() {
		return order;
	}
	
	public void setOrder(int order) {
		this.order = order;
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
	
	
}
