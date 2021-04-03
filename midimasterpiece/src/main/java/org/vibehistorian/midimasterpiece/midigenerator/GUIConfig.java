package org.vibehistorian.midimasterpiece.midigenerator;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "GUIConfig")
@XmlType(propOrder = {})
public class GUIConfig {
	/*
	 * soundbank, piece length, fixed duration CB, minor CB,
	 * melody/c1/c2/arp/bass/drums CB and inst choice, arp#, random#, 
	 * random pattern, repeatable, pauses CBs
	 * transpose, bpm
	 * max note jump, note#1 from chord, randomized, pause%, spice%, dim/aug
	 * first/last c, CUSTOM CHORDS, chord durations
	 * random seed
	 */
	
	public GUIConfig() {
		
	}
	
	public String getSoundbankName() {
		return soundbankName;
	}
	
	public void setSoundbankName(String soundbankName) {
		this.soundbankName = soundbankName;
	}
	
	public int getPieceLength() {
		return pieceLength;
	}
	
	public void setPieceLength(int pieceLength) {
		this.pieceLength = pieceLength;
	}
	
	public boolean isFixedDuration() {
		return fixedDuration;
	}
	
	public void setFixedDuration(boolean fixedDuration) {
		this.fixedDuration = fixedDuration;
	}
	
	public boolean isMinor() {
		return minor;
	}
	
	public void setMinor(boolean minor) {
		this.minor = minor;
	}
	
	public boolean isMelodyEnable() {
		return melodyEnable;
	}
	
	public void setMelodyEnable(boolean melodyEnable) {
		this.melodyEnable = melodyEnable;
	}
	
	public boolean isChords1Enable() {
		return chords1Enable;
	}
	
	public void setChords1Enable(boolean chords1Enable) {
		this.chords1Enable = chords1Enable;
	}
	
	public boolean isChords2Enable() {
		return chords2Enable;
	}
	
	public void setChords2Enable(boolean chords2Enable) {
		this.chords2Enable = chords2Enable;
	}
	
	public boolean isChords3ArpEnable() {
		return chords3ArpEnable;
	}
	
	public void setChords3ArpEnable(boolean chords3ArpEnable) {
		this.chords3ArpEnable = chords3ArpEnable;
	}
	
	public boolean isBassRootsEnable() {
		return bassRootsEnable;
	}
	
	public void setBassRootsEnable(boolean bassRootsEnable) {
		this.bassRootsEnable = bassRootsEnable;
	}
	
	public int getMelodyInst() {
		return melodyInst;
	}
	
	public void setMelodyInst(int melodyInst) {
		this.melodyInst = melodyInst;
	}
	
	public int getChords1Inst() {
		return chords1Inst;
	}
	
	public void setChords1Inst(int chords1Inst) {
		this.chords1Inst = chords1Inst;
	}
	
	public int getChords2Inst() {
		return chords2Inst;
	}
	
	public void setChords2Inst(int chords2Inst) {
		this.chords2Inst = chords2Inst;
	}
	
	public int getChords3ArpInst() {
		return chords3ArpInst;
	}
	
	public void setChords3ArpInst(int chords3ArpInst) {
		this.chords3ArpInst = chords3ArpInst;
	}
	
	public int getBassRootsInst() {
		return bassRootsInst;
	}
	
	public void setBassRootsInst(int bassRootsInst) {
		this.bassRootsInst = bassRootsInst;
	}
	
	public boolean isDrumsEnable() {
		return drumsEnable;
	}
	
	public void setDrumsEnable(boolean drumsEnable) {
		this.drumsEnable = drumsEnable;
	}
	
	public boolean isArpRandomCount() {
		return arpRandomCount;
	}
	
	public void setArpRandomCount(boolean arpRandomCount) {
		this.arpRandomCount = arpRandomCount;
	}
	
	public boolean isArpRandomPattern() {
		return arpRandomPattern;
	}
	
	public void setArpRandomPattern(boolean arpRandomPattern) {
		this.arpRandomPattern = arpRandomPattern;
	}
	
	public boolean isArpRandomRepeats() {
		return arpRandomRepeats;
	}
	
	public void setArpRandomRepeats(boolean arpRandomRepeats) {
		this.arpRandomRepeats = arpRandomRepeats;
	}
	
	public boolean isArpRandomPauses() {
		return arpRandomPauses;
	}
	
	public void setArpRandomPauses(boolean arpRandomPauses) {
		this.arpRandomPauses = arpRandomPauses;
	}
	
	public int getTranspose() {
		return transpose;
	}
	
	public void setTranspose(int transpose) {
		this.transpose = transpose;
	}
	
	public double getBpm() {
		return bpm;
	}
	
	public void setBpm(double bpm) {
		this.bpm = bpm;
	}
	
	public int getMaxNoteJump() {
		return maxNoteJump;
	}
	
	public void setMaxNoteJump(int maxNoteJump) {
		this.maxNoteJump = maxNoteJump;
	}
	
	public int getPauseChance() {
		return pauseChance;
	}
	
	public void setPauseChance(int pauseChance) {
		this.pauseChance = pauseChance;
	}
	
	public int getSpiceChance() {
		return spiceChance;
	}
	
	public void setSpiceChance(int spiceChance) {
		this.spiceChance = spiceChance;
	}
	
	public boolean isFirstNoteFromChord() {
		return firstNoteFromChord;
	}
	
	public void setFirstNoteFromChord(boolean firstNoteFromChord) {
		this.firstNoteFromChord = firstNoteFromChord;
	}
	
	public boolean isFirstNoteRandomized() {
		return firstNoteRandomized;
	}
	
	public void setFirstNoteRandomized(boolean firstNoteRandomized) {
		this.firstNoteRandomized = firstNoteRandomized;
	}
	
	public boolean isDimAugEnabled() {
		return dimAugEnabled;
	}
	
	public void setDimAugEnabled(boolean dimAugEnabled) {
		this.dimAugEnabled = dimAugEnabled;
	}
	
	public String getFirstChord() {
		return firstChord;
	}
	
	public void setFirstChord(String firstChord) {
		this.firstChord = firstChord;
	}
	
	public String getLastChord() {
		return lastChord;
	}
	
	public void setLastChord(String lastChord) {
		this.lastChord = lastChord;
	}
	
	public String getCustomChords() {
		return customChords;
	}
	
	public void setCustomChords(String customChords) {
		this.customChords = customChords;
	}
	
	public long getRandomSeed() {
		return randomSeed;
	}
	
	@XmlAttribute
	public void setRandomSeed(long randomSeed) {
		this.randomSeed = randomSeed;
	}
	
	private String soundbankName = "MuseScore_General.sf2";
	
	private int pieceLength = 4;
	
	private boolean fixedDuration = true;
	
	private boolean minor = false;
	
	private boolean melodyEnable = true;
	private boolean chords1Enable = false;
	private boolean chords2Enable = false;
	private boolean chords3ArpEnable = true;
	private boolean chords4ArpEnable = true;
	private boolean bassRootsEnable = true;
	
	private int melodyInst = 46;
	private int chords1Inst = 107;
	private int chords2Inst = 95;
	private int chords3ArpInst = 46;
	private int chords4ArpInst = 4;
	private int bassRootsInst = 33;
	
	private boolean drumsEnable = false;
	
	private int secondArpMultiplier = 4;
	private int secondArpOctaveAdjust = 0;
	private int arpCustomCount = 3;
	private boolean arpRandomCount = true;
	private boolean arpRandomPattern = true;
	private boolean arpRandomRepeats = true;
	private boolean arpRandomPauses = true;
	
	private int transpose = 0;
	private double bpm = 80;
	private boolean arpAffectsBpm = true;
	
	private int maxNoteJump = 4;
	private int maxExceptions = 1;
	
	private int pauseChance = 25;
	private int melodyPauseChance = 20;
	private int secondArpPauseChance = 50;
	private int spiceChance = 8;
	private int chordTransitionChance = 0;
	private int chordFlam = 0;
	
	private List<DrumPart> drumParts = new ArrayList<>();
	private boolean drumPatternAffectsVelocity = true;
	
	private boolean firstNoteFromChord = true;
	private boolean firstNoteRandomized = true;
	private boolean dimAugEnabled = false;
	
	private String firstChord = "R";
	private String lastChord = "R";
	
	private boolean customChordsEnabled = true;
	private String customChords = "R";
	private String customChordDurations = "2,2,2,2";
	
	
	private long randomSeed = 0;
	private long userMelodySeed = 0;
	
	//start-transients
	private transient boolean lockMelody = false;
	private transient boolean lockChords1 = true;
	private transient boolean lockChords2 = true;
	private transient boolean lockChords3Arp = false;
	private transient boolean lockChords4Arp = false;
	private transient boolean lockBassRoots = false;
	
	
	public boolean isLockChords4Arp() {
		return lockChords4Arp;
	}
	
	public void setLockChords4Arp(boolean lockChords4Arp) {
		this.lockChords4Arp = lockChords4Arp;
	}
	
	
	public boolean isLockMelody() {
		return lockMelody;
	}
	
	@XmlTransient
	public void setLockMelody(boolean lockMelody) {
		this.lockMelody = lockMelody;
	}
	
	public boolean isLockChords1() {
		return lockChords1;
	}
	
	@XmlTransient
	public void setLockChords1(boolean lockChords1) {
		this.lockChords1 = lockChords1;
	}
	
	public boolean isLockChords2() {
		return lockChords2;
	}
	
	@XmlTransient
	public void setLockChords2(boolean lockChords2) {
		this.lockChords2 = lockChords2;
	}
	
	public boolean isLockChords3Arp() {
		return lockChords3Arp;
	}
	
	@XmlTransient
	public void setLockChords3Arp(boolean lockChords3Arp) {
		this.lockChords3Arp = lockChords3Arp;
	}
	
	public boolean isLockBassRoots() {
		return lockBassRoots;
	}
	
	@XmlTransient
	public void setLockBassRoots(boolean lockBassRoots) {
		this.lockBassRoots = lockBassRoots;
	}
	//end-transients
	
	public boolean isChords4ArpEnable() {
		return chords4ArpEnable;
	}
	
	public void setChords4ArpEnable(boolean chords4ArpEnable) {
		this.chords4ArpEnable = chords4ArpEnable;
	}
	
	public int getChords4ArpInst() {
		return chords4ArpInst;
	}
	
	public void setChords4ArpInst(int chords4ArpInst) {
		this.chords4ArpInst = chords4ArpInst;
	}
	
	public String getCustomChordDurations() {
		return customChordDurations;
	}
	
	public void setCustomChordDurations(String customChordDurations) {
		this.customChordDurations = customChordDurations;
	}
	
	public boolean isCustomChordsEnabled() {
		return customChordsEnabled;
	}
	
	public void setCustomChordsEnabled(boolean customChordsEnabled) {
		this.customChordsEnabled = customChordsEnabled;
	}
	
	public int getArpCustomCount() {
		return arpCustomCount;
	}
	
	public void setArpCustomCount(int arpCustomCount) {
		this.arpCustomCount = arpCustomCount;
	}
	
	public long getUserMelodySeed() {
		return userMelodySeed;
	}
	
	public void setUserMelodySeed(long userMelodySeed) {
		this.userMelodySeed = userMelodySeed;
	}
	
	public int getMelodyPauseChance() {
		return melodyPauseChance;
	}
	
	public void setMelodyPauseChance(int melodyPauseChance) {
		this.melodyPauseChance = melodyPauseChance;
	}
	
	public int getSecondArpMultiplier() {
		return secondArpMultiplier;
	}
	
	public void setSecondArpMultiplier(int secondArpMultiplier) {
		this.secondArpMultiplier = secondArpMultiplier;
	}
	
	public int getSecondArpOctaveAdjust() {
		return secondArpOctaveAdjust;
	}
	
	public void setSecondArpOctaveAdjust(int secondArpOctaveAdjust) {
		this.secondArpOctaveAdjust = secondArpOctaveAdjust;
	}
	
	public int getSecondArpPauseChance() {
		return secondArpPauseChance;
	}
	
	public void setSecondArpPauseChance(int secondArpPauseChance) {
		this.secondArpPauseChance = secondArpPauseChance;
	}
	
	public boolean isArpAffectsBpm() {
		return arpAffectsBpm;
	}
	
	public void setArpAffectsBpm(boolean arpAffectBpm) {
		this.arpAffectsBpm = arpAffectBpm;
	}
	
	public int getMaxExceptions() {
		return maxExceptions;
	}
	
	public void setMaxExceptions(int maxExceptions) {
		this.maxExceptions = maxExceptions;
	}
	
	public List<DrumPart> getDrumParts() {
		return drumParts;
	}
	
	public void setDrumParts(List<DrumPart> drumParts) {
		this.drumParts = drumParts;
	}
	
	public boolean isDrumPatternAffectsVelocity() {
		return drumPatternAffectsVelocity;
	}
	
	public void setDrumPatternAffectsVelocity(boolean drumPatternAffectsVelocity) {
		this.drumPatternAffectsVelocity = drumPatternAffectsVelocity;
	}

	public int getChordTransitionChance() {
		return chordTransitionChance;
	}

	public void setChordTransitionChance(int chordTransitionChance) {
		this.chordTransitionChance = chordTransitionChance;
	}

	public int getChordFlam() {
		return chordFlam;
	}

	public void setChordFlam(int chordFlam) {
		this.chordFlam = chordFlam;
	}
	
}
