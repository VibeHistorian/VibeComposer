package org.vibehistorian.midimasterpiece.midigenerator;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "drumPart")
@XmlType(propOrder = {})
public class DrumPart {
	private int pitch = 36;
	private int hitsPerPattern = 8;
	private int chordSpan = 1;
	
	private int pauseChance = 70;
	private int exceptionChance = 5;
	
	private int velocityMin = 60;
	private int velocityMax = 100;
	
	private int slideMiliseconds = 0;
	
	private int patternSeed = 0;
	private DrumPattern pattern = DrumPattern.RANDOM;
	private boolean isVelocityPattern = true;
	private int patternRotation = 0;
	
	public DrumPart() {
		
	}
	
	public DrumPart(int pitch, int hitsPerPattern, int chordSpan, int pauseChance,
			int exceptionChance, int velocityMin, int velocityMax, int slideMiliseconds,
			int patternSeed, DrumPattern pattern, boolean isVelocityPattern, int patternRotation) {
		this.pitch = pitch;
		this.hitsPerPattern = hitsPerPattern;
		this.chordSpan = chordSpan;
		this.pauseChance = pauseChance;
		this.exceptionChance = exceptionChance;
		this.velocityMin = velocityMin;
		this.velocityMax = velocityMax;
		this.slideMiliseconds = slideMiliseconds;
		this.patternSeed = patternSeed;
		this.pattern = pattern;
		this.isVelocityPattern = isVelocityPattern;
		this.patternRotation = patternRotation;
	}
	
	@XmlAttribute
	public int getPitch() {
		return pitch;
	}
	
	public void setPitch(int pitch) {
		this.pitch = pitch;
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
	
	public int getSlideMiliseconds() {
		return slideMiliseconds;
	}
	
	public void setSlideMiliseconds(int slideMiliseconds) {
		this.slideMiliseconds = slideMiliseconds;
	}
	
	public int getPatternSeed() {
		return patternSeed;
	}
	
	public void setPatternSeed(int patternSeed) {
		this.patternSeed = patternSeed;
	}
	
	public DrumPattern getPattern() {
		return pattern;
	}
	
	public void setPattern(DrumPattern pattern) {
		this.pattern = pattern;
	}
	
	public boolean isVelocityPattern() {
		return isVelocityPattern;
	}
	
	public void setVelocityPattern(boolean isVelocityPattern) {
		this.isVelocityPattern = isVelocityPattern;
	}
	
	public int getPatternRotation() {
		return patternRotation;
	}
	
	public void setPatternRotation(int patternRotation) {
		this.patternRotation = patternRotation;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + chordSpan;
		result = prime * result + exceptionChance;
		result = prime * result + hitsPerPattern;
		result = prime * result + (isVelocityPattern ? 1231 : 1237);
		result = prime * result + ((pattern == null) ? 0 : pattern.hashCode());
		result = prime * result + patternRotation;
		result = prime * result + patternSeed;
		result = prime * result + pauseChance;
		result = prime * result + pitch;
		result = prime * result + slideMiliseconds;
		result = prime * result + velocityMax;
		result = prime * result + velocityMin;
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DrumPart other = (DrumPart) obj;
		if (chordSpan != other.chordSpan)
			return false;
		if (exceptionChance != other.exceptionChance)
			return false;
		if (hitsPerPattern != other.hitsPerPattern)
			return false;
		if (isVelocityPattern != other.isVelocityPattern)
			return false;
		if (pattern != other.pattern)
			return false;
		if (patternRotation != other.patternRotation)
			return false;
		if (patternSeed != other.patternSeed)
			return false;
		if (pauseChance != other.pauseChance)
			return false;
		if (pitch != other.pitch)
			return false;
		if (slideMiliseconds != other.slideMiliseconds)
			return false;
		if (velocityMax != other.velocityMax)
			return false;
		if (velocityMin != other.velocityMin)
			return false;
		return true;
	}
	
}
