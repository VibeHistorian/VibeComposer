package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "GUIPreset")
@XmlType(propOrder = {})
public class GUIPreset extends GUIConfig {

	private List<Integer> orderedValuesUI = new ArrayList<>();
	private boolean isFullMode = true;
	private boolean isDarkMode = true;
	private boolean isBigMode = false;

	@XmlList
	public List<Integer> getOrderedValuesUI() {
		return orderedValuesUI;
	}

	public void setOrderedValuesUI(List<Integer> orderedValuesUI) {
		this.orderedValuesUI = orderedValuesUI;
	}

	public boolean isFullMode() {
		return isFullMode;
	}

	public void setFullMode(boolean isFullMode) {
		this.isFullMode = isFullMode;
	}

	public boolean isDarkMode() {
		return isDarkMode;
	}

	public void setDarkMode(boolean isDarkMode) {
		this.isDarkMode = isDarkMode;
	}

	public boolean isBigMode() {
		return isBigMode;
	}

	public void setBigMode(boolean isBigMode) {
		this.isBigMode = isBigMode;
	}

}
