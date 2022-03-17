package org.vibehistorian.vibecomposer.Helpers;

import java.util.Enumeration;

import jm.music.data.Part;
import jm.music.data.Phrase;

public class PartExt extends Part {

	private static final long serialVersionUID = 4441440891894095L;

	private int trackNumber = -1;
	private boolean fillerPart = false;

	public PartExt(String string, int i, int j) {
		super(string, i, j);
	}

	public int getTrackNumber() {
		return trackNumber;
	}

	public void setTrackNumber(int trackNumber) {
		this.trackNumber = trackNumber;
	}

	@Override
	public Part copy() {
		PartExt i;
		i = new PartExt(this.getTitle() + " copy", this.getInstrument(), this.getChannel());
		Enumeration enum1 = this.getPhraseList().elements();
		while (enum1.hasMoreElements()) {
			Phrase oldPhrase = (Phrase) enum1.nextElement();
			i.addPhrase((Phrase) oldPhrase.copy());
		}

		i.setTempo(this.getTempo());
		i.setTimeIndex(this.getTimeIndex());
		i.setMyScore(this.getMyScore());
		i.setTrackNumber(this.getTrackNumber());
		i.setFillerPart(this.isFillerPart());
		return i;
	}

	public boolean isFillerPart() {
		return fillerPart;
	}

	public void setFillerPart(boolean fillerPart) {
		this.fillerPart = fillerPart;
	}

	public static PartExt makeFillerPart() {
		PartExt pe = new PartExt("Filler", 0, 1);
		pe.setFillerPart(true);
		return pe;
	}
}
