package org.vibehistorian.vibecomposer.Helpers;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import jm.music.data.Note;

@XmlRootElement(name = "PhraseNote")
@XmlType(propOrder = {})
public class PhraseNote implements Cloneable {
	private int pitch;
	private int dynamic;
	private double rv;
	private double duration;
	private double offset;

	private double startTime;
	private double absoluteStartTime;

	public PhraseNote(Note n) {
		super();
		this.pitch = n.getPitch();
		this.dynamic = n.getDynamic();
		this.rv = n.getRhythmValue();
		this.duration = n.getDuration();
		this.offset = n.getOffset();
	}

	public PhraseNote() {
		this(60, 69, 1.0, 1.0, 0.0);
	}

	public PhraseNote(int pitch) {
		this(pitch >= 0 ? pitch : Note.REST, 69, 1.0, 1.0, 0.0);
	}

	public PhraseNote(int pitch, int dynamic, double rv, double duration, double offset) {
		super();
		this.pitch = pitch;
		this.dynamic = dynamic;
		this.rv = rv;
		this.duration = duration;
		this.offset = offset;
	}

	public Note toNote() {
		Note n = new Note(pitch, rv, dynamic);
		n.setDuration(duration);
		n.setOffset(offset);
		return n;
	}

	public int getPitch() {
		return pitch;
	}

	public void setPitch(int pitch) {
		this.pitch = pitch >= 0 ? pitch : Note.REST;
	}

	public int getDynamic() {
		return dynamic;
	}

	public void setDynamic(int dynamic) {
		this.dynamic = dynamic;
	}

	public double getRv() {
		return rv;
	}

	public void setRv(double rv) {
		this.rv = rv;
	}

	public double getDuration() {
		return duration;
	}

	public void setDuration(double duration) {
		this.duration = duration;
	}

	public double getOffset() {
		return offset;
	}

	public void setOffset(double offset) {
		this.offset = offset;
	}

	@XmlTransient
	public double getAbsoluteStartTime() {
		return absoluteStartTime;
	}

	public void setAbsoluteStartTime(double absoluteStartTime) {
		this.absoluteStartTime = absoluteStartTime;
	}

	@XmlTransient
	public double getStartTime() {
		return startTime;
	}

	public void setStartTime(double startTime) {
		this.startTime = startTime;
	}

	public double getStart(boolean offsetted) {
		return offsetted ? startTime : absoluteStartTime;
	}

	@Override
	public PhraseNote clone() {
		try {
			return (PhraseNote) super.clone();
		} catch (CloneNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
