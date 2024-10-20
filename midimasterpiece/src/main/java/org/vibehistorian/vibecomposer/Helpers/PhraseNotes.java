package org.vibehistorian.vibecomposer.Helpers;

import jm.music.data.Note;
import jm.music.data.Phrase;
import org.apache.commons.lang3.StringUtils;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@XmlRootElement(name = "phraseNotes")
@XmlType(propOrder = {})
@XmlSeeAlso({ PhraseNote.class })
public class PhraseNotes extends ArrayList<PhraseNote> implements Cloneable {
	private static final long serialVersionUID = 8933379402297939538L;

	private int partOrder = -1;
	private boolean applied = false;
	private List<PhraseNote> iterationOrder = null;

	public PhraseNotes() {
		super();
	}

	public PhraseNotes(Phrase phr) {
		this(phr.getNoteList());
	}

	public PhraseNotes(List<Note> notes) {
		this();
		addAll(notes.stream().map(e -> new PhraseNote(e)).collect(Collectors.toList()));
	}

	public List<Note> makeNotes() {
		return stream().map(e -> e.toNote()).collect(Collectors.toList());
	}

	public PhraseExt makePhrase() {
		PhraseExt phr = new PhraseExt();
		makeNotes().forEach(e -> phr.addNote(e));
		return phr;
	}

	public PhraseNotes copy() {
		PhraseNotes pn = new PhraseNotes(makeNotes());
		pn.setPartOrder(partOrder);
		pn.setApplied(applied);
		return pn;
	}

	public String toStringPitches() {
		return StringUtils.join(stream().map(e -> e.getPitch() >= 0 ? e.getPitch() : -1)
				.collect(Collectors.toList()), ",");
	}

	@XmlElement(name = "phraseNote")
	public List<PhraseNote> getPhraseNotes() {
		return this;
	}

	public int getPartOrder() {
		return partOrder;
	}

	public void setPartOrder(int partOrder) {
		this.partOrder = partOrder;
	}

	public void remakeNoteStartTimes() {
		remakeNoteStartTimes(false);
	}

	public void remakeNoteStartTimes(boolean manipulateOrder) {
		double current = 0.0;
		for (PhraseNote pn : this) {
			pn.setStartTime(current + pn.getOffset());
			pn.setAbsoluteStartTime(current);
			current += pn.getRv();
		}
		if (manipulateOrder) {
			iterationOrder = new ArrayList<>(this);
			iterationOrder.sort(Comparator.comparingDouble(PhraseNote::getStartTime));
		}
	}

	public List<PhraseNote> getIterationOrder() {
		return iterationOrder != null && iterationOrder.size() == this.size() ? iterationOrder : this;
	}

	public boolean isApplied() {
		return applied;
	}

	public void setApplied(boolean applied) {
		this.applied = applied;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + partOrder;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		PhraseNotes other = (PhraseNotes) obj;
		if (partOrder != other.partOrder)
			return false;
		return true;
	}


}
