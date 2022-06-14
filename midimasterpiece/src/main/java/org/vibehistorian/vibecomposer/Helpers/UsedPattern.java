package org.vibehistorian.vibecomposer.Helpers;

import java.util.Date;

import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Parts.InstPart;

public class UsedPattern {

	public static final String NONE = "NONE";
	public static final String GENERATED = "**GENERATED**";
	public static final String[] BASE_PATTERNS = { NONE, "MAIN", "VERSE", "INST" };

	Integer part;
	Integer partOrder;
	String name;

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

	public static UsedPattern generateNew(InstPart ip) {
		return new UsedPattern(ip.getPartNum(), ip.getOrder(),
				generateName(ip.getPartNum(), ip.getOrder()));
	}

	public static String generateName(int part, int partOrder) {
		return VibeComposerGUI.instNames[part] + partOrder + "-" + new Date().hashCode();
	}

	public boolean isCustom() {
		return !UsedPattern.NONE.equals(getName()) && !UsedPattern.GENERATED.equals(getName());
	}

	@Override
	public String toString() {
		return "[" + part + ", " + partOrder + ", " + name + "]";
	}

}
