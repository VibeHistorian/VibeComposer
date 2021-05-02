package org.vibehistorian.midimasterpiece.midigenerator.Panels;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "chordSettings")
@XmlType(propOrder = {})
public class ChordGenSettings extends InstGenSettings {
	private boolean useDelay = false;
	private boolean useStrum = true;
	private boolean useSplit = false;
	private int sustainChance = 25;

	public ChordGenSettings() {
		super();
	}

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

	public int getSustainChance() {
		return sustainChance;
	}

	public void setSustainChance(int sustainChance) {
		this.sustainChance = sustainChance;
	}


}
