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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.Helpers.InclusionMapJAXB;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Helpers.UsedPattern;
import org.vibehistorian.vibecomposer.Helpers.UsedPatternMap;
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
		INTRO, VERSE1, VERSE2, VERSE3, CHORUS1, CHORUS2, HALF_CHORUS, BREAKDOWN, CHILL, BUILDUP1,
		BUILDUP2, CHORUS3, CLIMAX, OUTRO;
	}

	public static final String[][] variationDescriptions = {
			{ "#", "Incl.", "Transpose", "MaxJump", "Embellish", "Solo" },
			{ "#", "Incl.", "OffsetSeed", "RhythmPauses" },
			{ "#", "Incl.", "Transpose", "IgnoreFill", "UpStretch", "No2nd", "MaxStrum" },
			{ "#", "Incl.", "Transpose", "IgnoreFill", "RandOct", "FillLast", "ChordDir." },
			{ "#", "Incl.", "IgnoreFill", "MoreExceptions", "DrumFill" } };

	public static final String[] sectionVariationNames = { "Skip N-1 chord", "Swap Chords",
			"Swap Melody", "Melody Max Speed", "Key Change" };
	public static final Double[] sectionVariationChanceMultipliers = { 1.0, 0.3, 0.7, 1.0, 1.0 };

	public static final String[] transitionNames = { "None", "Hype Up", "Pipe Down", "Cut End",
			"Half Tempo" };
	public static final Double[] transitionChanceMultipliers = { 1.0, 1.5, 1.75, 0.7, 1.0 };

	public static final int VARIATION_CHANCE = 30;

	private String type;
	private int measures = 1;

	private double startTime;
	private double sectionDuration = -1;
	private List<Double> sectionBeatDurations = null;
	private List<Double> generatedSectionBeatDurations = null;

	private SectionConfig secConfig = new SectionConfig();

	private int melodyChance = 50;
	private int bassChance = 50;
	private int chordChance = 50;
	private int arpChance = 50;
	private int drumChance = 50;


	private List<Integer> instVelocityMultiplier = new ArrayList<>();

	// data (transient)
	private List<Phrase> melodies;
	private List<Phrase> basses;
	private List<Phrase> chords;
	private List<Phrase> arps;
	private List<Phrase> drums;
	private Phrase chordSlash;

	// customized chords/durations
	private boolean customChordsEnabled = false;
	private boolean customDurationsEnabled = false;
	private boolean displayAlternateChords = false;
	private String customChords = "?";
	private String customDurations = "4,4,4,4";

	// customized parts
	private List<BassPart> bassParts = null;
	private List<MelodyPart> melodyParts = null;
	private List<ChordPart> chordParts = null;
	private List<DrumPart> drumParts = null;
	private List<ArpPart> arpParts = null;

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

	public void setInstPartList(List<? extends InstPart> parts, int partNum) {
		switch (partNum) {
		case 0:
			setMelodyParts((List<MelodyPart>) (List<?>) parts);
			break;
		case 1:
			setBassParts((List<BassPart>) (List<?>) parts);
			break;
		case 2:
			setChordParts((List<ChordPart>) (List<?>) parts);
			break;
		case 3:
			setArpParts((List<ArpPart>) (List<?>) parts);
			break;
		case 4:
			setDrumParts((List<DrumPart>) (List<?>) parts);
			break;
		default:
			throw new IllegalArgumentException("PartNum incorrect: " + partNum);
		}
	}

	// map integer(part type), [part order][presence/section variation]
	private Map<Integer, Object[][]> partPresenceVariationMap = new HashMap<>();

	private List<Integer> sectionVariations = null;
	public static final List<Integer> EMPTY_SECTION_VARS = IntStream.iterate(0, f -> f)
			.limit(sectionVariationNames.length).boxed().collect(Collectors.toList());
	private int transitionType = 0;

	private List<UsedPatternMap> patterns = UsedPatternMap.multiMap();

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
		if (melodies == null) {
			melodies = new ArrayList<>();
		}
		return melodies;
	}

	@XmlTransient
	public void setMelodies(List<Phrase> melodies) {
		this.melodies = melodies;
	}

	public List<Phrase> getBasses() {
		if (basses == null) {
			basses = new ArrayList<>();
		}
		return basses;
	}

	@XmlTransient
	public void setBasses(List<Phrase> basses) {
		this.basses = basses;
	}

	public List<Phrase> getChords() {
		if (chords == null) {
			chords = new ArrayList<>();
		}
		return chords;
	}

	@XmlTransient
	public void setChords(List<Phrase> chords) {
		this.chords = chords;
	}

	public List<Phrase> getArps() {
		if (arps == null) {
			arps = new ArrayList<>();
		}
		return arps;
	}

	@XmlTransient
	public void setArps(List<Phrase> arps) {
		this.arps = arps;
	}

	public List<Phrase> getDrums() {
		if (drums == null) {
			drums = new ArrayList<>();
		}
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
		//LG.d("deep copy called");
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
		if (sectionVariations != null) {
			sec.sectionVariations = new ArrayList<>(sectionVariations);
		}
		sec.transitionType = getTransitionType();
		if (instVelocityMultiplier != null) {
			sec.instVelocityMultiplier = new ArrayList<>(instVelocityMultiplier);
		}

		sec.setMelodyParts(getMelodyParts());
		sec.setBassParts(getBassParts());
		sec.setChordParts(getChordParts());
		sec.setArpParts(getArpParts());
		sec.setDrumParts(getDrumParts());

		sec.setPatterns(UsedPatternMap.multiMapCopy(patterns));

		sec.setCustomChords(getCustomChords());
		sec.setCustomDurations(getCustomDurations());
		sec.setCustomChordsEnabled(customChordsEnabled);
		sec.setCustomDurationsEnabled(customDurationsEnabled);
		sec.setSectionDuration(sectionDuration);
		sec.setDisplayAlternateChords(isDisplayAlternateChords());
		if (sectionBeatDurations != null) {
			sec.sectionBeatDurations = new ArrayList<>(sectionBeatDurations);
		}
		if (generatedSectionBeatDurations != null) {
			sec.generatedSectionBeatDurations = new ArrayList<>(generatedSectionBeatDurations);
		}

		sec.setSecConfig(secConfig.clone());

		return sec;
	}

	public void resetCustomizedParts(int partNum) {
		if (partNum > 4) {
			resetCustomizedParts();
		} else {
			setInstPartList(null, partNum);
		}
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

	public Map<Integer, Integer> getPresenceWithIndices(int part) {
		initPartMapIfNull();
		Map<Integer, Integer> pres = new HashMap<>();

		Object[][] data = partPresenceVariationMap.get(part);
		for (int i = 0; i < data.length; i++) {
			if (data[i][1] == Boolean.TRUE) {
				pres.put((Integer) data[i][0], i);
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

	public void resetAllPresence(int part) {
		initPartMapIfNull();
		for (int i = 0; i < partPresenceVariationMap.get(part).length; i++) {
			partPresenceVariationMap.get(part)[i][1] = Boolean.FALSE;
		}

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
		if (partPresenceVariationMap.get(part).length <= partOrder) {
			return variations;
		}
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

	public void generatePresences(Random presRand, boolean forceAdd) {
		initPartMapIfNull();
		for (int i = 0; i < 5; i++) {
			generatePresences(presRand, i, VibeComposerGUI.arrangement.getInclMap(), forceAdd);
		}

	}

	public void generatePresences(Random presRand, int part, Map<Integer, Object[][]> inclusionMap,
			boolean forceAdd) {
		initPartMapIfNull();
		int chance = getChanceForInst(part);
		//LG.d("Chance: " + chance);
		List<? extends InstPanel> panels = new ArrayList<>(VibeComposerGUI.getInstList(part));
		panels.removeIf(e -> e.getMuteInst());
		if (inclusionMap != null) {
			panels.removeIf(e -> {
				int absOrder = VibeComposerGUI.getAbsoluteOrder(part, e.getPanelOrder());
				//LG.d("Abs order: " + absOrder);
				//LG.d("Offset+2: " + (getTypeMelodyOffset() + 2));
				if (inclusionMap.get(part).length <= absOrder || Boolean.FALSE
						.equals(inclusionMap.get(part)[absOrder][getTypeMelodyOffset() + 2])) {
					//LG.d("TRUE");
					return true;
				}
				return false;
			});
		}
		//LG.d("Panels size: " + panels.size());
		int added = 0;
		for (int j = 0; j < panels.size(); j++) {
			if (presRand.nextInt(100) < chance) {
				setPresence(part,
						VibeComposerGUI.getAbsoluteOrder(part, panels.get(j).getPanelOrder()));
				added++;
			}
		}
		if (forceAdd && added == 0 && panels.size() > 0) {
			InstPanel panel = panels.get(presRand.nextInt(panels.size()));
			setPresence(part, VibeComposerGUI.getAbsoluteOrder(part, panel.getPanelOrder()));
		}

	}

	public void generateVariations(Random presRand, int part) {
		initPartMapIfNull();
		List<Integer> presence = new ArrayList<>(getPresence(part));
		if (presence.isEmpty()) {
			return;
		}
		int chance = VibeComposerGUI.arrangementPartVariationChance.getInt();
		int added = 0;
		for (Integer i : presence) {
			for (int j = 2; j < Section.variationDescriptions[part].length; j++) {
				if (presRand.nextInt(100) < chance) {
					addVariation(part, VibeComposerGUI.getAbsoluteOrder(part, i),
							Collections.singletonList(j - 2));
					added++;
				}
			}
		}
		if (added == 0) {
			int pres = presence.get(presRand.nextInt(presence.size()));
			int randVar = presRand.nextInt(Section.variationDescriptions[part].length - 2);
			addVariation(part, VibeComposerGUI.getAbsoluteOrder(part, pres),
					Collections.singletonList(randVar));
		}
	}

	public void generateVariationForPartAndOrder(Random presRand, int part, int order,
			boolean forceAdd) {
		initPartMapIfNull();
		int chance = VibeComposerGUI.arrangementPartVariationChance.getInt();
		int added = 0;
		for (int j = 2; j < Section.variationDescriptions[part].length; j++) {
			if (presRand.nextInt(100) < chance) {
				addVariation(part, order, Collections.singletonList(j - 2));
				added++;
			}
		}
		if (forceAdd && added == 0) {
			int randVar = presRand.nextInt(Section.variationDescriptions[part].length - 2);
			addVariation(part, order, Collections.singletonList(randVar));
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

	public String getPatternType() {
		return UsedPattern.BASE_PATTERNS[getTypeMelodyOffset() + 1];
	}

	public int getTypeMelodyOffset() {
		return getTypeMelodyOffset(type);
	}

	public static int getTypeMelodyOffset(String type) {
		if (StringUtils.isEmpty(type)) {
			return 0;
		} else {
			type = type.toUpperCase();
			if (type.startsWith("CHORUS") || type.startsWith("CLIMAX")
					|| type.startsWith("PREVIEW")) {
				return 0;
			}
			if (type.startsWith("VERSE")) {
				return 1;
			}
			if (type.startsWith("INTRO") || type.startsWith("OUTRO") || type.startsWith("BUILDUP")
					|| type.startsWith("BREAKDOWN") || type.startsWith("CHILL")) {
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
		//LG.d("INIT PART MAP FROM OLD DATA!");
		for (int i = 0; i < 5; i++) {
			List<Integer> rowOrders = VibeComposerGUI.getInstList(i).stream()
					.map(e -> e.getPanelOrder()).collect(Collectors.toList());
			Collections.sort(rowOrders);
			Object[][] data = new Object[rowOrders.size()][variationDescriptions[i].length];
			Map<Integer, Integer> oldPresence = getPresenceWithIndices(i);
			//LG.d(i + "'s OldPresence: " + StringUtils.join(oldPresence, ","));
			for (int j = 0; j < rowOrders.size(); j++) {
				data[j][0] = rowOrders.get(j);
				Integer oldIndex = oldPresence.get(rowOrders.get(j));
				if (oldIndex == null) {
					/*LG.d(
							"Failed searching in j/order: " + j + ", value: " + rowOrders.get(j));*/
				} else {
					//LG.d("Found index for j: " + j + ", index: " + oldIndex);
				}
				for (int k = 1; k < variationDescriptions[i].length; k++) {
					if (oldIndex == null) {
						data[j][k] = Boolean.FALSE;
					} else {
						data[j][k] = getBooleanFromOldData(partPresenceVariationMap.get(i),
								oldIndex, k);
					}

				}
			}
			partPresenceVariationMap.put(i, data);
		}
	}

	private Boolean getBooleanFromOldData(Object[][] oldData, int j, int k) {
		if (oldData.length <= j || oldData[j].length <= k) {
			//LG.d("False for j: " + j + ", k: " + k);
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
			Object[][] data = new Object[rowOrders.size()][variationDescriptions[i].length];
			for (int j = 0; j < rowOrders.size(); j++) {
				data[j][0] = rowOrders.get(j);
				for (int k = 1; k < variationDescriptions[i].length; k++) {
					data[j][k] = Boolean.FALSE;
				}
			}
			partPresenceVariationMap.put(i, data);
		}
	}

	public void initPartMapIfNull() {
		if (partPresenceVariationMap.get(0) == null) {
			//LG.d("INITIALIZING PART PRESENCE VARIATION MAP: was null!");
			initPartMap();
		}
	}

	@XmlElement(name = "partVariations")
	public InclusionMapJAXB getPartPresenceVariationMap() {
		if (partPresenceVariationMap.isEmpty()) {
			return null;
		}
		return InclusionMapJAXB.from(partPresenceVariationMap);
	}

	public void setPartPresenceVariationMap(InclusionMapJAXB map) {
		partPresenceVariationMap = InclusionMapJAXB.toMap(map);
	}

	@XmlTransient
	public Map<Integer, Object[][]> getPartMap() {
		return partPresenceVariationMap;
	}


	public void setPartMap(Map<Integer, Object[][]> partPresenceVariationMap) {
		this.partPresenceVariationMap = partPresenceVariationMap;
	}

	@XmlList
	public List<Integer> getSectionVariations() {
		if (sectionVariations != null) {
			while (sectionVariations.size() < sectionVariationNames.length) {
				sectionVariations.add(0);
			}
		}
		return sectionVariations;
	}

	public boolean isSectionVar(int num) {
		return sectionVariations != null && getSectionVariations().get(num) > 0;
	}

	public boolean isTransition() {
		return transitionType > 0;
	}

	public int getTransitionType() {
		return transitionType;
	}

	public void setSectionVariations(List<Integer> sectionVariations) {
		this.sectionVariations = sectionVariations;
	}

	public void setSectionVariation(int order, Integer value) {
		if (sectionVariations == null) {
			List<Integer> sectionVars = new ArrayList<>();
			for (int i = 0; i < Section.sectionVariationNames.length; i++) {
				sectionVars.add(0);
			}
			setSectionVariations(sectionVars);
		}
		sectionVariations.set(order, value);
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

	public double countVariationsForPartAndOrder(int part, int order) {
		if (partPresenceVariationMap == null) {
			return 0;
		}
		double count = 0;
		double total = 0;
		Object[][] data = partPresenceVariationMap.get(part);
		if (data == null || order >= data.length) {
			return 0;
		}
		for (int j = 2; j < data[order].length; j++) {
			if (data[order][j] == Boolean.TRUE) {
				count++;
			}
			total++;
		}
		return count / total;
	}

	public void recalculatePartVariationMapBoundsIfNeeded() {
		boolean needsArrayCopy = false;
		for (int i = 0; i < 5; i++) {
			int actualInstCount = VibeComposerGUI.getInstList(i).size();
			int secInstCount = getPartMap().get(i).length;
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

	public void removeVariationForPart(int part, int partNum, int variationNum) {
		if (variationNum < 2) {
			return;
		}
		initPartMapIfNull();
		partPresenceVariationMap.get(part)[partNum][variationNum] = Boolean.FALSE;
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

	public List<String> getCustomChordsList() {
		if (StringUtils.isEmpty(customChords)) {
			return null;
		} else {
			String[] chords = customChords.split(",");
			List<String> chordList = new ArrayList<>();
			for (String c : chords) {
				chordList.add(c.trim());
			}
			return chordList;
		}
	}

	public void setCustomChords(String customChords) {
		this.customChords = customChords;
	}

	public String getCustomDurations() {
		return customDurations;
	}

	public List<Double> getCustomDurationsList() {
		if (StringUtils.isEmpty(customDurations)) {
			return null;
		} else {
			String[] durations = customDurations.split(",");
			List<Double> durationsList = new ArrayList<>();
			try {
				for (String c : durations) {
					durationsList.add(Double.parseDouble(c));
				}
			} catch (Exception ex) {
				LG.e(">>>>Custom section durations have wrong (not double) values!");
				return null;
			}
			return durationsList;
		}
	}

	public void setCustomDurations(String customDurations) {
		this.customDurations = customDurations;
	}

	public boolean isCustomChordsEnabled() {
		return customChordsEnabled;
	}

	public void setCustomChordsEnabled(boolean customChordsEnabled) {
		this.customChordsEnabled = customChordsEnabled;
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

	public void setTransitionType(int transitionType) {
		this.transitionType = transitionType;
	}

	public boolean isDisplayAlternateChords() {
		return displayAlternateChords;
	}

	public void setDisplayAlternateChords(boolean displayAlternateChords) {
		this.displayAlternateChords = displayAlternateChords;
	}

	public boolean containsPattern(int part, int partOrder) {
		UsedPattern pat = getPattern(part, partOrder);
		if (pat == null || pat.getName() == null) {
			return false;
		}
		return true;
	}

	public List<Double> getGeneratedSectionBeatDurations() {
		return generatedSectionBeatDurations;
	}

	public void setGeneratedSectionBeatDurations(List<Double> generatedSectionBeatDurations) {
		this.generatedSectionBeatDurations = generatedSectionBeatDurations;
	}

	public List<Double> getGeneratedDurations() {
		if (sectionBeatDurations != null) {
			return sectionBeatDurations;
		} else {
			return generatedSectionBeatDurations;
		}
	}

	public SectionConfig getSecConfig() {
		return secConfig;
	}

	public void setSecConfig(SectionConfig secConfig) {
		this.secConfig = secConfig;
	}

	public int getTransposeVariation(int part, int partOrder) {
		if (part == 1 || part == 4) {
			return 0;
		} else {
			List<Integer> vars = getVariation(part, partOrder);
			return (vars != null && vars.contains(0)) ? 12 : 0;
		}
	}

	@XmlElement(name = "pattern")
	public List<UsedPatternMap> getPatterns() {
		if (getGeneratedDurations() == null) {
			return null;
		}
		return patterns;
	}

	public void setPatterns(List<UsedPatternMap> patterns) {
		this.patterns = patterns;
	}

	public void putPattern(int part, int partOrder, UsedPattern pat) {
		patterns.get(part).put(partOrder, pat);
	}

	public UsedPattern getPattern(int part, int partOrder) {
		return patterns.get(part).get(partOrder);
	}

	public List<PhraseNotes> getPatterns(int part) {
		UsedPatternMap upMap = patterns.get(part);
		List<PhraseNotes> patterns = new ArrayList<>();
		for (Integer partOrder : upMap.keySet()) {
			patterns.add(VibeComposerGUI.guiConfig.getPattern(upMap.get(partOrder)));
		}
		return patterns;
	}

	public String getPatternName(int part, int partOrder) {
		UsedPattern pat = getPattern(part, partOrder);
		if (pat == null) {
			return UsedPattern.NONE;
		}
		return pat.getName();
	}

	public boolean isCustomDurationsEnabled() {
		return customDurationsEnabled;
	}

	public void setCustomDurationsEnabled(boolean customDurationsEnabled) {
		this.customDurationsEnabled = customDurationsEnabled;
	}

}
