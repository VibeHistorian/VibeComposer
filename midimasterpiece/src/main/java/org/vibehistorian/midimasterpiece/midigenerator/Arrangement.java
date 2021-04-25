package org.vibehistorian.midimasterpiece.midigenerator;

import java.util.ArrayList;
import java.util.List;

import org.vibehistorian.midimasterpiece.midigenerator.Section.SectionType;

public class Arrangement {
	private static final SectionType[] MANDATORY_SECTIONS = new SectionType[] {SectionType.INTRO, SectionType.VERSE1, SectionType.CHORUS1, SectionType.BREAKDOWN, SectionType.CHORUS2, SectionType.ADVANCED_CHORUS, SectionType.OUTRO}; 
	
	private List<Section> sections = new ArrayList<>();
	
	public Arrangement(List<Section> sections) {
		super();
		this.sections = sections;
	}

	public List<Section> getSections() {
		return sections;
	}

	public void setSections(List<Section> sections) {
		this.sections = sections;
	}
	
	public void generateArrangement(int randomSeed) {
		
	}
}
