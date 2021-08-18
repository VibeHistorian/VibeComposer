package org.vibehistorian.vibecomposer.Parts;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "melodyPart")
@XmlType(propOrder = {})
public class MelodyPart extends InstPart {

	private boolean fillPauses = false;

	public MelodyPart() {

	}

	public boolean isFillPauses() {
		return fillPauses;
	}

	public void setFillPauses(boolean fillPauses) {
		this.fillPauses = fillPauses;
	}
}
