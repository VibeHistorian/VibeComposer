package org.vibehistorian.midimasterpiece.midigenerator;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "drumPart")
@XmlType(propOrder = {})
public class DrumPart extends InstPart {
	private int pitch = 36;
	
	private int velocityMin = 60;
	private int velocityMax = 100;
	
	private int swingPercent = 50;
	
	private boolean isVelocityPattern = true;
	
	public DrumPart() {
		
	}
	
	public DrumPart(int pitch, int hitsPerPattern, int chordSpan, int pauseChance,
			int exceptionChance, int velocityMin, int velocityMax, int slideMiliseconds,
			int patternSeed, RhythmPattern pattern, boolean isVelocityPattern, int patternShift,
			boolean muted, int swingPercent) {
		this.pitch = pitch;
		this.hitsPerPattern = hitsPerPattern;
		this.chordSpan = chordSpan;
		this.pauseChance = pauseChance;
		this.exceptionChance = exceptionChance;
		this.velocityMin = velocityMin;
		this.velocityMax = velocityMax;
		this.delay = slideMiliseconds;
		this.patternSeed = patternSeed;
		this.pattern = pattern;
		this.isVelocityPattern = isVelocityPattern;
		this.patternShift = patternShift;
		this.muted = muted;
		this.swingPercent = swingPercent;
	}
	
	
	public int getPitch() {
		return pitch;
	}
	
	public void setPitch(int pitch) {
		this.pitch = pitch;
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
	
	
	public boolean isVelocityPattern() {
		return isVelocityPattern;
	}
	
	public void setVelocityPattern(boolean isVelocityPattern) {
		this.isVelocityPattern = isVelocityPattern;
	}
	
	@XmlAttribute
	public int getOrder() {
		return order;
	}
	
	public void setOrder(int order) {
		this.order = order;
	}
	
	public int getSwingPercent() {
		return swingPercent;
	}
	
	public void setSwingPercent(int swingPercent) {
		this.swingPercent = swingPercent;
	}
	
}
