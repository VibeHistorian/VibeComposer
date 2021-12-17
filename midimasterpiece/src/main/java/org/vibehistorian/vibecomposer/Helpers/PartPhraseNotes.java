package org.vibehistorian.vibecomposer.Helpers;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "partPhraseNotes")
@XmlType(propOrder = {})
@XmlSeeAlso({ PhraseNotes.class })
public class PartPhraseNotes extends ArrayList<PhraseNotes> {
	private static final long serialVersionUID = 2721039341055180730L;

	@XmlElement(name = "phraseNotes")
	public List<PhraseNotes> getPartPhraseNotes() {
		return this;
	}

}
