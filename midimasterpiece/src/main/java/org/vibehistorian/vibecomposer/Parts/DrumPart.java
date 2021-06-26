package org.vibehistorian.vibecomposer.Parts;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "drumPart")
@XmlType(propOrder = {})
public class DrumPart extends InstPart {


	private boolean isVelocityPattern = true;
	private List<Integer> customPattern = new ArrayList<>();

	public DrumPart() {

	}


	public boolean isVelocityPattern() {
		return isVelocityPattern;
	}

	public void setVelocityPattern(boolean isVelocityPattern) {
		this.isVelocityPattern = isVelocityPattern;
	}

	@XmlList
	public List<Integer> getCustomPattern() {
		return customPattern;
	}


	public void setCustomPattern(List<Integer> customPattern) {
		this.customPattern = customPattern;
	}


}
