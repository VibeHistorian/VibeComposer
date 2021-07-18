package org.vibehistorian.vibecomposer.Parts;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "bassPart")
@XmlType(propOrder = {})
public class BassPart extends InstPart {

	private boolean useRhythm = true;
	private boolean alternatingRhythm = true;
	private boolean doubleOct = false;
	private boolean noteVariation = false;

	public BassPart() {

	}

	public boolean isUseRhythm() {
		return useRhythm;
	}

	public void setUseRhythm(boolean useRhythm) {
		this.useRhythm = useRhythm;
	}

	public boolean isAlternatingRhythm() {
		return alternatingRhythm;
	}

	public void setAlternatingRhythm(boolean alternatingRhythm) {
		this.alternatingRhythm = alternatingRhythm;
	}

	public boolean isDoubleOct() {
		return doubleOct;
	}

	public void setDoubleOct(boolean doubleOct) {
		this.doubleOct = doubleOct;
	}

	public boolean isNoteVariation() {
		return noteVariation;
	}

	public void setNoteVariation(boolean noteVariation) {
		this.noteVariation = noteVariation;
	}
}
