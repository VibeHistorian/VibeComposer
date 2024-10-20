package org.vibehistorian.vibecomposer.Helpers;

import org.vibehistorian.vibecomposer.LG;

import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;

@XmlRootElement(name = "incl")
@XmlType(propOrder = {})
public class InclusionArray implements Cloneable {

	private int part = -1;
	private ArrayList<Boolean> options = null;

	public InclusionArray() {

	}

	public InclusionArray(int part, ArrayList<Boolean> options) {
		super();
		this.part = part;
		this.options = options;
	}

	public int length() {
		return (options == null) ? 1 : options.size() + 1;
	}

	public InclusionArray clone() {
		try {
			return (InclusionArray) super.clone();
		} catch (CloneNotSupportedException e) {
			LG.e("Unsupported cloning!", e);
		}
		return null;
	}

	public static InclusionArray from(Object[] rowData) {
		InclusionArray pva = new InclusionArray();
		if (rowData == null) {
			return pva;
		}
		pva.part = (Integer) rowData[0];
		ArrayList<Boolean> opts = new ArrayList<>();
		for (int i = 1; i < rowData.length; i++) {
			opts.add((Boolean) rowData[i]);
		}
		pva.options = opts;
		return pva;
	}

	public int getPart() {
		return part;
	}

	public void setPart(int part) {
		this.part = part;
	}

	@XmlList
	public ArrayList<Boolean> getOptions() {
		return options;
	}

	public void setOptions(ArrayList<Boolean> options) {
		this.options = options;
	}

}
