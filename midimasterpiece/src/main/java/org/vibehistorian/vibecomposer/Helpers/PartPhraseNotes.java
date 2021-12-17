package org.vibehistorian.vibecomposer.Helpers;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;

import jm.music.data.Phrase;

@XmlRootElement(name = "partPhraseNotes")
@XmlType(propOrder = {})
@XmlSeeAlso({ PhraseNotes.class })
public class PartPhraseNotes extends ArrayList<PhraseNotes> {
	private static final long serialVersionUID = 2721039341055180730L;

	private int part = 0;

	@XmlElement(name = "phraseNotes")
	public PartPhraseNotes getPartPhraseNotes() {
		return this;
	}

	public PartPhraseNotes() {
		super();
	}

	public PartPhraseNotes(List<Phrase> phrases) {
		this();

		int counter = 0;
		for (Phrase phr : phrases) {
			PhraseNotes pn = new PhraseNotes(phr);
			pn.setPartOrder(counter++);
			add(pn);
		}
	}

	public PartPhraseNotes(List<PhraseNotes> phraseNotes, Boolean foo) {
		this();

		addAll(phraseNotes);
	}

	public PartPhraseNotes(List<Phrase> phrases, int part) {
		this(phrases);
		setPart(part);
	}

	public int getPart() {
		return part;
	}

	public void setPart(int part) {
		this.part = part;
	}
}
