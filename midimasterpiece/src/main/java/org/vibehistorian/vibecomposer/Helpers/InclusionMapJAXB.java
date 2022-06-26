package org.vibehistorian.vibecomposer.Helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "inclusions")
@XmlType(propOrder = {})
public class InclusionMapJAXB {

	@XmlRootElement(name = "inclList")
	@XmlType(propOrder = {})
	public static class PartVarsList extends ArrayList<InclusionArray> {
		private static final long serialVersionUID = 1185026972238948008L;

		public PartVarsList() {
			super();
		}

		@XmlElement(name = "inclElems")
		public PartVarsList getPartVarsElems() {
			return PartVarsList.this;
		}

	}

	HashMap<Integer, PartVarsList> map = new HashMap<>();

	public InclusionMapJAXB() {
		super();
	}

	public HashMap<Integer, PartVarsList> getMap() {
		return map;
	}

	public void setMap(HashMap<Integer, PartVarsList> map) {
		this.map = map;
	}

	public static InclusionMapJAXB from(Map<Integer, Object[][]> partPresenceVariationMap) {
		InclusionMapJAXB jaxbMap = new InclusionMapJAXB();
		for (int i = 0; i < 5; i++) {
			Object[][] data = partPresenceVariationMap.get(i);
			PartVarsList pvas = new PartVarsList();
			if (data == null) {
				jaxbMap.map.put(i, pvas);
				continue;
			}
			for (int j = 0; j < data.length; j++) {
				Object[] rowData = data[j];
				InclusionArray pva = InclusionArray.from(rowData);
				pvas.add(pva);
			}
			jaxbMap.map.put(i, pvas);
		}
		return jaxbMap;
	}

	public static Map<Integer, Object[][]> toMap(InclusionMapJAXB jaxb) {
		Map<Integer, Object[][]> result = new HashMap<>();
		if (jaxb == null || jaxb.map == null) {
			return result;
		}
		for (int i = 0; i < 5; i++) {
			PartVarsList list = jaxb.map.get(i);
			if (list == null || list.isEmpty()) {
				result.put(i, new Object[0][0]);
				continue;
			}
			List<Integer> rowOrders = list.stream().map(e -> e.getPart())
					.collect(Collectors.toList());
			Object[][] data = new Object[rowOrders
					.size()][jaxb.map.get(i).get(0).getOptions().size() + 1];
			for (int j = 0; j < rowOrders.size(); j++) {
				data[j][0] = rowOrders.get(j);
				for (int k = 1; k < data[j].length; k++) {
					data[j][k] = list.get(j).getOptions().get(k - 1);
				}
			}
			result.put(i, data);
		}
		return result;
	}
}
