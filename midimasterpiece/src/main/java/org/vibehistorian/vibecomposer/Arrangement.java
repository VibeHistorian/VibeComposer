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
along with this program; if not, write to the Free Software
Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/

package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.apache.commons.lang3.StringUtils;

@XmlRootElement(name = "arrangement")
@XmlType(propOrder = {})
public class Arrangement {
	private static final List<String> MANDATORY_SECTIONS_ORDER = new ArrayList<>(
			Arrays.asList(new String[] { "INTRO", "CHORUS1", "CHORUS2", "BREAKDOWN", "CHILL",
					"CHORUS3", "CLIMAX", "CLIMAX", "OUTRO" }));

	public static final Map<String, Section> defaultSections = new LinkedHashMap<>();
	static {
		defaultSections.put("INTRO", new Section("INTRO", 1, 20, 10, 40, 25, 20));
		defaultSections.put("VERSE1", new Section("VERSE1", 1, 65, 60, 30, 25, 40));
		defaultSections.put("CHORUS1", new Section("CHORUS1", 1, 50, 90, 50, 35, 50));
		defaultSections.put("CHORUS2", new Section("CHORUS2", 1, 65, 100, 60, 50, 50));
		defaultSections.put("HALF_CHORUS", new Section("HALF_CHORUS", 1, 0, 100, 60, 50, 80));
		defaultSections.put("BREAKDOWN", new Section("BREAKDOWN", 1, 40, 60, 60, 25, 40));
		defaultSections.put("CHILL", new Section("CHILL", 1, 10, 30, 70, 70, 10));
		defaultSections.put("VERSE2", new Section("VERSE2", 1, 65, 60, 40, 50, 50));
		defaultSections.put("BUILDUP", new Section("BUILDUP", 1, 65, 60, 20, 40, 90));
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
		afterinsertsMap.put("INTRO", new String[] { "VERSE1", "VERSE2", "BUILDUP" });

		afterinsertsMap.put("CHORUS1", new String[] { "VERSE2" });

		//afterinsertsMap.put("BREAKDOWN", new String[] { "INTRO" });

		afterinsertsMap.put("CHILL", new String[] { "VERSE2", "BUILDUP" });
	}

	private static final List<String> variableSections = new ArrayList<>(
			Arrays.asList(new String[] { "VERSE2", "CHORUS2", "CLIMAX", "CHILL", "OUTRO" }));

	public void randomizeFully(int maxLength, int seed, int replacementChance, int insertChance,
			int maxInsertsPerSection, int maxInsertsTotal, int variabilityChance) {
		Random arrGen = new Random(seed);
		List<String> newArrangementSkeleton = new ArrayList<>(MANDATORY_SECTIONS_ORDER);
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
		for (String s : fullArrangement) {
			//System.out.println("DeepCopy for " + s);
			Section sec = defaultSections.get(s).deepCopy();
			if (variableSections.contains(s) && arrGen.nextInt(100) < variabilityChance) {

				if (sec.getMeasures() > 1) {
					sec.setMeasures(1);
				} else {
					sec.setMeasures(2);
				}
			}
			sections.add(sec);
		}
	}


	private List<Section> sections = new ArrayList<>();
	private boolean previewChorus = false;
	private boolean overridden;
	private int seed = 0;

	public Arrangement(List<Section> sections) {
		super();
		this.sections = sections;
	}

	public Arrangement() {
		resetArrangement();
		setPreviewChorus(true);
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
			if (!s.getType().equals("BUILDUP")) {
				sections.add(s.deepCopy());
			}
		}
	}

	public TableModel convertToTableModel() {

		TableModel model = new DefaultTableModel(7, getSections().size());
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

		};
		for (int i = 0; i < getSections().size(); i++) {
			Section s = getSections().get(i);
			model.setValueAt(s.getType(), 0, i);
			model.setValueAt(String.valueOf(s.getMeasures()), 1, i);
			for (int j = 0; j < 5; j++) {
				String pres = StringUtils.join(s.getPresence(j));
				pres = pres.replaceAll("\\[", "").replaceAll("\\]", "");
				model.setValueAt(pres, j + 2, i);
			}
		}
		/*model.addTableModelListener(new TableModelListener() {
			public void tableChanged(TableModelEvent evt) {
				// here goes your code "on cell update"
			}
		});*/
		return model;
	}

	public void setFromModel(JTable t) {
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
		scrollableArrangementTable.repaint();

	}

	public boolean setFromActualTable(JTable t, boolean forceColumns) {

		// TODO: possiblity to catch errors and return false
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
		System.out.println("setFromActualTable SUCCESS!");
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

	public void duplicateSection(JTable tbl, boolean isActual) {


		int column = tbl.getSelectedColumn();
		if (column == -1)
			return;
		/*if (isActual) {
			overridden = true;
			setFromActualTable(tbl, true);
		} else {
			overridden = false;
			setFromModel(tbl);
		}*/
		/*if (column != tbl.convertColumnIndexToModel(column)) {
			resortByIndexes(tbl, isActual);
		}*/
		Section sec = sections.get(column);
		sections.add(column, sec.deepCopy());
	}

	public void removeSection(JTable tbl, boolean isActual) {


		int[] columns = tbl.getSelectedColumns();
		if (columns.length == 0) {
			return;
		}
		/*if (isActual) {
			overridden = true;
			setFromActualTable(tbl, true);
		} else {
			overridden = false;
			setFromModel(tbl);
		}*/
		//resortByIndexes(tbl, isActual);
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
	}

	public int getSeed() {
		return seed;
	}

	public void setSeed(int seed) {
		this.seed = seed;
	}
}
