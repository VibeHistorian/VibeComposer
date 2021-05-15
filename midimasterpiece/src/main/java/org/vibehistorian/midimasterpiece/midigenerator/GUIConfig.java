package org.vibehistorian.midimasterpiece.midigenerator;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils.ScaleMode;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.ArpGenSettings;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.ChordGenSettings;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.DrumGenSettings;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.ArpPart;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.BassPart;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.ChordPart;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.DrumPart;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.MelodyPart;

@XmlRootElement(name = "GUIConfig")
@XmlType(propOrder = {})
public class GUIConfig {

	// arrangement
	private Arrangement arrangement = new Arrangement();
	private int arrangementVariationChance = 30;

	// macro params
	private ScaleMode scaleMode = ScaleMode.IONIAN;
	private String soundbankName = "MuseScore_General.sf2";
	private int pieceLength = 4;
	private boolean fixedDuration = true;
	private int transpose = 0;
	private double bpm = 80;
	private boolean arpAffectsBpm = true;

	// melody gen
	private int maxNoteJump = 2;
	private int maxExceptions = 2;
	private int melodyAlternateRhythmChance = 50;
	private int melodySameRhythmChance = 20;
	private int melodyUseOldAlgoChance = 20;
	private boolean firstNoteFromChord = true;
	private boolean firstNoteRandomized = true;

	// chord gen
	private boolean dimAugEnabled = false;
	private boolean enable9th13th = true;
	private int spiceChance = 8;
	private int chordSlashChance = 0;
	private String firstChord = "R";
	private String lastChord = "R";
	private boolean customChordsEnabled = true;
	private String customChords = "R";
	private String customChordDurations = "2,2,2,2";

	// individual parts
	private MelodyPart melodyPart = new MelodyPart();
	private BassPart bassPart = new BassPart();

	// tabbed parts
	private List<ChordPart> chordParts = new ArrayList<>();
	private List<DrumPart> drumParts = new ArrayList<>();
	private List<ArpPart> arpParts = new ArrayList<>();
	private boolean chordsEnable = false;
	private boolean arpsEnable = true;
	private boolean drumsEnable = false;
	private ChordGenSettings chordGenSettings = new ChordGenSettings();
	private DrumGenSettings drumGenSettings = new DrumGenSettings();
	private ArpGenSettings arpGenSettings = new ArpGenSettings();

	// seed
	private long randomSeed = 0;

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

	public boolean isChordsEnable() {
		return chordsEnable;
	}

	public void setChordsEnable(boolean chordsEnable) {
		this.chordsEnable = chordsEnable;
	}

	public boolean isArpsEnable() {
		return arpsEnable;
	}

	public void setArpsEnable(boolean arpsEnable) {
		this.arpsEnable = arpsEnable;
	}

	public boolean isDrumsEnable() {
		return drumsEnable;
	}

	public void setDrumsEnable(boolean drumsEnable) {
		this.drumsEnable = drumsEnable;
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

	public int getChordSlashChance() {
		return chordSlashChance;
	}

	public void setChordSlashChance(int chordSlashChance) {
		this.chordSlashChance = chordSlashChance;
	}

	public List<ChordPart> getChordParts() {
		return chordParts;
	}

	public void setChordParts(List<ChordPart> chordParts) {
		this.chordParts = chordParts;
	}

	public ChordGenSettings getChordGenSettings() {
		return chordGenSettings;
	}

	public void setChordGenSettings(ChordGenSettings chordGenSettings) {
		this.chordGenSettings = chordGenSettings;
	}

	public List<ArpPart> getArpParts() {
		return arpParts;
	}

	public void setArpParts(List<ArpPart> arpParts) {
		this.arpParts = arpParts;
	}

	public DrumGenSettings getDrumGenSettings() {
		return drumGenSettings;
	}

	public void setDrumGenSettings(DrumGenSettings drumGenSettings) {
		this.drumGenSettings = drumGenSettings;
	}

	public ArpGenSettings getArpGenSettings() {
		return arpGenSettings;
	}

	public void setArpGenSettings(ArpGenSettings arpGenSettings) {
		this.arpGenSettings = arpGenSettings;
	}

	public MelodyPart getMelodyPart() {
		return melodyPart;
	}

	public void setMelodyPart(MelodyPart melodyPart) {
		this.melodyPart = melodyPart;
	}

	public BassPart getBassPart() {
		return bassPart;
	}

	public void setBassPart(BassPart bassPart) {
		this.bassPart = bassPart;
	}

	public Arrangement getArrangement() {
		return arrangement;
	}

	public void setArrangement(Arrangement arrangement) {
		this.arrangement = arrangement;
	}

	public ScaleMode getScaleMode() {
		return scaleMode;
	}

	public void setScaleMode(ScaleMode scaleMode) {
		this.scaleMode = scaleMode;
	}

	public boolean isEnable9th13th() {
		return enable9th13th;
	}

	public void setEnable9th13th(boolean enable9th13th) {
		this.enable9th13th = enable9th13th;
	}

	public int getMelodyAlternateRhythmChance() {
		return melodyAlternateRhythmChance;
	}

	public void setMelodyAlternateRhythmChance(int melodyAlternateRhythmChance) {
		this.melodyAlternateRhythmChance = melodyAlternateRhythmChance;
	}

	public int getMelodySameRhythmChance() {
		return melodySameRhythmChance;
	}

	public void setMelodySameRhythmChance(int melodySameRhythmChance) {
		this.melodySameRhythmChance = melodySameRhythmChance;
	}

	public int getMelodyUseOldAlgoChance() {
		return melodyUseOldAlgoChance;
	}

	public void setMelodyUseOldAlgoChance(int melodyUseOldAlgoChance) {
		this.melodyUseOldAlgoChance = melodyUseOldAlgoChance;
	}

	public int getArrangementVariationChance() {
		return arrangementVariationChance;
	}

	public void setArrangementVariationChance(int arrangementVariationChance) {
		this.arrangementVariationChance = arrangementVariationChance;
	}


}
