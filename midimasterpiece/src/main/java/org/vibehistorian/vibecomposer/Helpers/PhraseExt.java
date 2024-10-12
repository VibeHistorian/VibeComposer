package org.vibehistorian.vibecomposer.Helpers;


import jm.music.data.Note;
import jm.music.data.Phrase;

import java.util.Enumeration;

public class PhraseExt extends Phrase {

	private static final long serialVersionUID = -3149703478694332198L;
	public int part = 0;
	public int partOrder = 0;
	public int secOrder = 0;

	public PhraseExt() {
		super();
	}

	public PhraseExt(int partNum, int partOrder, int secOrder) {
		part = partNum;
		this.partOrder = partOrder;
		this.secOrder = secOrder;
	}

	public PhraseExt(PhraseExt firstPhrase, double startTime) {
		part = firstPhrase.part;
		partOrder = firstPhrase.partOrder;
		secOrder = firstPhrase.secOrder;
		setStartTime(startTime);
	}

	@Override
	public Phrase copy() {
		Phrase phr = new PhraseExt(part, partOrder, secOrder);
		copyAttribs(phr);
		Enumeration enum1 = this.getNoteList().elements();
		while (enum1.hasMoreElements()) {
			phr.addNote(((Note) enum1.nextElement()).copy());
		}
		return phr;
	}

	private void copyAttribs(Phrase phr) {
		// NB: start time now covered by position
		phr.setStartTime(getStartTime());
		phr.setTitle(getTitle() + " copy");
		phr.setInstrument(getInstrument());
		phr.setAppend(getAppend());
		phr.setPan(getPan());
		phr.setLinkedPhrase(getLinkedPhrase());
		phr.setMyPart(getMyPart());
		phr.setTempo(getTempo());
		phr.setNumerator(getNumerator());
		phr.setDenominator(getDenominator());
	}
}
