package org.vibehistorian.midimasterpiece.midigenerator;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "arpSettings")
@XmlType(propOrder = {})
public class ArpGenSettings {
	
	private boolean useTranspose = true;
	private boolean includePresets = true;
	private int rotationChance = 25;
	
	
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
