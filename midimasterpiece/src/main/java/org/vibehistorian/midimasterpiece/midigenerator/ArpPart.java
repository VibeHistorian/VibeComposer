package org.vibehistorian.midimasterpiece.midigenerator;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "arpPart")
@XmlType(propOrder = {})
public class ArpPart extends InstPart {
	
	public ArpPart() {
		
	}
	
	public ArpPart(int instrument, int hitsPerPattern, int chordSpan, int pauseChance,
			int exceptionChance, boolean repeatableNotes, int patternRepeat, int transpose,
			int order, int patternSeed, RhythmPattern pattern, int patternShift, boolean muted,
			int midiChannel, ChordSpanFill fill) {
		super();
		this.instrument = instrument;
		this.hitsPerPattern = hitsPerPattern;
		this.chordSpan = chordSpan;
		this.pauseChance = pauseChance;
		this.exceptionChance = exceptionChance;
		this.repeatableNotes = repeatableNotes;
		this.patternRepeat = patternRepeat;
		this.transpose = transpose;
		this.order = order;
		this.patternSeed = patternSeed;
		this.pattern = pattern;
		this.patternShift = patternShift;
		this.muted = muted;
		this.midiChannel = midiChannel;
		this.chordSpanFill = fill;
	}
	
	@XmlAttribute
	public int getOrder() {
		return order;
	}
	
	public void setOrder(int order) {
		this.order = order;
	}
	
}
