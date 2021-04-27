package org.vibehistorian.midimasterpiece.midigenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import org.vibehistorian.midimasterpiece.midigenerator.Section.SectionType;

public class Arrangement {
	private static final List<SectionType> MANDATORY_SECTIONS_ORDER = new ArrayList<>(
			Arrays.asList(new SectionType[] { SectionType.INTRO, SectionType.VERSE1,
					SectionType.CHORUS1, SectionType.BREAKDOWN, SectionType.CHORUS2,
					SectionType.ADVANCED_CHORUS, SectionType.OUTRO }));

	private List<Section> sections = new ArrayList<>();
	private boolean previewChorus = false;

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
		sections.add(new Section(SectionType.ADVANCED_CHORUS, 1, 100, 100, 100, 100, 100));
	}

	public void generateDefaultArrangement() {
		sections.clear();
		// type, length, melody%, bass%, chord%, arp%, drum%
		sections.add(new Section(SectionType.INTRO, 1, 30, 10, 40, 40, 20));
		sections.add(new Section(SectionType.VERSE1, 1, 65, 60, 40, 50, 60));
		sections.add(new Section(SectionType.VERSE2, 1, 80, 60, 60, 60, 70));
		sections.add(new Section(SectionType.CHORUS1, 1, 100, 80, 70, 70, 80));
		sections.add(new Section(SectionType.BREAKDOWN, 1, 40, 70, 50, 50, 40));
		sections.add(new Section(SectionType.CHILL, 1, 30, 50, 60, 30, 20));
		sections.add(new Section(SectionType.VERSE3, 1, 90, 80, 60, 60, 70));
		sections.add(new Section(SectionType.BUILDUP, 1, 30, 80, 40, 40, 80));
		sections.add(new Section(SectionType.CHORUS2, 1, 100, 100, 80, 80, 85));
		sections.add(new Section(SectionType.ADVANCED_CHORUS, 2, 100, 100, 100, 100, 100));
		sections.add(new Section(SectionType.OUTRO, 1, 60, 50, 50, 40, 20));
	}

	public TableModel convertToTableModel() {
		TableModel model = new DefaultTableModel(7, getSections().size());
		for (int i = 0; i < getSections().size(); i++) {
			Section s = getSections().get(i);
			model.setValueAt(s.getType().toString(), 0, i);
			model.setValueAt(s.getMeasures(), 1, i);
			model.setValueAt(s.getMelodyChance(), 2, i);
			model.setValueAt(s.getBassChance(), 3, i);
			model.setValueAt(s.getChordChance(), 4, i);
			model.setValueAt(s.getArpChance(), 5, i);
			model.setValueAt(s.getDrumChance(), 6, i);
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
			s.setType(SectionType.valueOf((String) m.getValueAt(0, k)));
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
			tempSections[0] = sections.get(scrollableArrangementTable.convertColumnIndexToModel(i));
		}
		sections = Arrays.asList(tempSections);

	}
}
