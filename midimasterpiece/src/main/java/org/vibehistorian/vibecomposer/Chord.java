package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.vibehistorian.vibecomposer.Enums.StrumType;

import jm.music.data.Note;

public class Chord {
	private int[] notes;
	private double flam = 0.0;
	private double rhythmValue = 2;
	private double durationRatio = 1.0;
	private int transpose = 0;
	private int velocity = 69;
	private StrumType strumType = StrumType.ARP_U;
	private List<Note> storedNotesBackwards = null;

	public Chord(int[] notes) {
		this.notes = notes;
	}

	public static Chord EMPTY(double rhythmValue) {
		Chord c = new Chord(new int[] { Integer.MIN_VALUE });
		c.setRhythmValue(rhythmValue);
		return c;
	}

	public int[] getNotes() {
		return notes;
	}

	public void setNotes(int[] notes) {
		this.notes = notes;
	}

	public double getFlam() {
		return flam;
	}

	public void setFlam(double flam) {
		this.flam = flam;
	}

	public double getDuration() {
		return rhythmValue * durationRatio;
	}

	public double getRhythmValue() {
		return rhythmValue;
	}

	public void setRhythmValue(double rhythmValue) {
		this.rhythmValue = rhythmValue;
	}

	public double getDurationRatio() {
		return durationRatio;
	}

	public void setDurationRatio(double durationRatio) {
		this.durationRatio = durationRatio;
	}

	public int getTranspose() {
		return transpose;
	}

	public void setTranspose(int transpose) {
		this.transpose = transpose;
	}

	public int getVelocity() {
		return velocity;
	}

	public void setVelocity(int velocity) {
		this.velocity = velocity;
	}

	public static Chord copy(Chord c) {
		Chord c2 = Chord.EMPTY(c.getRhythmValue());
		c2.durationRatio = c.durationRatio;
		c2.flam = c.flam;
		c2.notes = Arrays.copyOf(c.notes, c.notes.length);
		c2.transpose = c.transpose;
		c2.velocity = c.velocity;
		c2.strumType = c.strumType;
		return c2;
	}

	public void makeAndStoreNotesBackwards(Random gen) {
		List<Note> noteList = new ArrayList<>();
		for (int i = notes.length - 1; i >= 0; i--) {
			int pitch = notes[i];
			if (pitch > 0) {
				pitch += transpose;
			}
			double rhythm = (i > 0) ? 0.0 : rhythmValue;
			double duration = getDuration();
			Note n = new Note(pitch, rhythm, velocity);
			n.setDuration(duration * Note.DEFAULT_DURATION_MULTIPLIER);
			//n.setOffset(flam * i);
			noteList.add(n);
		}
		StrumType.adjustNoteOffsets(strumType, noteList, flam, gen);
		storedNotesBackwards = noteList;
	}

	public List<Note> getNotesBackwards() {
		if (storedNotesBackwards != null) {
			return storedNotesBackwards;
		}
		List<Note> noteList = new ArrayList<>();
		for (int i = notes.length - 1; i >= 0; i--) {
			int pitch = notes[i];
			if (pitch > 0) {
				pitch += transpose;
			}
			double rhythm = (i > 0) ? 0.0 : rhythmValue;
			double duration = getDuration();
			Note n = new Note(pitch, rhythm, velocity);
			n.setDuration(duration * Note.DEFAULT_DURATION_MULTIPLIER);
			//n.setOffset(flam * i);
			noteList.add(n);
		}
		StrumType defaultType = StrumType.ARP_U;
		StrumType.adjustNoteOffsets(defaultType, noteList, flam, null);
		return noteList;
	}

	public StrumType getStrumType() {
		return strumType;
	}

	public void setStrumType(StrumType strumType) {
		this.strumType = strumType;
	}

	public List<Note> getStoredNotesBackwards() {
		return storedNotesBackwards;
	}

	public void setStoredNotesBackwards(List<Note> storedNotesBackwards) {
		this.storedNotesBackwards = storedNotesBackwards;
	}
}
