package org.vibehistorian.midimasterpiece.midigenerator;

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
import org.vibehistorian.midimasterpiece.midigenerator.Section.SectionType;

@XmlRootElement(name = "arrangement")
@XmlType(propOrder = {})
public class Arrangement {
	private static final List<String> MANDATORY_SECTIONS_ORDER = new ArrayList<>(
			Arrays.asList(new String[] { "INTRO", "CHORUS1", "CHORUS2", "BREAKDOWN", "CHILL",
					"CHORUS3", "CLIMAX", "OUTRO" }));

	private static final Map<String, Section> defaultSections = new LinkedHashMap<>();
	static {
		defaultSections.put("INTRO", new Section("INTRO", 1, 30, 10, 40, 25, 20));
		defaultSections.put("VERSE1", new Section("VERSE1", 1, 65, 60, 40, 25, 60));
		defaultSections.put("CHORUS1", new Section("CHORUS1", 1, 100, 90, 70, 35, 70));
		defaultSections.put("CHORUS2", new Section("CHORUS2", 1, 100, 100, 80, 50, 70));
		defaultSections.put("HALF_CHORUS", new Section("HALF_CHORUS", 1, 0, 100, 80, 50, 80));
		defaultSections.put("BREAKDOWN", new Section("BREAKDOWN", 1, 40, 70, 50, 25, 40));
		defaultSections.put("CHILL", new Section("CHILL", 1, 30, 50, 60, 70, 20));
		defaultSections.put("VERSE2", new Section("VERSE2", 1, 65, 60, 60, 70, 60));
		defaultSections.put("BUILDUP", new Section("BUILDUP", 1, 65, 60, 20, 40, 90));
		defaultSections.put("CHORUS3", new Section("CHORUS3", 1, 100, 100, 80, 80, 85));
		defaultSections.put("CLIMAX", new Section("CLIMAX", 2, 100, 100, 100, 100, 100));
		defaultSections.put("OUTRO", new Section("OUTRO", 1, 80, 70, 50, 40, 10));
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
			Section sec = defaultSections.get(s).deepCopy();
			if (variableSections.contains(s) && arrGen.nextInt(100) < variabilityChance) {
				// climax gets shortened, others get lengthened
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

	public Arrangement(List<Section> sections) {
		super();
		this.sections = sections;
	}

	public Arrangement() {
		resetArrangement();
	}

	public List<Section> getSections() {
		return sections;
	}

	public void setSections(List<Section> sections) {
		this.sections = sections;
	}

	public void resetArrangement() {
		sections.clear();
		sections.add(
				new Section(SectionType.ADVANCED_CHORUS.toString(), 1, 100, 100, 100, 100, 100));
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
		TableModel model = new DefaultTableModel(7, getSections().size());
		for (int i = 0; i < getSections().size(); i++) {
			Section s = getSections().get(i);
			model.setValueAt(s.getType(), 0, i);
			model.setValueAt(String.valueOf(s.getMeasures()), 1, i);
			String mp = StringUtils.join(s.getMelodyPresence());
			String bp = StringUtils.join(s.getBassPresence());
			String cp = StringUtils.join(s.getChordPresence());
			String ap = StringUtils.join(s.getArpPresence());
			String dp = StringUtils.join(s.getDrumPresence());
			mp = mp.replaceAll("\\[", "").replaceAll("\\]", "");
			bp = bp.replaceAll("\\[", "").replaceAll("\\]", "");
			cp = cp.replaceAll("\\[", "").replaceAll("\\]", "");
			ap = ap.replaceAll("\\[", "").replaceAll("\\]", "");
			dp = dp.replaceAll("\\[", "").replaceAll("\\]", "");
			model.setValueAt(mp, 2, i);
			model.setValueAt(bp, 3, i);
			model.setValueAt(cp, 4, i);
			model.setValueAt(ap, 5, i);
			model.setValueAt(dp, 6, i);
		}
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

	public void resortByIndexes(JTable scrollableArrangementTable) {
		// TODO: arrangement.resortByIndexes
		// convertColumnIndexToModel
		// adv chorus at index 0 -> model says 7
		// tempsections[0] = sections().get(7)
		// arrays as list

		TableModel m = scrollableArrangementTable.getModel();
		int[] indexes = new int[m.getColumnCount()];
		Section[] tempSections = new Section[m.getColumnCount()];
		for (int i = 0; i < indexes.length; i++) {
			tempSections[i] = sections.get(scrollableArrangementTable.convertColumnIndexToModel(i));
		}
		sections = new ArrayList<>(Arrays.asList(tempSections));

	}

	public void addSectionLast(JTable scrollableArrangementTable) {
		resortByIndexes(scrollableArrangementTable);
		Section lastSection = sections.get(sections.size() - 1);
		sections.add(new Section(lastSection));

	}

	public void removeSectionLast(JTable scrollableArrangementTable) {
		resortByIndexes(scrollableArrangementTable);
		sections.remove(sections.size() - 1);

	}

	public boolean setFromActualTable(JTable t) {
		if (!overridden)
			return false;
		// TODO: possiblity to catch errors and return false
		TableModel m = t.getModel();
		List<Section> sections = getSections();
		//sections.clear();
		if (m.getColumnCount() != sections.size()) {
			overridden = false;
			return false;
		}

		for (int i = 0; i < m.getColumnCount(); i++) {
			int k = t.convertColumnIndexToModel(i);
			Section s = sections.get(i);
			s.setType((String) m.getValueAt(0, k));
			Object k1 = m.getValueAt(1, k);
			List<Integer> k2 = integerListFromCell(m.getValueAt(2, k));
			List<Integer> k3 = integerListFromCell(m.getValueAt(3, k));
			List<Integer> k4 = integerListFromCell(m.getValueAt(4, k));
			List<Integer> k5 = integerListFromCell(m.getValueAt(5, k));
			List<Integer> k6 = integerListFromCell(m.getValueAt(6, k));
			if (k2.isEmpty() && k3.isEmpty() && k4.isEmpty() && k5.isEmpty() && k6.isEmpty()) {
				overridden = false;
				return false;
			}
			s.setMeasures(k1 instanceof Integer ? (Integer) k1 : Integer.valueOf((String) k1));
			s.setMelodyPresence(k2);
			s.setBassPresence(k3);
			s.setChordPresence(k4);
			s.setArpPresence(k5);
			s.setDrumPresence(k6);
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

	public boolean isOverridden() {
		return overridden;
	}

	public void setOverridden(boolean overridden) {
		this.overridden = overridden;
	}
}
