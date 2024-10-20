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
along with this program; if not,
see <https://www.gnu.org/licenses/>.
*/

package org.vibehistorian.vibecomposer;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.Enums.KeyChangeType;
import org.vibehistorian.vibecomposer.Helpers.PatternMap;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Helpers.UsedPattern;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.Panels.ArpGenSettings;
import org.vibehistorian.vibecomposer.Panels.ChordGenSettings;
import org.vibehistorian.vibecomposer.Panels.DrumGenSettings;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.BassPart;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "GUIConfig")
@XmlType(propOrder = {})
public class GUIConfig {

	public GUIConfig() {
	}

	private String version = VibeComposerGUI.CURRENT_VERSION;

	private PhraseNotes melodyNotes = null;

	// pattern map
	private List<PatternMap> patternMaps = new ArrayList<>();

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
	private Integer beatDurationMultiplierIndex = 1;
	private Integer swingUnitMultiplierIndex = 1;
	private boolean allowChordRepeats = true;
	private Integer globalSwingOverride = null;
	private boolean customMidiForceScale = false;
	private boolean transposedNotesForceScale = false;
	private int humanizeDrums = 20;
	private int humanizeNotes = 150;


	// melody gen
	private boolean combineMelodyTracks = false;

	private int melodyUseOldAlgoChance = 0;
	private boolean firstNoteFromChord = true;
	private boolean firstNoteRandomized = true;
	private int maxMelodySwing = 50;

	private boolean melodyBasicChordsOnly = true;
	private int melodyChordNoteTarget = 40;
	private int melodyTonicNoteTarget = 25;
	private boolean melodyEmphasizeKey = true;
	private int melodyModeNoteTarget = 15;
	private int melodyReplaceAvoidNotes = 1;
	private int melodyMaxDirChanges = 2;
	private int melodyTargetNoteVariation = 4;

	private boolean melodyArpySurprises = false;
	private boolean melodySingleNoteExceptions = false;
	private boolean melodyFillPausesPerChord = false;
	private int melodyNewBlocksChance = 0;
	private boolean melodyLegacyMode = false;
	private List<Integer> melodyBlockChoicePreference = new ArrayList<>();

	private boolean melodyAvoidChordJumps = false;
	private boolean melodyUseDirectionsFromProgression = true;
	private boolean melodyPatternFlip = false;
	private int melodyBlockTargetMode = 2;
	private int melodyPatternEffect = 2;
	private int melodyRhythmAccents = 0;
	private int melodyRhythmAccentsMode = 0;
	private boolean melodyRhythmAccentsPocket = false;

	// chord gen
	private int chordSlashChance = 0;
	private boolean dimAug6thEnabled = false;
	private boolean enable9th13th = false;
	private int spiceChance = 15;
	private boolean spiceFlattenBigChords = false;
	private boolean squishProgressively = false;
	private int spiceParallelChance = 5;

	private boolean spiceForceScale = true;
	private String firstChord = "?";
	private String lastChord = "?";

	private boolean useChordFormula = false;
	private int longProgressionSimilarity = 0;
	private boolean customChordsEnabled = false;
	private boolean customDurationsEnabled = false;
	private String customChords = "C";
	private String customChordDurations = "4,4,4,4";

	// arp gen
	private boolean useOctaveAdjustments = false;
	private boolean randomArpCorrectMelodyNotes = false;

	// drum gen
	private boolean drumCustomMapping = true;
	private String drumCustomMappingNumbers = StringUtils.join(InstUtils.DRUM_INST_NUMBERS_SEMI,
			",");

	// tabbed parts
	private List<MelodyPart> melodyParts = new ArrayList<>();
	private List<BassPart> bassParts = new ArrayList<>();
	private List<ChordPart> chordParts = new ArrayList<>();
	private List<DrumPart> drumParts = new ArrayList<>();
	private List<ArpPart> arpParts = new ArrayList<>();
	private boolean melodyEnable = true;
	private boolean bassEnable = true;
	private boolean chordsEnable = true;
	private boolean arpsEnable = true;
	private boolean drumsEnable = true;
	private ChordGenSettings chordGenSettings = new ChordGenSettings();
	private DrumGenSettings drumGenSettings = new DrumGenSettings();
	private ArpGenSettings arpGenSettings = new ArpGenSettings();

	private boolean midiMode = true;
	private long randomSeed = 0;

	private String bookmarkText = "";
	private int regenerateCount = 0;

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

	public boolean isDimAug6thEnabled() {
		return dimAug6thEnabled;
	}

	public void setDimAug6thEnabled(boolean dimAug6thEnabled) {
		this.dimAug6thEnabled = dimAug6thEnabled;
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

	public List<BassPart> getBassParts() {
		return bassParts;
	}

	public void setBassParts(List<BassPart> bassParts) {
		this.bassParts = bassParts;
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

	public int getMaxMelodySwing() {
		return maxMelodySwing;
	}

	public void setMaxMelodySwing(int maxMelodySwing) {
		this.maxMelodySwing = maxMelodySwing;
	}

	public Integer getBeatDurationMultiplierIndex() {
		return beatDurationMultiplierIndex;
	}

	public void setBeatDurationMultiplierIndex(Integer beatDurationMultiplierIndex) {
		this.beatDurationMultiplierIndex = beatDurationMultiplierIndex;
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

	public int getSpiceParallelChance() {
		return spiceParallelChance;
	}

	public void setSpiceParallelChance(int spiceParallelChance) {
		this.spiceParallelChance = spiceParallelChance;
	}

	public int getMelodyPatternEffect() {
		return melodyPatternEffect;
	}

	public void setMelodyPatternEffect(int melodyPatternEffect) {
		this.melodyPatternEffect = melodyPatternEffect;
	}

	public boolean isMelodyFillPausesPerChord() {
		return melodyFillPausesPerChord;
	}

	public void setMelodyFillPausesPerChord(boolean melodyFillPausesPerChord) {
		this.melodyFillPausesPerChord = melodyFillPausesPerChord;
	}

	public int getMelodyMaxDirChanges() {
		return melodyMaxDirChanges;
	}

	public void setMelodyMaxDirChanges(int melodyMaxDirChanges) {
		this.melodyMaxDirChanges = melodyMaxDirChanges;
	}

	public PhraseNotes getMelodyNotes() {
		return melodyNotes;
	}

	public void setMelodyNotes(PhraseNotes melodyNotes) {
		this.melodyNotes = melodyNotes;
	}

	public boolean isPartEnabled(int partNum) {
		switch (partNum) {
		case 0:
			return melodyEnable;
		case 1:
			return bassEnable;
		case 2:
			return chordsEnable;
		case 3:
			return arpsEnable;
		case 4:
			return drumsEnable;
		}
		throw new IllegalArgumentException("Invalid partNum");
	}

	public boolean isMelodyEnable() {
		return melodyEnable;
	}

	public void setMelodyEnable(boolean melodyEnable) {
		this.melodyEnable = melodyEnable;
	}

	public boolean isBassEnable() {
		return bassEnable;
	}

	public void setBassEnable(boolean bassEnable) {
		this.bassEnable = bassEnable;
	}

	public boolean isSquishProgressively() {
		return squishProgressively;
	}

	public void setSquishProgressively(boolean squishProgressively) {
		this.squishProgressively = squishProgressively;
	}

	public boolean isMidiMode() {
		return midiMode;
	}

	public void setMidiMode(boolean midiMode) {
		this.midiMode = midiMode;
	}

	public boolean isCombineMelodyTracks() {
		return combineMelodyTracks;
	}

	public void setCombineMelodyTracks(boolean combineMelodyTracks) {
		this.combineMelodyTracks = combineMelodyTracks;
	}

	public int getMelodyTargetNoteVariation() {
		return melodyTargetNoteVariation;
	}

	public void setMelodyTargetNoteVariation(int melodyTargetNoteVariation) {
		this.melodyTargetNoteVariation = melodyTargetNoteVariation;
	}

	public Integer getSwingUnitMultiplierIndex() {
		return swingUnitMultiplierIndex;
	}

	public void setSwingUnitMultiplierIndex(Integer swingUnitMultiplierIndex) {
		this.swingUnitMultiplierIndex = swingUnitMultiplierIndex;
	}

	public int getLongProgressionSimilarity() {
		return longProgressionSimilarity;
	}

	public void setLongProgressionSimilarity(int longProgressionSimilarity) {
		this.longProgressionSimilarity = longProgressionSimilarity;
	}

	public boolean isCustomMidiForceScale() {
		return customMidiForceScale;
	}

	public void setCustomMidiForceScale(boolean customMidiForceScale) {
		this.customMidiForceScale = customMidiForceScale;
	}

	@Override
	public String toString() {
		return MidiUtils.SEMITONE_LETTERS.get((transpose + 1200) % 12) + " " + scaleMode.toString()
				+ " " + (int) bpm + "bpm [Size: " + actualArrangement.getSections().size()
				+ "] Seed: " + randomSeed + " [" + customChords + "]"
				+ ((regenerateCount > 0) ? ("(" + regenerateCount + ")") : "")
				+ (StringUtils.isNotEmpty(bookmarkText) ? (" - " + bookmarkText) : "");
	}

	@XmlTransient
	public String getBookmarkText() {
		return bookmarkText;
	}

	public void setBookmarkText(String bookmarkText) {
		this.bookmarkText = bookmarkText;
	}

	public List<? extends InstPart> getInstPartList(int partNum) {
		switch (partNum) {
		case 0:
			return melodyParts;
		case 1:
			return bassParts;
		case 2:
			return chordParts;
		case 3:
			return arpParts;
		case 4:
			return drumParts;
		}
		throw new IllegalArgumentException("PartNum incorrect: " + partNum);
	}

	@XmlTransient
	public int getRegenerateCount() {
		return regenerateCount;
	}

	public void setRegenerateCount(int regenerateCount) {
		this.regenerateCount = regenerateCount;
	}

	public boolean isTransposedNotesForceScale() {
		return transposedNotesForceScale;
	}

	public void setTransposedNotesForceScale(boolean transposedNotesForceScale) {
		this.transposedNotesForceScale = transposedNotesForceScale;
	}

	public int getMelodyRhythmAccents() {
		return melodyRhythmAccents;
	}

	public void setMelodyRhythmAccents(int melodyRhythmAccents) {
		this.melodyRhythmAccents = melodyRhythmAccents;
	}

	public int getMelodyRhythmAccentsMode() {
		return melodyRhythmAccentsMode;
	}

	public void setMelodyRhythmAccentsMode(int melodyRhythmAccentsMode) {
		this.melodyRhythmAccentsMode = melodyRhythmAccentsMode;
	}

	public boolean isMelodyRhythmAccentsPocket() {
		return melodyRhythmAccentsPocket;
	}

	public void setMelodyRhythmAccentsPocket(boolean melodyRhythmAccentsPocket) {
		this.melodyRhythmAccentsPocket = melodyRhythmAccentsPocket;
	}

	public boolean isMelodyLegacyMode() {
		return melodyLegacyMode;
	}

	public void setMelodyLegacyMode(boolean melodyLegacyMode) {
		this.melodyLegacyMode = melodyLegacyMode;
	}

	@XmlElement(name = "patternMap")
	public List<PatternMap> getPatternMaps() {
		return patternMaps;
	}

	public void setPatternMaps(List<PatternMap> patternMaps) {
		this.patternMaps = patternMaps;
	}

	public void putPattern(UsedPattern pattern, PhraseNotes pn) {
		if (pattern == null) {
			return;
		}
		if (UsedPattern.NONE.equals(pattern.getName())) {
			pn = null;
		}
		patternMaps.get(pattern.getPart()).put(pattern.getPartOrder(), pattern.getName(), pn);
	}

	public PhraseNotes getPattern(UsedPattern pattern) {
		if (pattern == null) {
			return null;
		}
		return getPattern(pattern.getPart(), pattern.getPartOrder(), pattern.getName());
	}

	public PhraseNotes getPattern(int part, int partOrder, String patName) {
		if (UsedPattern.NONE.equals(patName)) {
			return null;
		}
		return patternMaps.get(part).get(partOrder, patName);
	}

	public PhraseNotes getPatternRaw(UsedPattern pattern) {
		if (pattern == null) {
			return null;
		}
		return getPatternRaw(pattern.getPart(), pattern.getPartOrder(), pattern.getName());
	}

	public PhraseNotes getPatternRaw(int part, int partOrder, String patName) {
		if (UsedPattern.NONE.equals(patName) || patternMaps.size() <= part) {
			return null;
		}
		return patternMaps.get(part).getRaw(partOrder, patName);
	}

	public boolean isCustomDurationsEnabled() {
		return customDurationsEnabled;
	}

	public void setCustomDurationsEnabled(boolean customDurationsEnabled) {
		this.customDurationsEnabled = customDurationsEnabled;
	}

	public boolean isRandomArpCorrectMelodyNotes() {
		return randomArpCorrectMelodyNotes;
	}

	public void setRandomArpCorrectMelodyNotes(boolean randomArpCorrectMelodyNotes) {
		this.randomArpCorrectMelodyNotes = randomArpCorrectMelodyNotes;
	}

	public int getMelodyNewBlocksChance() {
		return melodyNewBlocksChance;
	}

	public void setMelodyNewBlocksChance(int melodyNewBlocksChance) {
		this.melodyNewBlocksChance = melodyNewBlocksChance;
	}

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

	public int getHumanizeDrums() {
		return humanizeDrums;
	}

	public void setHumanizeDrums(int humanizeDrums) {
		this.humanizeDrums = humanizeDrums;
	}

	public int getHumanizeNotes() {
		return humanizeNotes;
	}

	public void setHumanizeNotes(int humanizeNotes) {
		this.humanizeNotes = humanizeNotes;
	}

	public List<Integer> getMelodyBlockChoicePreference() {
		return melodyBlockChoicePreference;
	}

	public void setMelodyBlockChoicePreference(List<Integer> melodyBlockChoicePreference) {
		this.melodyBlockChoicePreference = melodyBlockChoicePreference;
	}
}
