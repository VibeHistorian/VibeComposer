package org.vibehistorian.vibecomposer.Helpers;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.LG;

@XmlRootElement(name = "namedNotesMap")
@XmlType(propOrder = {})
@XmlSeeAlso({ PhraseNotes.class })
public class NamedNotesMap {

	private Map<String, PhraseNotes> namedNotesMap = new LinkedHashMap<>();

	public NamedNotesMap() {
	}

	public Map<String, PhraseNotes> getNamedNotesMap() {
		Map<String, PhraseNotes> appliedPatterns = new HashMap<>();
		for (Entry<String, PhraseNotes> pattern : namedNotesMap.entrySet()) {
			if (pattern != null && pattern.getValue() != null && pattern.getValue().isApplied()) {
				appliedPatterns.put(pattern.getKey(), pattern.getValue());
			}
		}
		return appliedPatterns;
	}

	public Map<String, PhraseNotes> getMap() {
		return namedNotesMap;
	}

	public void setNamedNotesMap(Map<String, PhraseNotes> namedNotesMap) {
		for (Entry<String, PhraseNotes> s : namedNotesMap.entrySet()) {
			LG.i(s.getKey() + " :|||: " + s.getValue().toStringPitches());
		}
		this.namedNotesMap = namedNotesMap;
	}


}
