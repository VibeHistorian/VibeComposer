package org.vibehistorian.vibecomposer.Parts;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "drumParts")
@XmlType(propOrder = {})
public class DrumPartsWrapper {
	List<DrumPart> drumParts = new ArrayList<>();

	public List<DrumPart> getDrumParts() {
		return drumParts;
	}

	public void setDrumParts(List<DrumPart> drumParts) {
		this.drumParts = drumParts;
	}

}
