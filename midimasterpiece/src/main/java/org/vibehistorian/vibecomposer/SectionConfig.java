package org.vibehistorian.vibecomposer;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "SectionConfig")
@XmlType(propOrder = {})
public class SectionConfig implements Cloneable {
	private Integer beatDurationMultiplierIndex = null;
	private Integer sectionSwingOverride = null;
	private Integer sectionBpm = null;

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
}
