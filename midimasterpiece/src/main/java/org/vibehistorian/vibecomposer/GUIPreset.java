package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.List;

public class GUIPreset extends GUIConfig {

	private List<Integer> orderedValuesUI = new ArrayList<>();

	public List<Integer> getOrderedValuesUI() {
		return orderedValuesUI;
	}

	public void setOrderedValuesUI(List<Integer> orderedValuesUI) {
		this.orderedValuesUI = orderedValuesUI;
	}

}
