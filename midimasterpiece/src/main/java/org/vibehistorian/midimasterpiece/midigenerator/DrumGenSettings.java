package org.vibehistorian.midimasterpiece.midigenerator;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "drumSettings")
@XmlType(propOrder = {})
public class DrumGenSettings {
	private boolean useSlide = true;
	private boolean includePresets = true;
	private int velocityPatternChance = 100;
	private int patternRotationChance = 25;
	
	public boolean isUseSlide() {
		return useSlide;
	}
	
	public void setUseSlide(boolean useSlide) {
		this.useSlide = useSlide;
	}
	
	public boolean isIncludePresets() {
		return includePresets;
	}
	
	public void setIncludePresets(boolean includePresets) {
		this.includePresets = includePresets;
	}
	
	public int getVelocityPatternChance() {
		return velocityPatternChance;
	}
	
	public void setVelocityPatternChance(int velocityPatternChance) {
		this.velocityPatternChance = velocityPatternChance;
	}
	
	public int getPatternRotationChance() {
		return patternRotationChance;
	}
	
	public void setPatternRotationChance(int patternRotationChance) {
		this.patternRotationChance = patternRotationChance;
	}
	
	
}
