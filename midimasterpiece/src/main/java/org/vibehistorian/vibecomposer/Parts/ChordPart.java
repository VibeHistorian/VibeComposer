package org.vibehistorian.vibecomposer.Parts;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.MidiUtils.POOL;

@XmlRootElement(name = "chordPart")
@XmlType(propOrder = {})
public class ChordPart extends InstPart {
	private int transitionChance = 0;
	private int transitionSplit = 625;

	private int strum = 0;


	private POOL instPool = POOL.PLUCK;

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


	public POOL getInstPool() {
		return instPool;
	}

	public void setInstPool(POOL instPool) {
		this.instPool = instPool;
	}

}
