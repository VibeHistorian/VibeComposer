package org.vibehistorian.vibecomposer.Parts;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "drumPart")
@XmlType(propOrder = {})
public class DrumPart extends InstPart {


	private boolean isVelocityPattern = true;

	public DrumPart() {
		partNum = 4;
	}


	public boolean isVelocityPattern() {
		return isVelocityPattern;
	}

	public void setVelocityPattern(boolean isVelocityPattern) {
		this.isVelocityPattern = isVelocityPattern;
	}

	public int getPartNum() {
		return 4;
	};
}
