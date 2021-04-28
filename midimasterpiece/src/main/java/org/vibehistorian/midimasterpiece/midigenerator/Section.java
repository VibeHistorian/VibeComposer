package org.vibehistorian.midimasterpiece.midigenerator;

import java.util.List;

import jm.music.data.CPhrase;
import jm.music.data.Phrase;

public class Section {
	public enum SectionType {
		INTRO, VERSE1, VERSE2, CHORUS1, BREAKDOWN, CHILL, VERSE3, BUILDUP, CHORUS2, ADVANCED_CHORUS,
		OUTRO;
	}

	private String type;
	private int measures;

	private double startTime;

	private int melodyChance = 50;
	private int bassChance = 50;
	private int chordChance = 50;
	private int arpChance = 50;
	private int drumChance = 50;

	private Phrase melody;
	private CPhrase bass;
	private List<CPhrase> chords;
	private List<CPhrase> arps;
	private List<Phrase> drums;
	private CPhrase chordSlash;

	public Section() {

	}

	public Section(String type, int measures, int melodyChance, int bassChance, int chordChance,
			int arpChance, int drumChance) {
		super();
		this.type = type;
		this.measures = measures;
		this.melodyChance = melodyChance;
		this.bassChance = bassChance;
		this.chordChance = chordChance;
		this.arpChance = arpChance;
		this.drumChance = drumChance;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public int getMeasures() {
		return measures;
	}

	public void setMeasures(int measures) {
		this.measures = measures;
	}

	public int getMelodyChance() {
		return melodyChance;
	}

	public void setMelodyChance(int melodyChance) {
		this.melodyChance = melodyChance;
	}

	public int getBassChance() {
		return bassChance;
	}

	public void setBassChance(int bassChance) {
		this.bassChance = bassChance;
	}

	public int getChordChance() {
		return chordChance;
	}

	public void setChordChance(int chordChance) {
		this.chordChance = chordChance;
	}

	public int getArpChance() {
		return arpChance;
	}

	public void setArpChance(int arpChance) {
		this.arpChance = arpChance;
	}

	public int getDrumChance() {
		return drumChance;
	}

	public void setDrumChance(int drumChance) {
		this.drumChance = drumChance;
	}

	public Phrase getMelody() {
		return melody;
	}

	public void setMelody(Phrase melody) {
		this.melody = melody;
	}

	public CPhrase getBass() {
		return bass;
	}

	public void setBass(CPhrase bass) {
		this.bass = bass;
	}

	public List<CPhrase> getChords() {
		return chords;
	}

	public void setChords(List<CPhrase> chords) {
		this.chords = chords;
	}

	public List<CPhrase> getArps() {
		return arps;
	}

	public void setArps(List<CPhrase> arps) {
		this.arps = arps;
	}

	public List<Phrase> getDrums() {
		return drums;
	}

	public void setDrums(List<Phrase> drums) {
		this.drums = drums;
	}

	public CPhrase getChordSlash() {
		return chordSlash;
	}

	public void setChordSlash(CPhrase chordSlash) {
		this.chordSlash = chordSlash;
	}

	public double getStartTime() {
		return startTime;
	}

	public void setStartTime(double startTime) {
		this.startTime = startTime;
	}


}
