package org.vibehistorian.vibecomposer.Parts;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.Enums.PatternJoinMode;

@XmlRootElement(name = "bassPart")
@XmlType(propOrder = {})
public class BassPart extends InstPart {

	private boolean useRhythm = true;
	private boolean alternatingRhythm = true;
	private boolean doubleOct = false;
	private int noteVariation = 20;
	private boolean melodyPattern = false;
	private PatternJoinMode patternJoinMode = PatternJoinMode.EXPAND;

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

	public int getNoteVariation() {
		return noteVariation;
	}

	public void setNoteVariation(int noteVariation) {
		this.noteVariation = noteVariation;
	}

	public boolean isMelodyPattern() {
		return melodyPattern;
	}

	public void setMelodyPattern(boolean melodyPattern) {
		this.melodyPattern = melodyPattern;
	}

	public PatternJoinMode getPatternJoinMode() {
		return patternJoinMode;
	}

	public void setPatternJoinMode(PatternJoinMode patternJoinMode) {
		this.patternJoinMode = patternJoinMode;
	}
}
