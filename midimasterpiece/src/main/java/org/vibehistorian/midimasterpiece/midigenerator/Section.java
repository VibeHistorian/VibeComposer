package org.vibehistorian.midimasterpiece.midigenerator;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import jm.music.data.CPhrase;
import jm.music.data.Phrase;

@XmlRootElement(name = "section")
@XmlType(propOrder = {})
public class Section {
	public enum SectionType {
		INTRO, VERSE1, VERSE2, CHORUS1, BREAKDOWN, CHILL, VERSE3, BUILDUP, CHORUS2, ADVANCED_CHORUS,
		OUTRO;
	}

	public static final int VARIATION_CHANCE = 30;

	private String type;
	private int measures;
	private boolean useVariation = false;

	private double startTime;

	private int melodyChance = 50;
	private int bassChance = 50;
	private int chordChance = 50;
	private int arpChance = 50;
	private int drumChance = 50;

	// data (transient)
	private Phrase melody;
	private CPhrase bass;
	private List<CPhrase> chords;
	private List<CPhrase> arps;
	private List<Phrase> drums;
	private CPhrase chordSlash;

	// display data (transient)
	private List<Integer> melodyPresence = new ArrayList<>();
	private List<Integer> bassPresence = new ArrayList<>();
	private List<Integer> chordPresence = new ArrayList<>();
	private List<Integer> arpPresence = new ArrayList<>();
	private List<Integer> drumPresence = new ArrayList<>();

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

	public Section(Section orig) {
		super();
		this.type = orig.type;
		this.measures = orig.measures;
		this.melodyChance = orig.melodyChance;
		this.bassChance = orig.bassChance;
		this.chordChance = orig.chordChance;
		this.arpChance = orig.arpChance;
		this.drumChance = orig.drumChance;
	}

	@XmlAttribute
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

	@XmlTransient
	public void setMelody(Phrase melody) {
		this.melody = melody;
	}

	public CPhrase getBass() {
		return bass;
	}

	@XmlTransient
	public void setBass(CPhrase bass) {
		this.bass = bass;
	}

	public List<CPhrase> getChords() {
		return chords;
	}

	@XmlTransient
	public void setChords(List<CPhrase> chords) {
		this.chords = chords;
	}

	public List<CPhrase> getArps() {
		return arps;
	}

	@XmlTransient
	public void setArps(List<CPhrase> arps) {
		this.arps = arps;
	}

	public List<Phrase> getDrums() {
		return drums;
	}

	@XmlTransient
	public void setDrums(List<Phrase> drums) {
		this.drums = drums;
	}

	public CPhrase getChordSlash() {
		return chordSlash;
	}

	@XmlTransient
	public void setChordSlash(CPhrase chordSlash) {
		this.chordSlash = chordSlash;
	}

	public double getStartTime() {
		return startTime;
	}

	public void setStartTime(double startTime) {
		this.startTime = startTime;
	}

	public Section deepCopy() {
		Section sec = new Section(type, measures, melodyChance, bassChance, chordChance, arpChance,
				drumChance);
		return sec;
	}

	public boolean isUseVariation() {
		return useVariation;
	}

	public void setUseVariation(boolean useVariation) {
		this.useVariation = useVariation;
	}

	public List<Integer> getMelodyPresence() {
		return melodyPresence;
	}

	@XmlTransient
	public void setMelodyPresence(List<Integer> melodyPresence) {
		this.melodyPresence = melodyPresence;
	}

	public List<Integer> getBassPresence() {
		return bassPresence;
	}

	@XmlTransient
	public void setBassPresence(List<Integer> bassPresence) {
		this.bassPresence = bassPresence;
	}

	public List<Integer> getChordPresence() {
		return chordPresence;
	}

	@XmlTransient
	public void setChordPresence(List<Integer> chordPresence) {
		this.chordPresence = chordPresence;
	}

	public List<Integer> getArpPresence() {
		return arpPresence;
	}

	@XmlTransient
	public void setArpPresence(List<Integer> arpPresence) {
		this.arpPresence = arpPresence;
	}

	public List<Integer> getDrumPresence() {
		return drumPresence;
	}

	@XmlTransient
	public void setDrumPresence(List<Integer> drumPresence) {
		this.drumPresence = drumPresence;
	}

}
