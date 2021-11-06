package org.vibehistorian.vibecomposer.Helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import jm.music.data.Note;
import jm.music.data.Phrase;

@XmlRootElement(name = "PhraseNotes")
@XmlType(propOrder = {})
public class PhraseNotes {
	private List<Integer> pitches;
	private List<Integer> dynamics;
	private List<Double> rv;
	private List<Double> durations;

	public PhraseNotes() {
		pitches = new ArrayList<>();
		dynamics = new ArrayList<>();
		rv = new ArrayList<>();
		durations = new ArrayList<>();
	}

	public PhraseNotes(Phrase phr) {
		this(phr.getNoteList());
	}

	public PhraseNotes(List<Note> notes) {
		if (notes != null) {
			pitches = notes.stream().map(e -> e.getPitch()).mapToInt(e -> e).boxed()
					.collect(Collectors.toList());
			dynamics = notes.stream().map(e -> e.getDynamic()).mapToInt(e -> e).boxed()
					.collect(Collectors.toList());
			rv = notes.stream().map(e -> e.getRhythmValue()).mapToDouble(e -> e).boxed()
					.collect(Collectors.toList());
			durations = notes.stream().map(e -> e.getDuration()).mapToDouble(e -> e).boxed()
					.collect(Collectors.toList());
		}
	}

	public List<Note> makeNotes() {
		if (pitches != null) {
			List<Note> notes = new ArrayList<>();
			for (int i = 0; i < pitches.size(); i++) {
				Note n = new Note(pitches.get(i), rv.get(i), dynamics.get(i));
				n.setDuration(durations.get(i));
				notes.add(n);
			}
			return notes;
		}
		return null;
	}

	public Phrase makePhrase() {
		if (pitches != null) {
			Phrase phr = new Phrase();
			makeNotes().forEach(e -> phr.addNote(e));
			return phr;
		}
		return null;
	}

	@XmlList
	public List<Integer> getPitches() {
		return pitches;
	}

	public void setPitches(List<Integer> pitches) {
		this.pitches = pitches;
	}

	@XmlList
	public List<Integer> getDynamics() {
		return dynamics;
	}

	public void setDynamics(List<Integer> dynamics) {
		this.dynamics = dynamics;
	}

	@XmlList
	public List<Double> getRv() {
		return rv;
	}

	public void setRv(List<Double> rv) {
		this.rv = rv;
	}

	@XmlList
	public List<Double> getDurations() {
		return durations;
	}

	public void setDurations(List<Double> durations) {
		this.durations = durations;
	}


}
