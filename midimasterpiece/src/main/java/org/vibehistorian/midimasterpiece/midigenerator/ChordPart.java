package org.vibehistorian.midimasterpiece.midigenerator;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "chordPart")
@XmlType(propOrder = {})
public class ChordPart {
	private int instrument = 46;
	
	private int transitionChance = 0;
	private int transitionSplit = 625;
	
	private int strum = 0;
	private int delay = 0;
	private int transpose = 0;
	
	private int patternSeed = 0;
	private RhythmPattern pattern = RhythmPattern.RANDOM;
	private int patternRotation = 0;
	private int order = 1;
	
	public ChordPart() {
		
	}
	
	public ChordPart(int instrument, int transitionChance, int transitionSplit, int strum,
			int delay, int transpose, int patternSeed, RhythmPattern pattern, int patternRotation,
			int order) {
		super();
		this.setInstrument(instrument);
		this.transitionChance = transitionChance;
		this.transitionSplit = transitionSplit;
		this.strum = strum;
		this.delay = delay;
		this.transpose = transpose;
		this.patternSeed = patternSeed;
		this.pattern = pattern;
		this.patternRotation = patternRotation;
		this.order = order;
	}
	
	
	public int getTransitionChance() {
		return transitionChance;
	}
	
	public void setTransitionChance(int transitionChance) {
		this.transitionChance = transitionChance;
	}
	
	public int getTransitionSplit() {
		return transitionSplit;
	}
	
	public void setTransitionSplit(int transitionSplit) {
		this.transitionSplit = transitionSplit;
	}
	
	public int getStrum() {
		return strum;
	}
	
	public void setStrum(int strum) {
		this.strum = strum;
	}
	
	public int getDelay() {
		return delay;
	}
	
	public void setDelay(int delay) {
		this.delay = delay;
	}
	
	public int getTranspose() {
		return transpose;
	}
	
	public void setTranspose(int transpose) {
		this.transpose = transpose;
	}
	
	public RhythmPattern getPattern() {
		return pattern;
	}
	
	public void setPattern(RhythmPattern pattern) {
		this.pattern = pattern;
	}
	
	public int getPatternRotation() {
		return patternRotation;
	}
	
	public void setPatternRotation(int patternRotation) {
		this.patternRotation = patternRotation;
	}
	
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
	
	public int getInstrument() {
		return instrument;
	}
	
	public void setInstrument(int instrument) {
		this.instrument = instrument;
	}
}
