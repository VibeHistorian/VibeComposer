package org.vibehistorian.midimasterpiece.midigenerator.Parts;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "drumPart")
@XmlType(propOrder = {})
public class DrumPart extends InstPart {
	private int pitch = 36;

	private int swingPercent = 50;

	private boolean isVelocityPattern = true;

	public DrumPart() {

	}


	public int getPitch() {
		return pitch;
	}

	public void setPitch(int pitch) {
		this.pitch = pitch;
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

	public int getSwingPercent() {
		return swingPercent;
	}

	public void setSwingPercent(int swingPercent) {
		this.swingPercent = swingPercent;
	}

}
