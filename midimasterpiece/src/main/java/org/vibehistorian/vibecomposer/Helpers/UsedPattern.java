package org.vibehistorian.vibecomposer.Helpers;

import java.util.Date;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Parts.InstPart;

@XmlRootElement(name = "UsedPattern")
@XmlType(propOrder = {})
public class UsedPattern {

	public static final String NONE = "NONE";
	public static final String MAIN = "MAIN";
	public static final String VERSE = "VERSE";
	public static final String INST = "INST";
	public static final String GENERATED = "**GEN**";
	public static final String[] BASE_PATTERNS = { NONE, MAIN, VERSE, INST };

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
		return VibeComposerGUI.instNames[part].substring(0, 1) + partOrder + "-"
				+ new Date().hashCode();
	}

	// different part, or different part order
	// or applied manually
	public boolean isCustom(int part, int partOrder) {
		if ((part != this.part) || (partOrder != this.partOrder)) {
			return true;
		}
		PhraseNotes pn = VibeComposerGUI.guiConfig.getPatternRaw(this);
		if (pn != null && pn.isApplied()) {
			return true;
		}

		return false;
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
		return "[" + part + ", " + partOrder + ", " + name + "]";
	}

}
