package org.vibehistorian.vibecomposer.Parts;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "melodyPart")
@XmlType(propOrder = {})
public class MelodyPart extends InstPart {
	public MelodyPart() {

	}

	@XmlAttribute
	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}
}
