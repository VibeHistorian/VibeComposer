package org.vibehistorian.vibecomposer.Parts;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "drumParts")
@XmlType(propOrder = {})
public class DrumPartsWrapper {

	private boolean useSemitonalMapping = false;

	List<InstPart> drumParts = new ArrayList<>();

	public List<InstPart> getDrumParts() {
		return drumParts;
	}

	public void setDrumParts(List<InstPart> drumParts) {
		this.drumParts = drumParts;
	}

	public boolean isUseSemitonalMapping() {
		return useSemitonalMapping;
	}

	public void setUseSemitonalMapping(boolean useSemitonalMapping) {
		this.useSemitonalMapping = useSemitonalMapping;
	}

}
