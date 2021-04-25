package org.vibehistorian.midimasterpiece.midigenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.vibehistorian.midimasterpiece.midigenerator.Section.SectionType;

public class Arrangement {
	private static final List<SectionType> MANDATORY_SECTIONS_ORDER = new ArrayList<>(
			Arrays.asList(new SectionType[] { SectionType.INTRO, SectionType.VERSE1,
					SectionType.CHORUS1, SectionType.BREAKDOWN, SectionType.CHORUS2,
					SectionType.ADVANCED_CHORUS, SectionType.OUTRO }));

	private List<Section> sections = new ArrayList<>();

	public Arrangement(List<Section> sections) {
		super();
		this.sections = sections;
	}

	public Arrangement() {
	}

	public List<Section> getSections() {
		return sections;
	}

	public void setSections(List<Section> sections) {
		this.sections = sections;
	}

	public void generateDefaultArrangement(int randomSeed) {
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
}
