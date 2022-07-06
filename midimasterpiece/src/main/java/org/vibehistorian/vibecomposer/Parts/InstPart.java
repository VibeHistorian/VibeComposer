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

package org.vibehistorian.vibecomposer.Parts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlList;

import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Panels.InstPanel;

public abstract class InstPart implements Cloneable {
	protected int instrument = 46;

	protected int hitsPerPattern = 8;
	protected int chordSpan = 1;
	protected ChordSpanFill chordSpanFill = ChordSpanFill.ALL;
	protected boolean fillFlip = false;

	protected int chordNotesStretch = 3;
	protected boolean stretchEnabled = false;

	protected int pauseChance = 0;
	protected int exceptionChance = 5;
	protected int patternRepeat = 1;

	protected int offset = 0;
	protected int transpose = 0;
	protected int feedbackCount = 0;
	protected int feedbackDuration = 500;
	protected int feedbackVol = 80;

	protected int velocityMin = 69;
	protected int velocityMax = 90;
	protected int noteLengthMultiplier = 100;

	protected int swingPercent = 50;
	protected int accents = 50;

	protected int order = 1;

	protected int patternSeed = 0;
	protected RhythmPattern pattern = RhythmPattern.FULL;
	protected List<Integer> customPattern = null;
	protected List<Integer> customVelocities = null;
	protected boolean patternFlip = false;

	protected int patternShift = 0;

	protected int sliderVolume = 100;
	protected int sliderPan = 50;

	protected int midiChannel = 10;

	protected boolean muted = false;

	protected int partNum = 0;

	protected PhraseNotes customMidi = null;

	public void setFromPanel(InstPanel panel, int lastRandomSeed) {
		setInstrument(panel.getInstrument());

		setHitsPerPattern(panel.getHitsPerPattern());
		setChordSpan(panel.getChordSpan());
		setChordSpanFill(panel.getChordSpanFill());
		setFillFlip(panel.getFillFlip());

		setChordNotesStretch(panel.getChordNotesStretch());
		setStretchEnabled(panel.getStretchEnabled());

		setPauseChance(panel.getPauseChance());
		setExceptionChance(panel.getExceptionChance());
		setPatternRepeat(panel.getPatternRepeat());

		setTranspose(panel.getTranspose());
		setOffset(panel.getOffset());
		setFeedbackDuration(panel.getFeedbackDuration());
		setFeedbackCount(panel.getFeedbackCount());
		setFeedbackVol(panel.getFeedbackVol());

		setVelocityMin(panel.getVelocityMin());
		setVelocityMax(panel.getVelocityMax());
		if (velocityMax <= velocityMin) {
			velocityMax = velocityMin + 1;
			panel.setVelocityMax(velocityMax);
		}

		setNoteLengthMultiplier(panel.getNoteLengthMultiplier());

		setSwingPercent(panel.getSwingPercent());
		setAccents(panel.getAccents());

		setCustomPattern(
				panel.getComboPanel() != null ? panel.getComboPanel().getTruePattern() : null);
		setCustomVelocities(
				panel.getComboPanel() != null ? panel.getComboPanel().getVelocities() : null);

		setPatternSeed((panel.getPatternSeed() != 0) ? panel.getPatternSeed() : lastRandomSeed);
		setPattern(panel.getPattern());
		setPatternFlip(panel.getPatternFlip());
		setPatternShift(panel.getPatternShift());

		setMuted(panel.getMuteInst());

		setSliderVolume(panel.getVolSlider().getValue());
		setSliderPan(panel.getPanSlider().getValue());

		setCustomMidi(panel.getCustomMidi());

		setMidiChannel(panel.getMidiChannel());

	}

	public int getOffset() {
		return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}

	public int getInstrument() {
		return instrument;
	}

	public void setInstrument(int instrument) {
		this.instrument = instrument;
	}

	public int getHitsPerPattern() {
		return hitsPerPattern;
	}

	public void setHitsPerPattern(int hitsPerPattern) {
		this.hitsPerPattern = hitsPerPattern;
	}

	public int getChordSpan() {
		return chordSpan;
	}

	public void setChordSpan(int chordSpan) {
		this.chordSpan = chordSpan;
	}

	public int getPauseChance() {
		return pauseChance;
	}

	public void setPauseChance(int pauseChance) {
		this.pauseChance = pauseChance;
	}

	public int getExceptionChance() {
		return exceptionChance;
	}

	public void setExceptionChance(int exceptionChance) {
		this.exceptionChance = exceptionChance;
	}

	public int getPatternRepeat() {
		return patternRepeat;
	}

	public void setPatternRepeat(int patternRepeat) {
		this.patternRepeat = patternRepeat;
	}

	public int getTranspose() {
		return transpose;
	}

	public void setTranspose(int transpose) {
		this.transpose = transpose;
	}

	public int getPatternSeed() {
		return patternSeed;
	}

	public int getPatternSeedWithPartOffset() {
		return patternSeed + partNum * 10000;
	}

	public void setPatternSeed(int patternSeed) {
		this.patternSeed = patternSeed;
	}

	public RhythmPattern getPattern() {
		return pattern;
	}

	public void setPattern(RhythmPattern pattern) {
		this.pattern = pattern;
	}

	public int getPatternShift() {
		return patternShift;
	}

	public void setPatternShift(int patternShift) {
		this.patternShift = patternShift;
	}

	public boolean isMuted() {
		return muted;
	}

	public void setMuted(boolean muted) {
		this.muted = muted;
	}

	public int getMidiChannel() {
		return midiChannel;
	}

	public void setMidiChannel(int midiChannel) {
		this.midiChannel = midiChannel;
	}

	public ChordSpanFill getChordSpanFill() {
		return chordSpanFill;
	}

	public void setChordSpanFill(ChordSpanFill cspanFill) {
		this.chordSpanFill = cspanFill;
	}

	public int getChordNotesStretch() {
		return chordNotesStretch;
	}

	public void setChordNotesStretch(int chordStretch) {
		this.chordNotesStretch = chordStretch;
	}

	public boolean isStretchEnabled() {
		return stretchEnabled;
	}

	public void setStretchEnabled(boolean stretchEnabled) {
		this.stretchEnabled = stretchEnabled;
	}


	public int getVelocityMin() {
		return velocityMin;
	}

	public void setVelocityMin(int velocityMin) {
		this.velocityMin = velocityMin;
	}

	public int getVelocityMax() {
		return velocityMax;
	}

	public void setVelocityMax(int velocityMax) {
		this.velocityMax = velocityMax;
	}

	public int getSliderVolume() {
		return sliderVolume;
	}

	public void setSliderVolume(int sliderVolume) {
		this.sliderVolume = sliderVolume;
	}

	public int getSliderPan() {
		return sliderPan;
	}

	public void setSliderPan(int sliderPan) {
		this.sliderPan = sliderPan;
	}

	public int getSwingPercent() {
		return swingPercent;
	}

	public void setSwingPercent(int swingPercent) {
		this.swingPercent = swingPercent;
	}

	public int getAccents() {
		return accents;
	}

	public void setAccents(int accents) {
		this.accents = accents;
	}

	public boolean isFillFlip() {
		return fillFlip;
	}

	public void setFillFlip(boolean fillFlip) {
		this.fillFlip = fillFlip;
	}

	public boolean isPatternFlip() {
		return patternFlip;
	}

	public void setPatternFlip(boolean patternFlip) {
		this.patternFlip = patternFlip;
	}

	public int getNoteLengthMultiplier() {
		return noteLengthMultiplier;
	}

	public void setNoteLengthMultiplier(int noteLengthMultiplier) {
		this.noteLengthMultiplier = noteLengthMultiplier;
	}

	@XmlAttribute
	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}


	@XmlList
	public List<Integer> getCustomPattern() {
		return customPattern;
	}


	public void setCustomPattern(List<Integer> customPattern) {
		this.customPattern = customPattern;
	}

	@XmlList
	public List<Integer> getCustomVelocities() {
		return customVelocities;
	}

	public void setCustomVelocities(List<Integer> customVelocities) {
		this.customVelocities = customVelocities;
	}

	public List<Integer> getFinalPatternCopy() {
		List<Integer> premadePattern = null;
		if (getPattern() != RhythmPattern.CUSTOM) {
			RhythmPattern d = getPattern();
			int shift = getPatternShift();
			int hits = getHitsPerPattern();
			premadePattern = ((d == RhythmPattern.EUCLID) && (getCustomPattern() != null))
					? RhythmPattern.makeEuclideanPattern(hits,
							(int) getCustomPattern().subList(0, hits).stream().filter(e -> e > 0)
									.count(),
							shift, null)
					: d.getPatternByLength(hits, shift);
		} else {
			List<Integer> premadeCopy = new ArrayList<>(getCustomPattern());
			Collections.rotate(premadeCopy, getPatternShift());
			premadePattern = premadeCopy;
		}
		return premadePattern;
	}

	public Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public static List<? extends InstPart> sortParts(List<? extends InstPart> parts) {
		Collections.sort(parts, Comparator.comparing(e -> e.getOrder()));
		return parts;
	}

	public abstract int getPartNum();

	public int getAbsoluteOrder() {
		return VibeComposerGUI.getAbsoluteOrder(getPartNum(), getOrder());
	}

	public PhraseNotes getCustomMidi() {
		return customMidi;
	}

	public void setCustomMidi(PhraseNotes customMidi) {
		this.customMidi = customMidi;
	}


	public String partInfo() {
		return "Part: " + getPartNum() + ", order: " + getOrder();
	}

	public int getFeedbackCount() {
		return feedbackCount;
	}

	public void setFeedbackCount(int feedbackCount) {
		this.feedbackCount = feedbackCount;
	}

	public int getFeedbackDuration() {
		return feedbackDuration;
	}

	public void setFeedbackDuration(int feedbackDuration) {
		this.feedbackDuration = feedbackDuration;
	}

	public int getFeedbackVol() {
		return feedbackVol;
	}

	public void setFeedbackVol(int feedbackVol) {
		this.feedbackVol = feedbackVol;
	}
}
