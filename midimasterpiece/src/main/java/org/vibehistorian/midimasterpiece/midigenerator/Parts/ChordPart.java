package org.vibehistorian.midimasterpiece.midigenerator.Parts;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils;
import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils.POOL;

@XmlRootElement(name = "chordPart")
@XmlType(propOrder = {})
public class ChordPart extends InstPart {
	private int transitionChance = 0;
	private int transitionSplit = 625;
	
	private int strum = 0;
	
	
	private MidiUtils.POOL instPool = MidiUtils.POOL.PLUCK;
	
	public ChordPart() {
		
	}
	
	
	public int getTransitionChance() {
		return transitionChance;
	}
	
	public void setTransitionChance(int transitionChance) {
		this.transitionChance = transitionChance;
	}
	
	public int getTransitionSplit() {
		return transitionSplit;
	}
	
	public void setTransitionSplit(int transitionSplit) {
		this.transitionSplit = transitionSplit;
	}
	
	public int getStrum() {
		return strum;
	}
	
	public void setStrum(int strum) {
		this.strum = strum;
	}
	
	
	@XmlAttribute
	public int getOrder() {
		return order;
	}
	
	public void setOrder(int order) {
		this.order = order;
	}
	
	
	public MidiUtils.POOL getInstPool() {
		return instPool;
	}
	
	public void setInstPool(MidiUtils.POOL instPool) {
		this.instPool = instPool;
	}
	
}
