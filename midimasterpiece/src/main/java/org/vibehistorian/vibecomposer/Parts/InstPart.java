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

package org.vibehistorian.vibecomposer.Parts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlList;

import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Panels.InstPanel;

public abstract class InstPart implements Cloneable {
	protected int instrument = 46;

	protected int hitsPerPattern = 8;
	protected int chordSpan = 1;
	protected ChordSpanFill chordSpanFill = ChordSpanFill.ALL;
	protected boolean fillFlip = false;

	protected int chordNotesStretch = 3;
	protected boolean stretchEnabled = false;

	protected int pauseChance = 20;
	protected int exceptionChance = 5;
	protected boolean repeatableNotes = true;
	protected int patternRepeat = 1;

	protected int delay = 0;
	protected int transpose = 0;

	protected int velocityMin = 70;
	protected int velocityMax = 90;

	protected int swingPercent = 50;


	protected int order = 1;

	protected int patternSeed = 0;
	protected RhythmPattern pattern = RhythmPattern.FULL;
	protected List<Integer> customPattern = null;
	protected boolean patternFlip = false;

	protected int patternShift = 0;

	protected int sliderVolume = 100;

	protected int midiChannel = 10;

	protected boolean muted = false;

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
		setRepeatableNotes(panel.getRepeatableNotes());
		setPatternRepeat(panel.getPatternRepeat());

		setTranspose(panel.getTranspose());
		setDelay(panel.getDelay());

		setVelocityMin(panel.getVelocityMin());
		setVelocityMax(panel.getVelocityMax());
		if (velocityMax <= velocityMin) {
			velocityMax = velocityMin + 1;
			panel.setVelocityMax(velocityMax);
		}

		setSwingPercent(panel.getSwingPercent());

		setCustomPattern(
				panel.getComboPanel() != null ? panel.getComboPanel().getTruePattern() : null);

		setPatternSeed((panel.getPatternSeed() != 0) ? panel.getPatternSeed() : lastRandomSeed);
		setPattern(panel.getPattern());
		setPatternFlip(panel.getPatternFlip());
		setPatternShift(panel.getPatternShift());

		setMuted(panel.getMuteInst());

		setSliderVolume(panel.getVolSlider().getValue());

		setMidiChannel(panel.getMidiChannel());

	}

	public int getDelay() {
		return delay;
	}

	public void setDelay(int delay) {
		this.delay = delay;
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

	public boolean isRepeatableNotes() {
		return repeatableNotes;
	}

	public void setRepeatableNotes(boolean repeatableNotes) {
		this.repeatableNotes = repeatableNotes;
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

	public int getSwingPercent() {
		return swingPercent;
	}

	public void setSwingPercent(int swingPercent) {
		this.swingPercent = swingPercent;
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

	public List<Integer> getFinalPatternCopy() {
		List<Integer> premadePattern = null;
		if (getPattern() != RhythmPattern.CUSTOM) {
			premadePattern = getPattern().getPatternByLength(getHitsPerPattern(),
					getPatternShift());
		} else {
			List<Integer> premadeCopy = new ArrayList<>(getCustomPattern());
			Collections.rotate(premadeCopy, getPatternShift());
			premadePattern = premadeCopy;
		}
		return premadePattern;
	}


	public void setCustomPattern(List<Integer> customPattern) {
		this.customPattern = customPattern;
	}

	public Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}
