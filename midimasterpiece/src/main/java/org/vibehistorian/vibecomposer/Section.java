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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.Helpers.OMNI;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.BassPart;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;

import jm.music.data.Phrase;

@XmlRootElement(name = "section")
@XmlType(propOrder = {})
public class Section {
	public enum SectionType {
		INTRO, VERSE1, VERSE2, CHORUS1, CHORUS2, HALF_CHORUS, BREAKDOWN, CHILL, BUILDUP, CHORUS3,
		CLIMAX, OUTRO;
	}

	public static final String[][] variationDescriptions = {
			{ "#", "Incl.", "Transpose", "MaxJump" },
			{ "#", "Incl.", "OffsetSeed", "RhythmPauses" },
			{ "#", "Incl.", "Transpose", "IgnoreFill", "UpStretch", "No2nd", "MaxStrum" },
			{ "#", "Incl.", "Transpose", "IgnoreFill", "RandOct", "FillLast", "ChordDir." },
			{ "#", "Incl.", "IgnoreFill", "MoreExceptions", "DrumFill" } };

	public static final String[] riskyVariationNames = { "Skip N-1 chord", "Swap Chords",
			"Swap Melody", "Melody Pause Squish", "Key Change", "TransitionFast", "TransitionSlow",
			"TransitionCut" };
	public static final Double[] riskyVariationChanceMultipliers = { 1.0, 1.0, 0.7, 1.0, 1.0, 1.5,
			1.75, 1.0 };

	public static final int VARIATION_CHANCE = 30;

	private String type;
	private int measures;

	private double startTime;
	private double sectionDuration = -1;
	private List<Double> sectionBeatDurations = null;

	private int melodyChance = 50;
	private int bassChance = 50;
	private int chordChance = 50;
	private int arpChance = 50;
	private int drumChance = 50;


	private List<Integer> instVelocityMultiplier = new ArrayList<>();

	// data (transient)
	private List<Phrase> melodies;
	private Phrase bass;
	private List<Phrase> chords;
	private List<Phrase> arps;
	private List<Phrase> drums;
	private Phrase chordSlash;

	// customized chords/durations
	private boolean customChordsDurationsEnabled = false;
	private String customChords = "?";
	private String customDurations = "2,2,2,2";

	// customized parts
	private List<BassPart> bassParts = null;
	private List<MelodyPart> melodyParts = null;
	private List<ChordPart> chordParts = null;
	private List<DrumPart> drumParts = null;
	private List<ArpPart> arpParts = null;

	public List<? extends InstPart> getInstPartList(int order) {
		if (order < 0 || order > 4) {
			throw new IllegalArgumentException("Inst part list order wrong.");
		}
		switch (order) {
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
		return null;
	}

	// map integer(what), map integer(part order), list integer(section variation)
	private Map<Integer, Object[][]> partPresenceVariationMap = new HashMap<>();

	private List<Boolean> riskyVariations = null;

	public Section() {

	}

	public Section(String type, int measures, int melodyChance, int bassChance, int chordChance,
			int arpChance, int drumChance) {
		this();
		this.type = type;
		this.measures = measures;
		this.melodyChance = melodyChance;
		this.bassChance = bassChance;
		this.chordChance = chordChance;
		this.arpChance = arpChance;
		this.drumChance = drumChance;
	}

	public boolean isClimax() {
		return SectionType.CLIMAX.toString().equals(type);
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

	public List<Phrase> getMelodies() {
		return melodies;
	}

	@XmlTransient
	public void setMelodies(List<Phrase> melodies) {
		this.melodies = melodies;
	}

	public Phrase getBass() {
		return bass;
	}

	@XmlTransient
	public void setBass(Phrase bass) {
		this.bass = bass;
	}

	public List<Phrase> getChords() {
		return chords;
	}

	@XmlTransient
	public void setChords(List<Phrase> chords) {
		this.chords = chords;
	}

	public List<Phrase> getArps() {
		return arps;
	}

	@XmlTransient
	public void setArps(List<Phrase> arps) {
		this.arps = arps;
	}

	public List<Phrase> getDrums() {
		return drums;
	}

	@XmlTransient
	public void setDrums(List<Phrase> drums) {
		this.drums = drums;
	}

	public Phrase getChordSlash() {
		return chordSlash;
	}

	@XmlTransient
	public void setChordSlash(Phrase chordSlash) {
		this.chordSlash = chordSlash;
	}

	@XmlTransient
	public double getStartTime() {
		return startTime;
	}

	public void setStartTime(double startTime) {
		this.startTime = startTime;
	}

	public Section deepCopy() {
		initPartMapIfNull();
		Section sec = new Section(type, measures, melodyChance, bassChance, chordChance, arpChance,
				drumChance);
		Map<Integer, Object[][]> dataCopy = new HashMap<>();
		for (int i = 0; i < 5; i++) {
			if (partPresenceVariationMap.get(i).length == 0) {
				dataCopy.put(i, new Object[0][0]);
				continue;
			}
			Object[][] data = new Object[partPresenceVariationMap
					.get(i).length][partPresenceVariationMap.get(i)[0].length];
			for (int k = 0; k < data.length; k++) {
				data[k] = partPresenceVariationMap.get(i)[k].clone();
			}
			dataCopy.put(i, data);
		}
		sec.partPresenceVariationMap = dataCopy;
		if (riskyVariations != null) {
			sec.riskyVariations = new ArrayList<>(riskyVariations);
		}
		if (instVelocityMultiplier != null) {
			sec.instVelocityMultiplier = new ArrayList<>(instVelocityMultiplier);
		}

		if (getMelodyParts() != null) {
			sec.setMelodyParts(getMelodyParts());
		}
		if (getBassParts() != null) {
			sec.setBassParts(getBassParts());
		}
		if (getChordParts() != null) {
			sec.setChordParts(getChordParts());
		}
		if (getArpParts() != null) {
			sec.setArpParts(getArpParts());
		}
		if (getDrumParts() != null) {
			sec.setDrumParts(getDrumParts());
		}

		sec.setCustomChords(getCustomChords());
		sec.setCustomDurations(getCustomDurations());
		sec.setCustomChordsDurationsEnabled(customChordsDurationsEnabled);
		sec.setSectionDuration(sectionDuration);
		if (sectionBeatDurations != null) {
			sec.sectionBeatDurations = new ArrayList<>(sectionBeatDurations);
		}

		return sec;
	}

	public void resetCustomizedParts() {
		setMelodyParts(null);
		setBassParts(null);
		setChordParts(null);
		setArpParts(null);
		setDrumParts(null);
	}

	public boolean hasCustomizedParts() {
		return melodyParts != null || bassParts != null || chordParts != null || arpParts != null
				|| drumParts != null;
	}

	public boolean hasPresence() {
		for (int i = 0; i < 5; i++) {
			if (countPresence(i) > 0) {
				return true;
			}
		}
		return false;
	}

	public int countPresence(int part) {
		return getPresence(part).size();
	}

	public Set<Integer> getPresence(int part) {
		initPartMapIfNull();
		Set<Integer> pres = new HashSet<>();

		Object[][] data = partPresenceVariationMap.get(part);
		for (int i = 0; i < data.length; i++) {
			if (data[i][1] == Boolean.TRUE) {
				pres.add((Integer) data[i][0]);
			}
		}
		return pres;
	}

	public void setPresence(int part, int partOrder) {
		initPartMapIfNull();
		partPresenceVariationMap.get(part)[partOrder][1] = Boolean.TRUE;
	}

	public void resetPresence(int part, int partOrder) {
		initPartMapIfNull();
		partPresenceVariationMap.get(part)[partOrder][1] = Boolean.FALSE;
		/*for (int i = 2; i < variationDescriptions[part].length; i++) {
			partPresenceVariationMap.get(part)[partOrder][i] = Boolean.FALSE;
		}*/
	}

	public boolean hasVariation(int part) {
		for (int i = 0; i < VibeComposerGUI.getInstList(part).size(); i++) {
			if (!getVariation(part, i).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	public List<Integer> getVariation(int part, int partOrder) {
		initPartMapIfNull();
		List<Integer> variations = new ArrayList<>();
		for (int i = 2; i < partPresenceVariationMap.get(part)[partOrder].length; i++) {
			if (partPresenceVariationMap.get(part)[partOrder][i] == Boolean.TRUE) {
				variations.add(i - 2);
			}
		}
		return variations;
	}

	public void setVariation(int part, int partOrder, List<Integer> vars) {
		initPartMapIfNull();
		for (int i = 0; i < partPresenceVariationMap.get(part)[partOrder].length - 2; i++) {
			partPresenceVariationMap.get(part)[partOrder][i + 2] = vars
					.contains(Integer.valueOf(i));
		}
	}

	public void addVariation(int part, int partOrder, List<Integer> vars) {
		initPartMapIfNull();
		for (int i = 0; i < partPresenceVariationMap.get(part)[partOrder].length - 2; i++) {
			partPresenceVariationMap.get(part)[partOrder][i
					+ 2] = ((Boolean) partPresenceVariationMap.get(part)[partOrder][i + 2])
							|| vars.contains(Integer.valueOf(i));
		}
	}

	public void generatePresences(Random presRand) {
		initPartMapIfNull();
		for (int i = 0; i < 5; i++) {
			generatePresences(presRand, i);
		}

	}

	public void generatePresences(Random presRand, int part) {
		initPartMapIfNull();
		int chance = getChanceForInst(part);
		System.out.println("Chance: " + chance);
		List<? extends InstPanel> panels = VibeComposerGUI.getInstList(part);
		for (int j = 0; j < panels.size(); j++) {
			if (presRand.nextInt(100) < chance) {
				setPresence(part, j);
			}
		}

	}

	public void generateVariations(Random presRand, int part) {
		initPartMapIfNull();
		Set<Integer> presence = getPresence(part);
		int chance = VibeComposerGUI.arrangementPartVariationChance.getInt();
		for (Integer i : presence) {
			for (int j = 2; j < Section.variationDescriptions[part].length; j++) {
				if (presRand.nextInt(100) < chance) {
					addVariation(part, i - 1, Collections.singletonList(j - 2));
				}
			}
		}


	}

	public int getChanceForInst(int inst) {
		switch (inst) {
		case 0:
			return melodyChance;
		case 1:
			return bassChance;
		case 2:
			return chordChance;
		case 3:
			return arpChance;
		case 4:
			return drumChance;
		default:
			throw new IllegalArgumentException("Too high inst. order");
		}
	}

	public void addChanceForInst(int inst, int chance) {
		switch (inst) {
		case 0:
			melodyChance = OMNI.clampChance(melodyChance + chance);
			break;
		case 1:
			bassChance = OMNI.clampChance(bassChance + chance);
			break;
		case 2:
			chordChance = OMNI.clampChance(chordChance + chance);
			break;
		case 3:
			arpChance = OMNI.clampChance(arpChance + chance);
			break;
		case 4:
			drumChance = OMNI.clampChance(drumChance + chance);
			break;
		default:
			throw new IllegalArgumentException("Too high inst. order");
		}
	}

	public int getTypeSeedOffset() {
		if (StringUtils.isEmpty(type)) {
			return 0;
		} else {
			int offset = 0;
			for (int i = 0; i < type.length(); i++) {
				offset += type.charAt(i);
			}
			return offset;
		}
	}

	public int getTypeMelodyOffset() {
		return getTypeMelodyOffset(type);
	}

	public static int getTypeMelodyOffset(String type) {
		if (StringUtils.isEmpty(type)) {
			return 0;
		} else {
			if (type.startsWith("CHORUS") || type.startsWith("CLIMAX")
					|| type.startsWith("PREVIEW")) {
				return 0;
			}
			if (type.startsWith("INTRO") || type.startsWith("OUTRO") || type.startsWith("BUILDUP")
					|| type.startsWith("BREAKDOWN") || type.startsWith("CHILL")) {
				return 1;
			}
			if (type.startsWith("VERSE")) {
				return 2;
			} else {
				return 0;
			}
		}
	}

	public void initPartMapFromOldData() {
		if (partPresenceVariationMap == null) {
			initPartMap();
			return;
		}
		for (int i = 0; i < 5; i++) {
			List<Integer> rowOrders = VibeComposerGUI.getInstList(i).stream()
					.map(e -> e.getPanelOrder()).collect(Collectors.toList());
			Collections.sort(rowOrders);
			Object[][] data = new Object[rowOrders.size()][variationDescriptions[i].length + 1];
			for (int j = 0; j < rowOrders.size(); j++) {
				data[j][0] = rowOrders.get(j);
				for (int k = 1; k < variationDescriptions[i].length + 1; k++) {
					data[j][k] = getBooleanFromOldData(partPresenceVariationMap.get(i), j, k);
				}
			}
			partPresenceVariationMap.put(i, data);
		}
	}

	private Boolean getBooleanFromOldData(Object[][] oldData, int j, int k) {
		if (oldData.length <= j || oldData[j].length <= k) {
			return Boolean.FALSE;
		} else {
			return (Boolean) oldData[j][k];
		}
	}

	public void initPartMap() {
		for (int i = 0; i < 5; i++) {
			List<Integer> rowOrders = VibeComposerGUI.getInstList(i).stream()
					.map(e -> e.getPanelOrder()).collect(Collectors.toList());
			Collections.sort(rowOrders);
			Object[][] data = new Object[rowOrders.size()][variationDescriptions[i].length + 1];
			for (int j = 0; j < rowOrders.size(); j++) {
				data[j][0] = rowOrders.get(j);
				for (int k = 1; k < variationDescriptions[i].length + 1; k++) {
					data[j][k] = Boolean.FALSE;
				}
			}
			partPresenceVariationMap.put(i, data);
		}
	}

	public void initPartMapIfNull() {
		if (partPresenceVariationMap.get(0) == null) {
			initPartMap();
		}
	}


	public Map<Integer, Object[][]> getPartPresenceVariationMap() {
		return partPresenceVariationMap;
	}


	public void setPartPresenceVariationMap(Map<Integer, Object[][]> partPresenceVariationMap) {
		this.partPresenceVariationMap = partPresenceVariationMap;
	}

	@XmlList
	public List<Boolean> getRiskyVariations() {
		if (riskyVariations != null) {
			while (riskyVariations.size() < riskyVariationNames.length) {
				riskyVariations.add(Boolean.FALSE);
			}
		}
		return riskyVariations;
	}

	public boolean isTransition() {
		return getRiskyVariations() != null && (getRiskyVariations().get(5)
				|| getRiskyVariations().get(6) || getRiskyVariations().get(7));
	}

	public int getTransitionType() {
		if (!isTransition()) {
			return -1;
		}

		return getRiskyVariations().get(7) ? 7 : (getRiskyVariations().get(6) ? 6 : 5);
	}

	public void setRiskyVariations(List<Boolean> riskyVariations) {
		this.riskyVariations = riskyVariations;
	}

	public void setRiskyVariation(int order, Boolean value) {
		if (riskyVariations == null) {
			List<Boolean> riskyVars = new ArrayList<>();
			for (int i = 0; i < Section.riskyVariationNames.length; i++) {
				riskyVars.add(Boolean.FALSE);
			}
			setRiskyVariations(riskyVars);
		}
		riskyVariations.set(order, value);
	}

	public double countVariationsForPartType(int part) {
		if (partPresenceVariationMap == null) {
			return 0;
		}
		double count = 0;
		double total = 0;
		Object[][] data = partPresenceVariationMap.get(part);
		for (int i = 0; i < data.length; i++) {
			for (int j = 2; j < data[i].length; j++) {
				if (data[i][j] == Boolean.TRUE) {
					count++;
				}
				total++;
			}
		}
		return count / total;
	}

	public void recalculatePartVariationMapBoundsIfNeeded() {
		boolean needsArrayCopy = false;
		for (int i = 0; i < 5; i++) {
			int actualInstCount = VibeComposerGUI.getInstList(i).size();
			int secInstCount = getPartPresenceVariationMap().get(i).length;
			if (secInstCount != actualInstCount) {
				needsArrayCopy = true;
				break;
			}
		}
		if (needsArrayCopy) {
			initPartMapFromOldData();
		}

	}

	public void removeVariationForAllParts(int part, int variationNum) {
		if (variationNum < 2) {
			return;
		}
		initPartMapIfNull();
		for (int i = 0; i < partPresenceVariationMap.get(part).length; i++) {
			partPresenceVariationMap.get(part)[i][variationNum] = Boolean.FALSE;
		}
	}

	public List<BassPart> getBassParts() {
		return bassParts;
	}

	public void setBassParts(List<BassPart> bassParts) {
		this.bassParts = bassParts;
	}

	public List<MelodyPart> getMelodyParts() {
		return melodyParts;
	}

	public void setMelodyParts(List<MelodyPart> melodyParts) {
		this.melodyParts = melodyParts;
	}

	public List<ChordPart> getChordParts() {
		return chordParts;
	}

	public void setChordParts(List<ChordPart> chordParts) {
		this.chordParts = chordParts;
	}

	public List<DrumPart> getDrumParts() {
		return drumParts;
	}

	public void setDrumParts(List<DrumPart> drumParts) {
		this.drumParts = drumParts;
	}

	public List<ArpPart> getArpParts() {
		return arpParts;
	}

	public void setArpParts(List<ArpPart> arpParts) {
		this.arpParts = arpParts;
	}

	public String getCustomChords() {
		return customChords;
	}

	public void setCustomChords(String customChords) {
		this.customChords = customChords;
	}

	public String getCustomDurations() {
		return customDurations;
	}

	public void setCustomDurations(String customDurations) {
		this.customDurations = customDurations;
	}

	public boolean isCustomChordsDurationsEnabled() {
		return customChordsDurationsEnabled;
	}

	public void setCustomChordsDurationsEnabled(boolean customChordsDurationsEnabled) {
		this.customChordsDurationsEnabled = customChordsDurationsEnabled;
	}

	@XmlTransient
	public double getSectionDuration() {
		return sectionDuration;
	}

	public void setSectionDuration(double sectionDuration) {
		this.sectionDuration = sectionDuration;
	}

	@XmlTransient
	public List<Double> getSectionBeatDurations() {
		return sectionBeatDurations;
	}

	public void setSectionBeatDurations(List<Double> sectionBeatDurations) {
		this.sectionBeatDurations = sectionBeatDurations;
	}

	@XmlList
	public List<Integer> getInstVelocityMultiplier() {
		return instVelocityMultiplier;
	}

	public void setInstVelocityMultiplier(List<Integer> instVelocityMultiplier) {
		this.instVelocityMultiplier = instVelocityMultiplier;
	}

	public Integer getVol(int inst) {
		if (instVelocityMultiplier == null || instVelocityMultiplier.size() <= inst) {
			return (67 + getChanceForInst(inst) / 3);
		}
		return instVelocityMultiplier.get(inst);
	}
}
