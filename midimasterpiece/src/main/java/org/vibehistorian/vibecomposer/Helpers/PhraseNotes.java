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
public class PhraseNotes extends ArrayList<PhraseNote> {
	private static final long serialVersionUID = 8933379402297939538L;

	private boolean isCustom = false;

	public PhraseNotes() {
		super();
	}

	public PhraseNotes(Phrase phr) {
		this(phr.getNoteList());
	}

	public PhraseNotes(List<Note> notes) {
		super();
		addAll(notes.stream().map(e -> new PhraseNote(e)).collect(Collectors.toList()));
	}

	public List<Note> makeNotes() {
		return stream().map(e -> e.toNote()).collect(Collectors.toList());
	}

	public Phrase makePhrase() {
		Phrase phr = new Phrase();
		makeNotes().forEach(e -> phr.addNote(e));
		return phr;
	}

	public String toStringPitches() {
		return StringUtils.join(stream().map(e -> e.getPitch() >= 0 ? e.getPitch() : -1)
				.collect(Collectors.toList()), ",");
	}

	@XmlElement(name = "phraseNote")
	public List<PhraseNote> getPhraseNotes() {
		return isCustom ? this : null;
	}

	public boolean isCustom() {
		return isCustom;
	}

	public void setCustom(boolean isCustom) {
		this.isCustom = isCustom;
	}
}
