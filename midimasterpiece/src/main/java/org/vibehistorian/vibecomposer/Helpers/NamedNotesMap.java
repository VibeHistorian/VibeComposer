package org.vibehistorian.vibecomposer.Helpers;

import java.util.HashMap;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "NamedNotesMap")
@XmlType(propOrder = {})
public class NamedNotesMap extends HashMap<String, PhraseNotes> {

	private static final long serialVersionUID = -5573846527689276057L;
}
