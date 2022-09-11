/* --------------------
* @author Vibe Historian
* ---------------------

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or any
later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not,
see <https://www.gnu.org/licenses/>.
*/

package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.Section.SectionType;
import org.vibehistorian.vibecomposer.Helpers.InclusionMapJAXB;
import org.vibehistorian.vibecomposer.Popups.ArrangementPartInclusionPopup;

@XmlRootElement(name = "arrangement")
@XmlType(propOrder = {})
public class Arrangement {
	private static List<List<String>> DEFAULT_ARRANGEMENTS = new ArrayList<>();

	private static final List<String> POP_ARRANGEMENT = new ArrayList<>(
			Arrays.asList(new String[] { "INTRO", "CHORUS1", "CHORUS2", "BREAKDOWN", "CHILL",
					"CHORUS3", "CLIMAX", "CLIMAX", "OUTRO" }));
	private static final List<String> EDM_ARRANGEMENT = new ArrayList<>(
			Arrays.asList(new String[] { "INTRO", "BUILDUP1", "CHORUS2", "CHORUS3", "VERSE1",
					"VERSE2", "CHORUS3", "CLIMAX", "OUTRO" }));

	private static final List<String> EDM_ARRANGEMENT2 = new ArrayList<>(
			Arrays.asList(new String[] { "INTRO", "BUILDUP1", "CHORUS2", "CHORUS3", "VERSE1",
					"BREAKDOWN", "CHILL", "BUILDUP1", "BUILDUP2", "CHORUS3", "CLIMAX", "BREAKDOWN",
					"CHILL", "CLIMAX", "CLIMAX", "HALF_CHORUS", "OUTRO" }));

	private static final List<String> POP_ARRANGEMENT2 = new ArrayList<>(
			Arrays.asList(new String[] { "HALF_CHORUS", "HALF_CHORUS", "OUTRO", "VERSE2", "CHORUS2",
					"VERSE2", "CHORUS3", "BREAKDOWN", "BUILDUP1", "BUILDUP2", "CHORUS3", "CLIMAX",
					"OUTRO" }));

	static {
		DEFAULT_ARRANGEMENTS.add(POP_ARRANGEMENT);
		DEFAULT_ARRANGEMENTS.add(EDM_ARRANGEMENT);
		DEFAULT_ARRANGEMENTS.add(EDM_ARRANGEMENT2);
		DEFAULT_ARRANGEMENTS.add(POP_ARRANGEMENT2);
	}

	public static final Map<String, Section> defaultSections = new LinkedHashMap<>();
	static {
		defaultSections.put("INTRO", new Section("INTRO", 1, 20, 10, 40, 25, 20));
		defaultSections.put("VERSE1", new Section("VERSE1", 1, 40, 60, 30, 25, 40));
		defaultSections.put("VERSE2", new Section("VERSE2", 1, 40, 60, 40, 50, 50));
		defaultSections.put("CHORUS1", new Section("CHORUS1", 1, 50, 90, 50, 35, 60));
		defaultSections.put("CHORUS2", new Section("CHORUS2", 1, 65, 100, 60, 50, 70));
		defaultSections.put("HALF_CHORUS", new Section("HALF_CHORUS", 1, 0, 100, 60, 50, 80));
		defaultSections.put("BREAKDOWN", new Section("BREAKDOWN", 1, 40, 60, 60, 25, 40));
		defaultSections.put("CHILL", new Section("CHILL", 1, 10, 30, 70, 70, 10));
		defaultSections.put("VERSE3", new Section("VERSE3", 1, 50, 80, 40, 70, 60));
		defaultSections.put("BUILDUP1", new Section("BUILDUP1", 1, 40, 40, 10, 20, 70));
		defaultSections.put("BUILDUP2", new Section("BUILDUP2", 1, 65, 60, 20, 40, 90));
		defaultSections.put("CHORUS3", new Section("CHORUS3", 1, 80, 100, 80, 80, 85));
		defaultSections.put("CLIMAX", new Section("CLIMAX", 1, 100, 100, 100, 100, 100));
		defaultSections.put("OUTRO", new Section("OUTRO", 1, 50, 70, 60, 40, 10));
	}

	private static final Map<String, String[]> replacementMap = new HashMap<>();
	static {
		replacementMap.put("INTRO",
				new String[] { "HALF_CHORUS", "CHORUS1", "BREAKDOWN", "OUTRO" });

		replacementMap.put("CHORUS1", new String[] { "VERSE2" });

		replacementMap.put("CHORUS2", new String[] { "CHORUS3" });

		replacementMap.put("CHILL", new String[] { "VERSE2" });

		replacementMap.put("BREAKDOWN", new String[] { "OUTRO" });
	}


	private static final Map<String, String[]> afterinsertsMap = new HashMap<>();
	static {
		afterinsertsMap.put("INTRO", new String[] { "VERSE1", "VERSE2", "BUILDUP1" });

		afterinsertsMap.put("CHORUS1", new String[] { "VERSE2" });
		afterinsertsMap.put("CHORUS2", new String[] { "CHORUS3" });

		//afterinsertsMap.put("BREAKDOWN", new String[] { "INTRO" });

		afterinsertsMap.put("CHILL", new String[] { "VERSE2", "BUILDUP1", "BUILDUP2" });
	}

	private static final List<String> variableSections = new ArrayList<>(
			Arrays.asList(new String[] { "VERSE2", "CHORUS2", "CLIMAX", "CHILL", "OUTRO" }));

	public void randomizeFully(int maxLength, int seed, int replacementChance, int insertChance,
			int maxInsertsPerSection, int maxInsertsTotal, int variabilityChance) {
		Random arrGen = new Random(seed);
		List<String> newArrangementSkeleton = new ArrayList<>(
				DEFAULT_ARRANGEMENTS.get(arrGen.nextInt(DEFAULT_ARRANGEMENTS.size())));
		for (int i = 0; i < newArrangementSkeleton.size(); i++) {
			String s = newArrangementSkeleton.get(i);
			if (arrGen.nextInt(100) < replacementChance && replacementMap.containsKey(s)) {
				String[] options = replacementMap.get(s);
				String pickedOption = options[arrGen.nextInt(options.length)];
				newArrangementSkeleton.set(i, pickedOption);
			}
		}
		List<String> fullArrangement = new ArrayList<>();
		int totalInsertsCounter = 0;
		for (int i = 0; i < newArrangementSkeleton.size(); i++) {
			String s = newArrangementSkeleton.get(i);
			int sectionInsertsCounter = 0;
			fullArrangement.add(s);
			if (afterinsertsMap.containsKey(s) && totalInsertsCounter < maxInsertsTotal
					&& (fullArrangement.size() + newArrangementSkeleton.size() - i) < maxLength) {
				while (sectionInsertsCounter < maxInsertsPerSection
						&& arrGen.nextInt(100) < insertChance) {
					String[] options = afterinsertsMap.get(s);
					String pickedOption = options[arrGen.nextInt(options.length)];
					fullArrangement.add(pickedOption);
					sectionInsertsCounter++;
					totalInsertsCounter++;
				}
			}
		}

		sections.clear();
		Section lastSec = null;
		for (String s : fullArrangement) {
			// skip same section with no changes in it
			if (lastSec != null && s.equals(lastSec.getType())
					&& !s.equals(SectionType.CLIMAX.toString())) {
				continue;
			}
			//LG.d("DeepCopy for " + s);
			Section sec = defaultSections.get(s).deepCopy();
			if (variableSections.contains(s) && arrGen.nextInt(100) < variabilityChance) {
				sections.add(sec);
				Section doubleSec = sec.deepCopy();
				for (int i = 0; i < 5; i++) {
					doubleSec.addChanceForInst(i, 10);
				}
				sections.add(doubleSec);
			} else {
				sections.add(sec);
			}
			lastSec = sec;
		}
	}


	private List<Section> sections = new ArrayList<>();
	private boolean previewChorus = false;
	private boolean overridden;
	private int seed = 0;
	private Map<Integer, Object[][]> partInclusionMap = new HashMap<>();
	private Map<Integer, Boolean[]> globalVariationMap = new HashMap<>();

	public Arrangement(List<Section> sections) {
		super();
		this.sections = sections;
	}

	public Arrangement() {
		resetArrangement();
		setPreviewChorus(true);
		initGlobalVariationMap();
	}

	public List<Section> getSections() {
		return sections;
	}

	public void setSections(List<Section> sections) {
		this.sections = sections;
	}

	public void resetArrangement() {
		sections.clear();
		Section preview = new Section("PREVIEW", 1, 100, 100, 100, 100, 100);
		sections.add(preview);
	}

	public void generateDefaultArrangement() {
		sections.clear();
		// type, length, melody%, bass%, chord%, arp%, drum%
		for (Section s : defaultSections.values()) {
			sections.add(s.deepCopy());
		}
	}

	public TableModel convertToTableModel() {

		TableModel model = new DefaultTableModel(7, getSections().size()) {

			private static final long serialVersionUID = 1471873999987138971L;

			@Override
			public String getColumnName(int column) {
				return String.valueOf(column + 1);
			}
		};
		for (int i = 0; i < getSections().size(); i++) {
			Section s = getSections().get(i);
			model.setValueAt(s.getType(), 0, i);
			model.setValueAt(s.getMeasures(), 1, i);
			model.setValueAt(s.getMelodyChance(), 2, i);
			model.setValueAt(s.getBassChance(), 3, i);
			model.setValueAt(s.getChordChance(), 4, i);
			model.setValueAt(s.getArpChance(), 5, i);
			model.setValueAt(s.getDrumChance(), 6, i);
		}
		return model;
	}

	public TableModel convertToActualTableModel() {
		TableModel model = new DefaultTableModel(7, getSections().size()) {

			private static final long serialVersionUID = 2770352745917910024L;

			@Override
			public boolean isCellEditable(int row, int col) {
				return (row == 0);
			}

			@Override
			public String getColumnName(int column) {
				return String.valueOf(column + 1);
			}

		};
		for (int i = 0; i < getSections().size(); i++) {
			Section s = getSections().get(i);
			model.setValueAt(s.getType(), 0, i);
			model.setValueAt(String.valueOf(s.getMeasures()), 1, i);
			for (int j = 0; j < 5; j++) {
				/*String pres = StringUtils.join(s.getPresence(j));
				pres = pres.replaceAll("\\[", "").replaceAll("\\]", "");
				boolean isCustomizedPart = s.getInstPartList(j) != null;
				if (isCustomizedPart) {
					pres = "*" + pres;
				}*/
				model.setValueAt(s.getPresence(j), j + 2, i);
			}
		}
		/*model.addTableModelListener(new TableModelListener() {
			public void tableChanged(TableModelEvent evt) {
				// here goes your code "on cell update"
			}
		});*/
		return model;
	}

	public void setFromTable(JTable t) {
		TableModel m = t.getModel();
		List<Section> sections = getSections();
		sections.clear();
		for (int i = 0; i < m.getColumnCount(); i++) {
			int k = t.convertColumnIndexToModel(i);
			Section s = new Section();
			s.setType((String) m.getValueAt(0, k));
			Object k1 = m.getValueAt(1, k);
			Object k2 = m.getValueAt(2, k);
			Object k3 = m.getValueAt(3, k);
			Object k4 = m.getValueAt(4, k);
			Object k5 = m.getValueAt(5, k);
			Object k6 = m.getValueAt(6, k);
			s.setMeasures(k1 instanceof Integer ? (Integer) k1 : Integer.valueOf((String) k1));
			s.setMelodyChance(k2 instanceof Integer ? (Integer) k2 : Integer.valueOf((String) k2));
			s.setBassChance(k3 instanceof Integer ? (Integer) k3 : Integer.valueOf((String) k3));
			s.setChordChance(k4 instanceof Integer ? (Integer) k4 : Integer.valueOf((String) k4));
			s.setArpChance(k5 instanceof Integer ? (Integer) k5 : Integer.valueOf((String) k5));
			s.setDrumChance(k6 instanceof Integer ? (Integer) k6 : Integer.valueOf((String) k6));
			sections.add(s);

		}
		setOverridden(false);
	}

	public boolean isPreviewChorus() {
		return previewChorus;
	}

	public void setPreviewChorus(boolean previewChorus) {
		this.previewChorus = previewChorus;
	}

	public void resortByIndexes(JTable scrollableArrangementTable, boolean isActual) {
		TableModel m = scrollableArrangementTable.getModel();
		int[] indexes = new int[m.getColumnCount()];
		Section[] tempSections = new Section[m.getColumnCount()];
		for (int i = 0; i < indexes.length; i++) {
			tempSections[i] = sections.get(scrollableArrangementTable.convertColumnIndexToModel(i));
		}
		sections = new ArrayList<>(Arrays.asList(tempSections));
		scrollableArrangementTable
				.setModel(isActual ? convertToActualTableModel() : convertToTableModel());
		VibeComposerGUI.recolorAllVariationButtons();
		scrollableArrangementTable.repaint();

	}

	public boolean setFromActualTable(JTable t, boolean forceColumns) {

		TableModel m = t.getModel();
		List<Section> sections = getSections();
		//sections.clear();
		if (forceColumns) {
			sections.clear();
			for (int i = 0; i < m.getColumnCount(); i++) {
				sections.add(new Section());
			}
		}

		if (sections.size() != m.getColumnCount()) {
			return false;
		}

		for (int i = 0; i < m.getColumnCount(); i++) {
			int k = t.convertColumnIndexToModel(i);
			Section s = sections.get(i);
			s.setType((String) m.getValueAt(0, k));
			Object k1 = m.getValueAt(1, k);
			s.setMeasures(k1 instanceof Integer ? (Integer) k1 : Integer.valueOf((String) k1));
			//sections.add(s);

		}
		return true;
	}

	private List<Integer> integerListFromCell(Object cell) {
		if (cell == null) {
			return new ArrayList<>();
		}
		String sCell = (String) cell;
		sCell = sCell.replaceAll(" ", "");
		return Arrays.asList(sCell.split(",")).stream().filter(e -> !e.isBlank())
				.map(e -> Integer.valueOf(e)).collect(Collectors.toList());
	}

	public Section addDefaultSection(JTable tbl, String defaultType, Integer col) {
		int column = col != null ? col : tbl.getSelectedColumn();
		if (column < 0) {
			// add at start
			column = 0;
		} else {
			// add after section
			column++;
		}
		Section sec = defaultSections.get(defaultType).deepCopy();
		sections.add(column, sec);
		return sec;
	}

	public Section addDefaultSection(JTable tbl, String defaultType) {
		return addDefaultSection(tbl, defaultType, null);
	}

	public void duplicateSection(JTable tbl) {


		int column = tbl.getSelectedColumn();
		if (column == -1)
			return;
		Section sec = sections.get(column);
		sections.add(column, sec.deepCopy());
	}

	public void duplicateSectionExact(JTable tbl, int column) {
		if (column == -1)
			return;
		Section sec = sections.get(column);
		sections.add(column, sec.deepCopy());
	}

	public void removeSection(JTable tbl) {


		int[] columns = tbl.getSelectedColumns();
		if (columns.length == 0) {
			return;
		}
		List<Section> secs = new ArrayList<>();
		for (int i : columns) {
			secs.add(sections.get(i));
		}
		sections.removeAll(secs);
	}

	public void removeSectionExact(JTable tbl, int col) {
		int[] columns = new int[] { col };
		if (columns.length == 0) {
			return;
		}
		List<Section> secs = new ArrayList<>();
		for (int i : columns) {
			secs.add(sections.get(i));
		}
		sections.removeAll(secs);
	}

	public boolean isOverridden() {
		return overridden;
	}

	public void setOverridden(boolean overridden) {
		this.overridden = overridden;
		LG.d(("ARRANGEMENT overridden: " + overridden));
	}

	public int getSeed() {
		return seed;
	}

	public void setSeed(int seed) {
		this.seed = seed;
	}

	@XmlTransient
	public Map<Integer, Object[][]> getInclMap() {
		return partInclusionMap;
	}

	public void setInclMap(Map<Integer, Object[][]> partInclusionMap) {
		this.partInclusionMap = partInclusionMap;
	}

	@XmlElement(name = "partInclusions")
	public InclusionMapJAXB getPartInclusionMap() {
		if (partInclusionMap.isEmpty()) {
			return null;
		}
		return InclusionMapJAXB.from(partInclusionMap);
	}

	public void setPartInclusionMap(InclusionMapJAXB map) {
		partInclusionMap = InclusionMapJAXB.toMap(map);
	}

	public void initPartInclusionMap() {
		int typesCount = ArrangementPartInclusionPopup.ENERGY_LEVELS.length;

		for (int i = 0; i < 5; i++) {
			List<Integer> rowOrders = VibeComposerGUI.getInstList(i).stream()
					.map(e -> e.getPanelOrder()).collect(Collectors.toList());
			Collections.sort(rowOrders);
			Object[][] data = new Object[rowOrders.size()][typesCount];
			for (int j = 0; j < rowOrders.size(); j++) {
				data[j][0] = rowOrders.get(j);
				for (int k = 1; k < typesCount; k++) {
					data[j][k] = Boolean.TRUE;
				}
			}
			partInclusionMap.put(i, data);
		}
		if (partInclusionMap.get(0).length > 0) {
			partInclusionMap.get(0)[0][1] = Boolean.FALSE;
			partInclusionMap.get(0)[0][4] = Boolean.FALSE;
		}
	}

	public void initPartInclusionMapIfNull() {
		if (partInclusionMap.get(0) == null) {
			initPartInclusionMap();
		}
	}

	public boolean isPartInclusion(int part, int partOrder, int sectionType) {
		initPartInclusionMapIfNull();
		if (isOverridden()) {
			return true;
		}
		return partInclusionMap.get(part)[partOrder][sectionType + 2] == Boolean.TRUE;
	}

	public void recalculatePartInclusionMapBoundsIfNeeded() {
		if (getInclMap() == null) {
			initPartInclusionMap();
			return;
		}
		boolean needsArrayCopy = false;
		for (int i = 0; i < 5; i++) {
			int actualInstCount = VibeComposerGUI.getInstList(i).size();
			if (getInclMap().get(i) == null) {
				initPartInclusionMap();
			}
			int secInstCount = getInclMap().get(i).length;
			if (secInstCount != actualInstCount) {
				needsArrayCopy = true;
				break;
			}
		}
		if (needsArrayCopy) {
			initPartInclusionMapFromOldData();
		}

	}

	private void initPartInclusionMapFromOldData() {
		if (getInclMap() == null) {
			initPartInclusionMap();
			return;
		}
		for (int i = 0; i < 5; i++) {
			List<Integer> rowOrders = VibeComposerGUI.getInstList(i).stream()
					.map(e -> e.getPanelOrder()).collect(Collectors.toList());
			Collections.sort(rowOrders);
			Object[][] data = new Object[rowOrders
					.size()][ArrangementPartInclusionPopup.ENERGY_LEVELS.length];
			for (int j = 0; j < rowOrders.size(); j++) {
				data[j][0] = rowOrders.get(j);
				for (int k = 1; k < ArrangementPartInclusionPopup.ENERGY_LEVELS.length; k++) {
					data[j][k] = getBooleanFromOldData(getInclMap().get(i), j, k);
				}
			}
			getInclMap().put(i, data);
		}
	}

	private Object getBooleanFromOldData(Object[][] oldData, int j, int k) {
		if (oldData.length <= j || oldData[j].length <= k) {
			return Boolean.TRUE;
		} else {
			return (Boolean) oldData[j][k];
		}
	}

	public void initGlobalVariationMap() {
		for (int i = 0; i < 5; i++) {
			int typesCount = Section.variationDescriptions[i].length - 1;
			Boolean[] data = new Boolean[typesCount];
			data[0] = Boolean.TRUE;
			for (int k = 1; k < typesCount; k++) {
				data[k] = Boolean.TRUE;
			}
			globalVariationMap.put(i, data);
		}
		Boolean[] data = new Boolean[Section.sectionVariationNames.length + 1];
		for (int k = 0; k < data.length; k++) {
			data[k] = Boolean.TRUE;
		}
		globalVariationMap.put(5, data);
	}

	public void initGlobalVariationMapFromOldData() {
		if (getGlobalVariationMap() == null) {
			initGlobalVariationMap();
			return;
		}
		for (int i = 0; i < 5; i++) {
			int typesCount = Section.variationDescriptions[i].length - 1;
			Boolean[] data = new Boolean[typesCount];
			Boolean data0 = Boolean.TRUE;
			for (int k = 1; k < typesCount; k++) {
				data[k] = getBooleanFromOldData1D(globalVariationMap.get(i), k);
				data0 &= data[k];
			}
			data[0] = data0;
			globalVariationMap.put(i, data);
		}
		Boolean[] data = new Boolean[Section.sectionVariationNames.length + 1];
		Boolean data0 = Boolean.TRUE;
		for (int k = 0; k < data.length; k++) {
			data[k] = getBooleanFromOldData1D(globalVariationMap.get(5), k);
			data0 &= data[k];
		}
		data[0] = data0;
		globalVariationMap.put(5, data);
	}

	private Boolean getBooleanFromOldData1D(Boolean[] oldData, int j) {
		if (oldData == null || oldData.length <= j) {
			return Boolean.FALSE;
		} else {
			return (Boolean) oldData[j];
		}
	}

	public void initGlobalVariationMapIfNull() {
		if (globalVariationMap.get(0) == null) {
			initGlobalVariationMap();
		}
	}

	public Map<Integer, Boolean[]> getGlobalVariationMap() {
		return globalVariationMap;
	}

	public void setGlobalVariationMap(Map<Integer, Boolean[]> globalVariationMap) {
		this.globalVariationMap = globalVariationMap;
	}

	public boolean isGlobalVariation(int part, int variationOrder) {
		initGlobalVariationMapIfNull();
		if (isOverridden()) {
			return true;
		}
		if (globalVariationMap.get(part).length <= variationOrder + 1) {
			initGlobalVariationMapFromOldData();
		}
		return globalVariationMap.get(part)[variationOrder + 1] == Boolean.TRUE;
	}

	public void verifyGlobalVariations() {
		if (getGlobalVariationMap() == null) {
			initGlobalVariationMap();
		}
		for (int i = 0; i < 6; i++) {
			if (getGlobalVariationMap().get(i) == null) {
				initGlobalVariationMap();
			}
			if (i < 5) {
				if (getGlobalVariationMap().get(i).length < Section.variationDescriptions[i].length
						- 1) {
					initGlobalVariationMapFromOldData();
					return;
				}
			} else {
				if (getGlobalVariationMap().get(i).length < Section.sectionVariationNames.length
						+ 1) {
					initGlobalVariationMapFromOldData();
					return;
				}
			}
		}
	}
}
