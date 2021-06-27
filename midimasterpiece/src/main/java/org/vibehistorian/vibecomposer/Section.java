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
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.commons.lang3.StringUtils;

import jm.music.data.CPhrase;
import jm.music.data.Phrase;

@XmlRootElement(name = "section")
@XmlType(propOrder = {})
public class Section {
	public enum SectionType {
		INTRO, VERSE1, VERSE2, CHORUS1, BREAKDOWN, CHILL, VERSE3, BUILDUP, CHORUS2, ADVANCED_CHORUS,
		OUTRO;
	}

	public static final String[][] variationDescriptions = {
			{ "#", "Incl.", "Transpose", "MaxJump" }, { "#", "Incl.", "OffsetSeed" },
			{ "#", "Incl.", "Transpose", "IgnoreFill", "UpStretch" },
			{ "#", "Incl.", "Transpose", "IgnoreFill", "ForceRandOct" },
			{ "#", "Incl.", "IgnoreFill", "MoreExceptions" } };

	public static final int VARIATION_CHANCE = 30;

	private String type;
	private int measures;

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
	private Phrase chordSlash;

	// map integer(what), map integer(part order), list integer(section variation)
	private Map<Integer, Object[][]> partPresenceVariationMap = new HashMap<>();

	private List<Boolean> riskyVariations = null;
	public static final int RISKY_VARIATION_COUNT = 3;

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

	public Section(Section orig) {
		this();
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

	public Phrase getChordSlash() {
		return chordSlash;
	}

	@XmlTransient
	public void setChordSlash(Phrase chordSlash) {
		this.chordSlash = chordSlash;
	}

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
			Object[][] data = new Object[partPresenceVariationMap
					.get(i).length][partPresenceVariationMap.get(i)[0].length];
			for (int k = 0; k < data.length; k++) {
				data[k] = partPresenceVariationMap.get(i)[k].clone();
			}
			dataCopy.put(i, data);
		}
		sec.partPresenceVariationMap = dataCopy;
		return sec;
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

	@XmlTransient
	public void setPartPresenceVariationMap(Map<Integer, Object[][]> partPresenceVariationMap) {
		this.partPresenceVariationMap = partPresenceVariationMap;
	}

	public List<Boolean> getRiskyVariations() {
		return riskyVariations;
	}

	public void setRiskyVariations(List<Boolean> riskyVariations) {
		this.riskyVariations = riskyVariations;
	}

	public void setRiskyVariation(int order, Boolean value) {
		if (riskyVariations == null) {
			List<Boolean> riskyVars = new ArrayList<>();
			for (int i = 0; i < Section.RISKY_VARIATION_COUNT; i++) {
				riskyVars.add(Boolean.FALSE);
			}
			setRiskyVariations(riskyVars);
		}
		riskyVariations.set(order, value);
	}
}
