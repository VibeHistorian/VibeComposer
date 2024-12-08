package org.vibehistorian.vibecomposer.Helpers;

import org.vibehistorian.vibecomposer.Constants;
import org.vibehistorian.vibecomposer.Parts.InstPart;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

@XmlRootElement(name = "UsedPattern")
@XmlType(propOrder = {})
public class UsedPattern {

	public static final String NONE = "NONE";
	public static final String MAIN = "MAIN";
	public static final String VERSE = "VERSE";
	public static final String INST = "INST";
	public static final String GENERATED = "**GEN**";
	public static final String[] BASE_PATTERNS = { NONE, MAIN, VERSE, INST };
	public static final Set<String> BASE_PATTERNS_SET = new LinkedHashSet<>();
	static {
        Collections.addAll(BASE_PATTERNS_SET, BASE_PATTERNS);
	}

	Integer part;
	Integer partOrder;
	String name;

	public UsedPattern() {
	}

	public UsedPattern(Integer part, Integer partOrder, String name) {
		super();
		this.part = part;
		this.partOrder = partOrder;
		this.name = name;
	}

	public Integer getPart() {
		return part;
	}

	public void setPart(Integer part) {
		this.part = part;
	}

	public Integer getPartOrder() {
		return partOrder;
	}

	public void setPartOrder(Integer partOrder) {
		this.partOrder = partOrder;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public static UsedPattern generated(InstPart ip) {
		return new UsedPattern(ip.getPartNum(), ip.getOrder(), GENERATED);
	}

	public static UsedPattern generated(InstPart ip, PhraseNotes pn) {
		return new UsedPattern(ip.getPartNum(), ip.getOrder(),
				GENERATED + "[" + pn.hashCode() + "]");
	}

	public static UsedPattern generateNew(InstPart ip) {
		return new UsedPattern(ip.getPartNum(), ip.getOrder(),
				generateName(ip.getPartNum(), ip.getOrder()));
	}

	public static String generateName(int part, int partOrder) {
		return Constants.instNames[part].substring(0, 1) + partOrder + ";"
				+ new Date().hashCode();
	}

	// different part, or different part order
	// or applied manually
	public boolean isCustom(int part, int partOrder, final PhraseNotes currentPattern) {
		if ((part != this.part) || (partOrder != this.partOrder)) {
			return true;
		}
        return currentPattern != null && currentPattern.isApplied();
    }

	public int getType() {
		if (name == null) {
			return -1;
		}
		switch (name) {
		case MAIN:
			return 0;
		case VERSE:
			return 1;
		case INST:
			return 2;
		default:
			return 3;
		}
	}

	@Override
	public String toString() {
		return "[" + Constants.instNames[part] + ", " + partOrder + ", " + name + "]";
	}
}
