/* --------------------
* @author Vibe Historian
* ---------------------

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or any
later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/

package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.Enums.KeyChangeType;
import org.vibehistorian.vibecomposer.Panels.ArpGenSettings;
import org.vibehistorian.vibecomposer.Panels.ChordGenSettings;
import org.vibehistorian.vibecomposer.Panels.DrumGenSettings;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.BassPart;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;

@XmlRootElement(name = "GUIConfig")
@XmlType(propOrder = {})
public class GUIConfig {

	// arrangement
	private Arrangement arrangement = new Arrangement();
	private Arrangement actualArrangement = new Arrangement();
	private int arrangementVariationChance = 30;
	private int arrangementPartVariationChance = 30;
	private boolean scaleMidiVelocityInArrangement = true;
	private boolean arrangementEnabled = false;
	private KeyChangeType keyChangeType = KeyChangeType.PIVOT;

	// macro params
	private ScaleMode scaleMode = ScaleMode.IONIAN;
	private String soundbankName = "MuseScore_General.sf2";
	private int pieceLength = 4;
	private int fixedDuration = 4;
	private int transpose = 0;
	private double bpm = 80;
	private boolean arpAffectsBpm = false;
	private boolean doubledDurations = false;
	private boolean allowChordRepeats = true;
	private Integer globalSwingOverride = null;


	// melody gen
	private int melodyUseOldAlgoChance = 0;
	private boolean firstNoteFromChord = true;
	private boolean firstNoteRandomized = true;
	private int maxMelodySwing = 50;

	private boolean melodyBasicChordsOnly = true;
	private int melodyChordNoteTarget = 40;
	private int melodyTonicNoteTarget = 25;
	private boolean melodyEmphasizeKey = true;
	private int melodyModeNoteTarget = 15;
	private int melodyReplaceAvoidNotes = 2;

	private boolean melodyArpySurprises = false;
	private boolean melodySingleNoteExceptions = false;
	private boolean melodyAvoidChordJumps = false;
	private boolean melodyUseDirectionsFromProgression = true;
	private boolean melodyPatternFlip = false;
	private int melodyBlockTargetMode = 2;

	// chord gen
	private int chordSlashChance = 0;
	private boolean dimAugDom7thEnabled = false;
	private boolean enable9th13th = false;
	private int spiceChance = 15;
	private boolean spiceFlattenBigChords = false;

	private boolean spiceForceScale = true;
	private String firstChord = "?";
	private String lastChord = "?";

	private boolean useChordFormula = false;
	private boolean customChordsEnabled = true;
	private String customChords = "?";
	private String customChordDurations = "2,2,2,2";

	// arp gen
	private boolean useOctaveAdjustments = false;
	private int maxArpSwing = 50;

	// drum gen
	private boolean drumCustomMapping = true;
	private String drumCustomMappingNumbers = StringUtils.join(InstUtils.DRUM_INST_NUMBERS_SEMI,
			",");

	// individual parts

	private BassPart bassPart = new BassPart();

	// tabbed parts
	private List<MelodyPart> melodyParts = new ArrayList<>();
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

	public int getFixedDuration() {
		return fixedDuration;
	}

	public void setFixedDuration(int fixedDuration) {
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

	public boolean isDimAugDom7thEnabled() {
		return dimAugDom7thEnabled;
	}

	public void setDimAugDom7thEnabled(boolean dimAugDom7thEnabled) {
		this.dimAugDom7thEnabled = dimAugDom7thEnabled;
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

	public List<MelodyPart> getMelodyParts() {
		return melodyParts;
	}

	public void setMelodyParts(List<MelodyPart> melodyPart) {
		this.melodyParts = melodyPart;
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

	public Arrangement getActualArrangement() {
		return actualArrangement;
	}

	public void setArrangement(Arrangement arrangement) {
		this.arrangement = arrangement;
	}

	public void setActualArrangement(Arrangement arrangement) {
		this.actualArrangement = arrangement;
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

	public boolean isUseOctaveAdjustments() {
		return useOctaveAdjustments;
	}

	public void setUseOctaveAdjustments(boolean useOctaveAdjustments) {
		this.useOctaveAdjustments = useOctaveAdjustments;
	}

	public int getMaxArpSwing() {
		return maxArpSwing;
	}

	public void setMaxArpSwing(int maxArpSwing) {
		this.maxArpSwing = maxArpSwing;
	}

	public int getMaxMelodySwing() {
		return maxMelodySwing;
	}

	public void setMaxMelodySwing(int maxMelodySwing) {
		this.maxMelodySwing = maxMelodySwing;
	}

	public boolean isDoubledDurations() {
		return doubledDurations;
	}

	public void setDoubledDurations(boolean doubledDurations) {
		this.doubledDurations = doubledDurations;
	}

	public int getArrangementPartVariationChance() {
		return arrangementPartVariationChance;
	}

	public void setArrangementPartVariationChance(int arrangementPartVariationChance) {
		this.arrangementPartVariationChance = arrangementPartVariationChance;
	}

	public boolean isScaleMidiVelocityInArrangement() {
		return scaleMidiVelocityInArrangement;
	}

	public void setScaleMidiVelocityInArrangement(boolean scaleMidiVelocityInArrangement) {
		this.scaleMidiVelocityInArrangement = scaleMidiVelocityInArrangement;
	}

	public boolean isMelodyBasicChordsOnly() {
		return melodyBasicChordsOnly;
	}

	public void setMelodyBasicChordsOnly(boolean melodyBasicChordsOnly) {
		this.melodyBasicChordsOnly = melodyBasicChordsOnly;
	}

	public boolean isAllowChordRepeats() {
		return allowChordRepeats;
	}

	public void setAllowChordRepeats(boolean allowChordRepeats) {
		this.allowChordRepeats = allowChordRepeats;
	}

	public boolean isArrangementEnabled() {
		return arrangementEnabled;
	}

	public void setArrangementEnabled(boolean arrangementEnabled) {
		this.arrangementEnabled = arrangementEnabled;
	}

	public boolean isDrumCustomMapping() {
		return drumCustomMapping;
	}

	public void setDrumCustomMapping(boolean drumCustomMapping) {
		this.drumCustomMapping = drumCustomMapping;
	}

	public boolean isSpiceFlattenBigChords() {
		return spiceFlattenBigChords;
	}

	public void setSpiceFlattenBigChords(boolean spiceFlattenBigChords) {
		this.spiceFlattenBigChords = spiceFlattenBigChords;
	}

	public String getDrumCustomMappingNumbers() {
		return drumCustomMappingNumbers;
	}

	public void setDrumCustomMappingNumbers(String drumCustomMappingNumbers) {
		this.drumCustomMappingNumbers = drumCustomMappingNumbers;
	}

	public KeyChangeType getKeyChangeType() {
		return keyChangeType;
	}

	public void setKeyChangeType(KeyChangeType keyChangeType) {
		this.keyChangeType = keyChangeType;
	}

	public int getMelodyTonicNoteTarget() {
		return melodyTonicNoteTarget;
	}

	public void setMelodyTonicNoteTarget(int melodyTonicNoteTarget) {
		this.melodyTonicNoteTarget = melodyTonicNoteTarget;
	}

	public boolean isMelodyArpySurprises() {
		return melodyArpySurprises;
	}

	public void setMelodyArpySurprises(boolean melodyArpySurprises) {
		this.melodyArpySurprises = melodyArpySurprises;
	}

	public boolean isMelodySingleNoteExceptions() {
		return melodySingleNoteExceptions;
	}

	public void setMelodySingleNoteExceptions(boolean melodySingleNoteExceptions) {
		this.melodySingleNoteExceptions = melodySingleNoteExceptions;
	}

	public boolean isMelodyAvoidChordJumps() {
		return melodyAvoidChordJumps;
	}

	public void setMelodyAvoidChordJumps(boolean melodyAvoidChordJumps) {
		this.melodyAvoidChordJumps = melodyAvoidChordJumps;
	}

	public boolean isMelodyUseDirectionsFromProgression() {
		return melodyUseDirectionsFromProgression;
	}

	public void setMelodyUseDirectionsFromProgression(boolean melodyUseDirectionsFromProgression) {
		this.melodyUseDirectionsFromProgression = melodyUseDirectionsFromProgression;
	}

	public boolean isMelodyPatternFlip() {
		return melodyPatternFlip;
	}

	public void setMelodyPatternFlip(boolean melodyPatternFlip) {
		this.melodyPatternFlip = melodyPatternFlip;
	}

	public boolean isUseChordFormula() {
		return useChordFormula;
	}

	public void setUseChordFormula(boolean useChordFormula) {
		this.useChordFormula = useChordFormula;
	}

	public boolean isSpiceForceScale() {
		return spiceForceScale;
	}

	public void setSpiceForceScale(boolean spiceForceScale) {
		this.spiceForceScale = spiceForceScale;
	}

	public int getMelodyBlockTargetMode() {
		return melodyBlockTargetMode;
	}

	public void setMelodyBlockTargetMode(int melodyBlockTargetMode) {
		this.melodyBlockTargetMode = melodyBlockTargetMode;
	}

	public boolean isMelodyEmphasizeKey() {
		return melodyEmphasizeKey;
	}

	public void setMelodyEmphasizeKey(boolean melodyEmphasizeKey) {
		this.melodyEmphasizeKey = melodyEmphasizeKey;
	}

	public int getMelodyModeNoteTarget() {
		return melodyModeNoteTarget;
	}

	public void setMelodyModeNoteTarget(int melodyModeNoteTarget) {
		this.melodyModeNoteTarget = melodyModeNoteTarget;
	}

	public int getMelodyReplaceAvoidNotes() {
		return melodyReplaceAvoidNotes;
	}

	public void setMelodyReplaceAvoidNotes(int melodyReplaceAvoidNotes) {
		this.melodyReplaceAvoidNotes = melodyReplaceAvoidNotes;
	}

	public int getMelodyChordNoteTarget() {
		return melodyChordNoteTarget;
	}

	public void setMelodyChordNoteTarget(int melodyChordNoteTarget) {
		this.melodyChordNoteTarget = melodyChordNoteTarget;
	}

	public Integer getGlobalSwingOverride() {
		return globalSwingOverride;
	}

	public void setGlobalSwingOverride(Integer globalSwingOverride) {
		this.globalSwingOverride = globalSwingOverride;
	}

}
