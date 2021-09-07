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
	private int maxBlockChange = 7;
	private int leadChordsChance = 0;
	private int blockJump = 0;
	private int maxNoteExceptions = 2;
	private int alternatingRhythmChance = 50;
	private int doubledRhythmChance = 50;
	private int splitChance = 0;
	private int noteExceptionChance = 33;
	private int speed = 0;

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

	public int getMaxBlockChange() {
		return maxBlockChange;
	}

	public void setMaxBlockChange(int maxBlockChange) {
		this.maxBlockChange = maxBlockChange;
	}

	public int getLeadChordsChance() {
		return leadChordsChance;
	}

	public void setLeadChordsChance(int leadChordsChance) {
		this.leadChordsChance = leadChordsChance;
	}

	public int getBlockJump() {
		return blockJump;
	}

	public void setBlockJump(int blockJump) {
		this.blockJump = blockJump;
	}

	public int getMaxNoteExceptions() {
		return maxNoteExceptions;
	}

	public void setMaxNoteExceptions(int maxNoteExceptions) {
		this.maxNoteExceptions = maxNoteExceptions;
	}

	public int getAlternatingRhythmChance() {
		return alternatingRhythmChance;
	}

	public void setAlternatingRhythmChance(int alternatingRhythmChance) {
		this.alternatingRhythmChance = alternatingRhythmChance;
	}

	public int getDoubledRhythmChance() {
		return doubledRhythmChance;
	}

	public void setDoubledRhythmChance(int doubledRhythmChance) {
		this.doubledRhythmChance = doubledRhythmChance;
	}

	public int getSplitChance() {
		return splitChance;
	}

	public void setSplitChance(int splitChance) {
		this.splitChance = splitChance;
	}

	public int getNoteExceptionChance() {
		return noteExceptionChance;
	}

	public void setNoteExceptionChance(int noteExceptionChance) {
		this.noteExceptionChance = noteExceptionChance;
	}

	public int getSpeed() {
		return speed;
	}

	public void setSpeed(int speed) {
		this.speed = speed;
	}

}
