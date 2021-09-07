package org.vibehistorian.vibecomposer.Panels;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "chordSettings")
@XmlType(propOrder = {})
public class ChordGenSettings extends InstGenSettings {
	private boolean useDelay = false;
	private boolean useStrum = true;
	private boolean useSplit = false;
	private int sustainChance = 25;
	private int flattenVoicingChance = 100;
	private boolean useShortening = true;

	public ChordGenSettings() {
		super();
	}

	public boolean isUseDelay() {
		return useDelay;
	}

	public void setUseDelay(boolean val) {
		this.useDelay = val;
	}

	public boolean isUseStrum() {
		return useStrum;
	}

	public void setUseStrum(boolean val) {
		this.useStrum = val;
	}

	public boolean isUseSplit() {
		return useSplit;
	}

	public void setUseSplit(boolean val) {
		this.useSplit = val;
	}

	public int getSustainChance() {
		return sustainChance;
	}

	public void setSustainChance(int val) {
		this.sustainChance = val;
	}

	public boolean isUseShortening() {
		return useShortening;
	}

	public void setUseShortening(boolean val) {
		this.useShortening = val;
	}

	public int getFlattenVoicingChance() {
		return flattenVoicingChance;
	}

	public void setFlattenVoicingChance(int val) {
		this.flattenVoicingChance = val;
	}


}
