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
	private Phrase chordSlash;

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
}
