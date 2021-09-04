package org.vibehistorian.vibecomposer.Parts;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "melodyPart")
@XmlType(propOrder = {})
public class MelodyPart extends InstPart {

	private boolean fillPauses = false;
	private List<Integer> chordNoteChoices = null;
	private List<Integer> melodyPatternOffsets = null;

	public MelodyPart() {

	}

	public boolean isFillPauses() {
		return fillPauses;
	}

	public void setFillPauses(boolean fillPauses) {
		this.fillPauses = fillPauses;
	}

	public List<Integer> getChordNoteChoices() {
		return chordNoteChoices;
	}

	public void setChordNoteChoices(List<Integer> chordNoteChoices) {
		this.chordNoteChoices = chordNoteChoices;
	}

	public List<Integer> getMelodyPatternOffsets() {
		return melodyPatternOffsets;
	}

	public void setMelodyPatternOffsets(List<Integer> melodyPatternOffsets) {
		this.melodyPatternOffsets = melodyPatternOffsets;
	}
}
