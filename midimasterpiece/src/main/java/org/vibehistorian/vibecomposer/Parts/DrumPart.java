package org.vibehistorian.vibecomposer.Parts;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "drumPart")
@XmlType(propOrder = {})
public class DrumPart extends InstPart {


	private boolean isVelocityPattern = true;

	public DrumPart() {

	}


	public boolean isVelocityPattern() {
		return isVelocityPattern;
	}

	public void setVelocityPattern(boolean isVelocityPattern) {
		this.isVelocityPattern = isVelocityPattern;
	}

	@XmlAttribute
	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}


}
