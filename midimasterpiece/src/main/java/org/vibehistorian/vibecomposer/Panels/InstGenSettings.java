package org.vibehistorian.vibecomposer.Panels;

public class InstGenSettings {
	protected boolean useTranspose = true;
	protected boolean includePresets = true;
	protected int shiftChance = 25;


	public boolean isUseTranspose() {
		return useTranspose;
	}

	public void setUseTranspose(boolean val) {
		this.useTranspose = val;
	}

	public boolean isIncludePresets() {
		return includePresets;
	}

	public void setIncludePresets(boolean val) {
		this.includePresets = val;
	}

	public int getShiftChance() {
		return shiftChance;
	}

	public void setShiftChance(int val) {
		this.shiftChance = val;
	}
}
