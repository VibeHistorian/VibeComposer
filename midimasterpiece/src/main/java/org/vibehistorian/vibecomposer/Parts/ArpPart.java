package org.vibehistorian.vibecomposer.Parts;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.Enums.ArpPattern;

@XmlRootElement(name = "arpPart")
@XmlType(propOrder = {})
public class ArpPart extends InstPart {

	private ArpPattern arpPattern = ArpPattern.RANDOM;

	public ArpPart() {

	}


	public ArpPattern getArpPattern() {
		return arpPattern;
	}

	public void setArpPattern(ArpPattern arpPattern) {
		this.arpPattern = arpPattern;
	}

}
