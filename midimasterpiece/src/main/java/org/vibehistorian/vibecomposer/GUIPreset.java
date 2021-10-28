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

	@XmlList
	public List<Integer> getOrderedValuesUI() {
		return orderedValuesUI;
	}

	public void setOrderedValuesUI(List<Integer> orderedValuesUI) {
		this.orderedValuesUI = orderedValuesUI;
	}

}
