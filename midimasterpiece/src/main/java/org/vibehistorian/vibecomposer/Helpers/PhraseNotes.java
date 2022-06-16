package org.vibehistorian.vibecomposer.Helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;

import org.apache.commons.lang3.StringUtils;

import jm.music.data.Note;
import jm.music.data.Phrase;

@XmlRootElement(name = "phraseNotes")
@XmlType(propOrder = {})
@XmlSeeAlso({ PhraseNote.class })
public class PhraseNotes extends ArrayList<PhraseNote> implements Cloneable {
	private static final long serialVersionUID = 8933379402297939538L;

	private int partOrder = -1;
	private boolean applied = false;

	public PhraseNotes() {
		super();
	}

	public PhraseNotes(Phrase phr) {
		this(phr.getNoteList());
	}

	public PhraseNotes(List<Note> notes) {
		this();
		addAll(notes.stream().map(e -> new PhraseNote(e)).collect(Collectors.toList()));
	}

	public List<Note> makeNotes() {
		return stream().map(e -> e.toNote()).collect(Collectors.toList());
	}

	public Phrase makePhrase() {
		Phrase phr = new PhraseExt();
		makeNotes().forEach(e -> phr.addNote(e));
		return phr;
	}

	public PhraseNotes copy() {
		PhraseNotes pn = new PhraseNotes(makeNotes());
		pn.setPartOrder(partOrder);
		pn.setApplied(applied);
		return pn;
	}

	public String toStringPitches() {
		return StringUtils.join(stream().map(e -> e.getPitch() >= 0 ? e.getPitch() : -1)
				.collect(Collectors.toList()), ",");
	}

	@XmlElement(name = "phraseNote")
	public List<PhraseNote> getPhraseNotes() {
		return this;
	}

	public int getPartOrder() {
		return partOrder;
	}

	public void setPartOrder(int partOrder) {
		this.partOrder = partOrder;
	}

	public void remakeNoteStartTimes() {
		double current = 0.0;
		for (PhraseNote pn : this) {
			pn.setStartTime(current + pn.getOffset());
			pn.setAbsoluteStartTime(current);
			current += pn.getRv();
		}
	}

	public boolean isApplied() {
		return applied;
	}

	public void setApplied(boolean applied) {
		this.applied = applied;
	}
}
