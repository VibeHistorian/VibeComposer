package org.vibehistorian.vibecomposer.Panels;

public class InstGenSettings {
	protected boolean useTranspose = true;
	protected boolean includePresets = true;
	protected int shiftChance = 25;


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

	public int getShiftChance() {
		return shiftChance;
	}

	public void setShiftChance(int shiftChance) {
		this.shiftChance = shiftChance;
	}
}
