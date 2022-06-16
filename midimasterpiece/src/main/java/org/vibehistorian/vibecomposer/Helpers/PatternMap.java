package org.vibehistorian.vibecomposer.Helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

@XmlRootElement(name = "PatternMap")
@XmlType(propOrder = {})
public class PatternMap {

	// exists for specific part. - 0 to 4
	// query for a partOrder, get map of pattern names and their notes
	Map<Integer, NamedNotesMap> partOrderPatternMap = new HashMap<>();

	public PatternMap() {
	}

	public void putRaw(Integer partOrder, String name, PhraseNotes notes) {
		put(partOrder, name, notes, false);
	}

	public void put(Integer partOrder, String name, PhraseNotes notes) {
		put(partOrder, name, notes, true);
	}

	public void put(Integer partOrder, String name, PhraseNotes notes, boolean putAsCopy) {
		if (notes != null && putAsCopy) {
			notes = notes.copy();
		}
		if (!partOrderPatternMap.containsKey(partOrder)) {
			partOrderPatternMap.put(partOrder, new NamedNotesMap());
		}
		partOrderPatternMap.get(partOrder).put(name, notes);
	}

	public PhraseNotes getRaw(Integer partOrder, String name) {
		if (!partOrderPatternMap.containsKey(partOrder)) {
			return null;
		}
		return partOrderPatternMap.get(partOrder).get(name);
	}

	public PhraseNotes get(Integer partOrder, String name) {
		PhraseNotes pn = getRaw(partOrder, name);
		return (pn != null) ? pn.copy() : null;
	}

	public List<Integer> getKeys() {
		List<Integer> keys = new ArrayList<>(partOrderPatternMap.keySet());
		Collections.sort(keys);
		return keys;
	}

	public void remove(Integer partOrder) {
		partOrderPatternMap.remove(partOrder);
	}

	public NamedNotesMap getNamedMap(Integer partOrder) {
		return partOrderPatternMap.get(partOrder);
	}

	public Set<String> getPatternNames(Integer partOrder) {
		if (!partOrderPatternMap.containsKey(partOrder)) {
			return null;
		}
		return partOrderPatternMap.get(partOrder).keySet();
	}

	public Map<Integer, NamedNotesMap> getpartOrderPatternMap() {
		return partOrderPatternMap;
	}

	public void setpartOrderPatternMap(Map<Integer, NamedNotesMap> partOrderPatternMap) {
		this.partOrderPatternMap = partOrderPatternMap;
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
		for (Integer key : other.getpartOrderPatternMap().keySet()) {
			for (Entry<String, PhraseNotes> nameNotes : other.getpartOrderPatternMap().get(key)
					.entrySet()) {
				map.put(key, nameNotes.getKey(),
						(nameNotes.getValue() != null) ? nameNotes.getValue().copy() : null);
			}
		}
		return map;
	}

	public static void checkMapBounds(List<PatternMap> patternMaps) {
		if (patternMaps.isEmpty()) {
			patternMaps.addAll(multiMap());
		}


		for (int i = 0; i < 5; i++) {
			PatternMap map = patternMaps.get(i);
			List<Integer> partOrders = VibeComposerGUI.getInstList(i).stream()
					.map(e -> e.getPanelOrder()).collect(Collectors.toList());
			List<Integer> mappartOrdersToRemove = map.getKeys();
			List<Integer> mappartOrdersCopy = new ArrayList<>(mappartOrdersToRemove);
			// remove old partOrders
			/*mappartOrdersToRemove.removeAll(partOrders);
			for (Integer p : mappartOrdersToRemove) {
				map.remove(p);
			}*/

			// add new partOrders
			partOrders.removeAll(mappartOrdersCopy);
			for (Integer p : partOrders) {
				for (String name : UsedPattern.BASE_PATTERNS) {
					map.put(p, name, null);
				}
			}
		}
	}
}
