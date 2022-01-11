package org.vibehistorian.vibecomposer;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;

@XmlRootElement(name = "SectionConfig")
@XmlType(propOrder = {})
public class SectionConfig implements Cloneable {
	private Integer beatDurationMultiplierIndex = null;
	private Integer sectionSwingOverride = null;
	private Integer sectionBpm = null;
	private Integer customKeyChange = null;
	private ScaleMode customScale = null;
	private int customKeyChangeType = 0;

	public SectionConfig() {

	}

	public Integer getBeatDurationMultiplierIndex() {
		return beatDurationMultiplierIndex;
	}

	public void setBeatDurationMultiplierIndex(Integer beatDurationMultiplierIndex) {
		this.beatDurationMultiplierIndex = beatDurationMultiplierIndex;
	}

	public Integer getSectionSwingOverride() {
		return sectionSwingOverride;
	}

	public void setSectionSwingOverride(Integer sectionSwingOverride) {
		this.sectionSwingOverride = sectionSwingOverride;
	}

	public Integer getSectionBpm() {
		return sectionBpm;
	}

	public void setSectionBpm(Integer sectionBpm) {
		this.sectionBpm = sectionBpm;
	}

	@Override
	public SectionConfig clone() {
		try {
			return (SectionConfig) super.clone();
		} catch (CloneNotSupportedException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public Integer getCustomKeyChange() {
		return customKeyChange;
	}

	public void setCustomKeyChange(Integer customKeyChange) {
		this.customKeyChange = customKeyChange;
	}

	public ScaleMode getCustomScale() {
		return customScale;
	}

	public void setCustomScale(ScaleMode customScale) {
		this.customScale = customScale;
	}

	public int getCustomKeyChangeType() {
		return customKeyChangeType;
	}

	public void setCustomKeyChangeType(int customKeyChangeType) {
		this.customKeyChangeType = customKeyChangeType;
	}
}
