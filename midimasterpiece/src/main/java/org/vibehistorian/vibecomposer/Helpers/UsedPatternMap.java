package org.vibehistorian.vibecomposer.Helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "UsedPatternMap")
@XmlType(propOrder = {})
public class UsedPatternMap extends HashMap<Integer, UsedPattern> {

	private static final long serialVersionUID = -6053638740549359136L;
	int part = 0;


	public UsedPatternMap() {
	}

	public UsedPatternMap(int part) {
		this.part = part;
	}

	public UsedPatternMap(UsedPatternMap other) {
		super.putAll(other);
	}

	public static List<UsedPatternMap> multiMap() {
		List<UsedPatternMap> multiMap = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			multiMap.add(i, new UsedPatternMap(i));
		}
		return multiMap;
	}

	public static List<UsedPatternMap> multiMapCopy(List<UsedPatternMap> others) {
		if (others == null) {
			return null;
		}
		List<UsedPatternMap> maps = others.stream().map(e -> new UsedPatternMap(e))
				.collect(Collectors.toList());
		return maps;
	}

	@XmlElement(name = "usedPatternMap")
	public UsedPatternMap getUsedPatternMap() {
		return this;
	}

	@XmlAttribute
	public int getPart() {
		return part;
	}

	public void setPart(int part) {
		this.part = part;
	}


}
