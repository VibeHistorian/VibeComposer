package org.vibehistorian.vibecomposer.Parts;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.Enums.ArpPattern;

@XmlRootElement(name = "arpPart")
@XmlType(propOrder = {})
public class ArpPart extends InstPart {

	private ArpPattern arpPattern = ArpPattern.RANDOM;

	public ArpPart() {

	}

	@XmlAttribute
	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public ArpPattern getArpPattern() {
		return arpPattern;
	}

	public void setArpPattern(ArpPattern arpPattern) {
		this.arpPattern = arpPattern;
	}

}
