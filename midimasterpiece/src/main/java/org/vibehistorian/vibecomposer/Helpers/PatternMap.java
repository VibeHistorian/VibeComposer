package org.vibehistorian.vibecomposer.Helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

@XmlRootElement(name = "PatternMap")
@XmlType(propOrder = {})
@XmlSeeAlso({ NamedNotesMap.class })
public class PatternMap {

	// exists for specific part. - 0 to 4
	// query for a partOrder, get map of pattern names and their notes
	Map<Integer, NamedNotesMap> partOrderPatternMap = new HashMap<>();
	int part = 0;

	public PatternMap() {
	}

	public PatternMap(int part) {
		this.part = part;
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
		partOrderPatternMap.get(partOrder).getMap().put(name, notes);
	}

	public PhraseNotes getRaw(Integer partOrder, String name) {
		if (!partOrderPatternMap.containsKey(partOrder)) {
			return null;
		}
		return partOrderPatternMap.get(partOrder).getMap().get(name);
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

	public void removePattern(Integer partOrder, String patName) {
		partOrderPatternMap.get(partOrder).getMap().remove(patName);
	}

	public NamedNotesMap getNamedMap(Integer partOrder) {
		return partOrderPatternMap.get(partOrder);
	}

	public Set<String> getPatternNames(Integer partOrder) {
		if (!partOrderPatternMap.containsKey(partOrder)) {
			return null;
		}
		return new HashSet<>(partOrderPatternMap.get(partOrder).getMap().keySet());
	}

	@XmlElement
	public Map<Integer, NamedNotesMap> getPartOrderPatternMap() {
		return partOrderPatternMap;
	}

	public void setPartOrderPatternMap(Map<Integer, NamedNotesMap> partOrderPatternMap) {
		this.partOrderPatternMap = partOrderPatternMap;
	}

	public static List<PatternMap> multiMap() {
		List<PatternMap> multiMap = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			multiMap.add(i, new PatternMap(i));
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
		for (Integer key : other.getPartOrderPatternMap().keySet()) {
			for (Entry<String, PhraseNotes> nameNotes : other.getPartOrderPatternMap().get(key)
					.getMap().entrySet()) {
				map.put(key, nameNotes.getKey(),
						(nameNotes.getValue() != null) ? nameNotes.getValue().copy() : null);
			}
		}
		return map;
	}

	public static void checkMapBounds(List<PatternMap> patternMaps,
			boolean removeOldForNewArrangement) {
		if (patternMaps.isEmpty()) {
			patternMaps.addAll(multiMap());
		}


		for (int i = 0; i < 5; i++) {
			PatternMap map = patternMaps.get(i);
			List<Integer> partOrders = VibeComposerGUI.getInstList(i).stream()
					.map(e -> e.getPanelOrder()).collect(Collectors.toList());
			List<Integer> mapPartOrdersToRemove = map.getKeys();
			List<Integer> mapPartOrdersCopy = new ArrayList<>(mapPartOrdersToRemove);
			if (removeOldForNewArrangement) {
				// remove old partOrders
				mapPartOrdersToRemove.removeAll(partOrders);
				for (Integer p : mapPartOrdersToRemove) {
					map.remove(p);
				}

				// remove old generated
				for (Integer k : map.getKeys()) {
					Map<String, PhraseNotes> nnm = map.getNamedMap(k).getMap();
					Set<String> names = nnm.keySet().stream()
							.filter(e -> e.startsWith(UsedPattern.GENERATED))
							.collect(Collectors.toSet());
					for (String n : names) {
						nnm.remove(n);
					}
				}
			}

			// add new partOrders
			partOrders.removeAll(mapPartOrdersCopy);
			for (Integer p : partOrders) {
				for (String name : UsedPattern.BASE_PATTERNS) {
					map.put(p, name, null);
				}
			}
		}
	}

	@XmlAttribute
	public int getPart() {
		return part;
	}


	public void setPart(int part) {
		this.part = part;
	}
}
