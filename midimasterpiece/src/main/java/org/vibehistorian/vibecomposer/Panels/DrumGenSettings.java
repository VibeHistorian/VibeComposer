package org.vibehistorian.vibecomposer.Panels;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "drumSettings")
@XmlType(propOrder = {})
public class DrumGenSettings {
	private boolean useSlide = true;
	private boolean includePresets = true;
	private int velocityPatternChance = 100;
	private int patternShiftChance = 25;
	
	public boolean isUseSlide() {
		return useSlide;
	}
	
	public void setUseSlide(boolean val) {
		this.useSlide = val;
	}
	
	public boolean isIncludePresets() {
		return includePresets;
	}
	
	public void setIncludePresets(boolean val) {
		this.includePresets = val;
	}
	
	public int getVelocityPatternChance() {
		return velocityPatternChance;
	}
	
	public void setVelocityPatternChance(int val) {
		this.velocityPatternChance = val;
	}
	
	public int getPatternShiftChance() {
		return patternShiftChance;
	}
	
	public void setPatternShiftChance(int patternShiftChance) {
		this.patternShiftChance = patternShiftChance;
	}
	
	
}
