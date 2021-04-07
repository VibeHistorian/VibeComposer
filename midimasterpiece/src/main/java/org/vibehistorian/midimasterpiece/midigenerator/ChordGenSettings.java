package org.vibehistorian.midimasterpiece.midigenerator;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "chordSettings")
@XmlType(propOrder = {})
public class ChordGenSettings {
	private boolean useDelay = false;
	private boolean useStrum = true;
	private boolean useSplit = false;
	private boolean useTranspose = true;
	private boolean includePresets = true;
	private int rotationChance = 25;
	
	public boolean isUseDelay() {
		return useDelay;
	}
	
	public void setUseDelay(boolean useDelay) {
		this.useDelay = useDelay;
	}
	
	public boolean isUseStrum() {
		return useStrum;
	}
	
	public void setUseStrum(boolean useStrum) {
		this.useStrum = useStrum;
	}
	
	public boolean isUseSplit() {
		return useSplit;
	}
	
	public void setUseSplit(boolean useSplit) {
		this.useSplit = useSplit;
	}
	
	public boolean isUseTranspose() {
		return useTranspose;
	}
	
	public void setUseTranspose(boolean useTranspose) {
		this.useTranspose = useTranspose;
	}
	
	public boolean isIncludePresets() {
		return includePresets;
	}
	
	public void setIncludePresets(boolean includePresets) {
		this.includePresets = includePresets;
	}
	
	public int getRotationChance() {
		return rotationChance;
	}
	
	public void setRotationChance(int rotationChance) {
		this.rotationChance = rotationChance;
	}
	
	
}
