package org.vibehistorian.vibecomposer.Helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "PatternMap")
@XmlType(propOrder = {})
public class PatternMap {
	// exists for specific inst. - 0 to 4
	// query for a part, get map of pattern names and their notes
	Map<Integer, Map<String, PhraseNotes>> partPatternMap = new HashMap<>();

	public PatternMap() {
	}

	public void putRaw(Integer part, String name, PhraseNotes notes) {
		put(part, name, notes, true);
	}

	public void put(Integer part, String name, PhraseNotes notes) {
		put(part, name, notes, false);
	}

	public void put(Integer part, String name, PhraseNotes notes, boolean putAsCopy) {
		if (notes == null) {
			return;
		}
		if (!partPatternMap.containsKey(part)) {
			partPatternMap.put(part, new HashMap<>());
		}
		partPatternMap.get(part).put(name, putAsCopy ? notes.copy() : notes);
	}

	public PhraseNotes getRaw(Integer part, String name) {
		if (!partPatternMap.containsKey(part)) {
			return null;
		}
		return partPatternMap.get(part).get(name);
	}

	public PhraseNotes get(Integer part, String name) {
		PhraseNotes pn = getRaw(part, name);
		return (pn != null) ? pn.copy() : null;
	}

	public Set<String> getPatternNames(Integer part) {
		if (!partPatternMap.containsKey(part)) {
			return null;
		}
		return partPatternMap.get(part).keySet();
	}

	public Map<Integer, Map<String, PhraseNotes>> getPartPatternMap() {
		return partPatternMap;
	}

	public void setPartPatternMap(Map<Integer, Map<String, PhraseNotes>> partPatternMap) {
		this.partPatternMap = partPatternMap;
	}

	public static List<PatternMap> multiMap() {
		List<PatternMap> multiMap = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			multiMap.add(i, new PatternMap());
		}
		return multiMap;
	}

	public static List<PatternMap> multiMapCopy(List<PatternMap> others) {
		if (others == null) {
			return null;
		}
		List<PatternMap> maps = others.stream().map(e -> PatternMap.mapCopy(e))
				.collect(Collectors.toList());
		return maps;
	}


	public static PatternMap mapCopy(PatternMap other) {
		PatternMap map = new PatternMap();
		if (other == null) {
			return map;
		}
		for (Integer key : other.getPartPatternMap().keySet()) {
			for (Entry<String, PhraseNotes> nameNotes : other.getPartPatternMap().get(key)
					.entrySet()) {
				map.put(key, nameNotes.getKey(),
						(nameNotes.getValue() != null) ? nameNotes.getValue().copy() : null);
			}
		}
		return map;
	}
}
