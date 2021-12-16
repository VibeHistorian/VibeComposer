package org.vibehistorian.vibecomposer.Helpers;

import java.util.ArrayList;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "partPhraseNotes")
@XmlAccessorType(XmlAccessType.FIELD)
public class PartPhraseNotes extends ArrayList<PhraseNotes> {
	private static final long serialVersionUID = 2721039341055180730L;

}
