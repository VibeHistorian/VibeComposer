package org.vibehistorian.vibecomposer;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "SectionConfig")
@XmlType(propOrder = {})
public class SectionConfig {
	private Integer beatDurationMultiplierIndex = null;
	private Integer sectionSwingOverride = null;
	private Integer sectionBpm = null;
}
