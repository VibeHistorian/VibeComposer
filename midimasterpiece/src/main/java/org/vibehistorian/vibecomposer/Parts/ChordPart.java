package org.vibehistorian.vibecomposer.Parts;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.InstUtils;
import org.vibehistorian.vibecomposer.Enums.PatternJoinMode;

@XmlRootElement(name = "chordPart")
@XmlType(propOrder = {})
public class ChordPart extends InstPart {
	private int transitionChance = 0;
	private int transitionSplit = 625;

	private int strum = 0;

	private PatternJoinMode patternJoinMode = PatternJoinMode.NOJOIN;
	private int noteLengthMultiplier = 100;

	private InstUtils.POOL instPool = InstUtils.POOL.PLUCK;

	public ChordPart() {

	}


	public int getTransitionChance() {
		return transitionChance;
	}

	public void setTransitionChance(int transitionChance) {
		this.transitionChance = transitionChance;
	}

	public int getTransitionSplit() {
		return transitionSplit;
	}

	public void setTransitionSplit(int transitionSplit) {
		this.transitionSplit = transitionSplit;
	}

	public int getStrum() {
		return strum;
	}

	public void setStrum(int strum) {
		this.strum = strum;
	}


	public InstUtils.POOL getInstPool() {
		return instPool;
	}

	public void setInstPool(InstUtils.POOL instPool) {
		this.instPool = instPool;
	}


	public PatternJoinMode getPatternJoinMode() {
		return patternJoinMode;
	}


	public void setPatternJoinMode(PatternJoinMode patternJoinMode) {
		this.patternJoinMode = patternJoinMode;
	}


	public int getNoteLengthMultiplier() {
		return noteLengthMultiplier;
	}


	public void setNoteLengthMultiplier(int noteLengthMultiplier) {
		this.noteLengthMultiplier = noteLengthMultiplier;
	}

}
