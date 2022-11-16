package org.vibehistorian.vibecomposer.Parts;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.Enums.ArpPattern;

@XmlRootElement(name = "arpPart")
@XmlType(propOrder = {})
public class ArpPart extends InstPart {

	private ArpPattern arpPattern = ArpPattern.RANDOM;
	private int arpPatternRotate = 0;
	private List<Integer> arpPatternCustom = null;
	private List<Integer> arpContour = null;
	private boolean arpContourChordMode = false;

	public ArpPart() {
		partNum = 3;
	}


	public ArpPattern getArpPattern() {
		return arpPattern;
	}

	public void setArpPattern(ArpPattern arpPattern) {
		this.arpPattern = arpPattern;
	}


	public int getArpPatternRotate() {
		return arpPatternRotate;
	}


	public void setArpPatternRotate(int arpPatternRotate) {
		this.arpPatternRotate = arpPatternRotate;
	}

	public int getPartNum() {
		return 3;
	}

	public List<Integer> getArpPatternCustom() {
		return arpPatternCustom;
	}


	public void setArpPatternCustom(List<Integer> arpPatternCustom) {
		this.arpPatternCustom = arpPatternCustom;
	}


	public List<Integer> getArpContour() {
		return arpContour;
	}


	public void setArpContour(List<Integer> arpContour) {
		this.arpContour = arpContour;
	}


	public boolean isArpContourChordMode() {
		return arpContourChordMode;
	}


	public void setArpContourChordMode(boolean arpContourChordMode) {
		this.arpContourChordMode = arpContourChordMode;
	};
}
