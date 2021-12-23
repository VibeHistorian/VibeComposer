package org.vibehistorian.vibecomposer.Parts;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.Enums.ArpPattern;

@XmlRootElement(name = "arpPart")
@XmlType(propOrder = {})
public class ArpPart extends InstPart {

	private ArpPattern arpPattern = ArpPattern.RANDOM;
	private int arpPatternRotate = 0;

	public ArpPart() {
		partNum = 3;
	}


	public ArpPattern getArpPattern() {
		return arpPattern;
	}

	public void setArpPattern(ArpPattern arpPattern) {
		this.arpPattern = arpPattern;
	}


	public int getArpPatternRotate() {
		return arpPatternRotate;
	}


	public void setArpPatternRotate(int arpPatternRotate) {
		this.arpPatternRotate = arpPatternRotate;
	}

	public int getPartNum() {
		return 3;
	};
}
