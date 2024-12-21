package org.vibehistorian.vibecomposer.Helpers;

import jm.music.data.Part;

import java.util.Enumeration;

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
			PhraseExt oldPhrase = (PhraseExt) enum1.nextElement();
			i.addPhrase(oldPhrase.copy());
		}

		i.setTempo(this.getTempo()); // double
		i.setTimeIndex(this.getTimeIndex()); // int
		i.setMyScore(this.getMyScore()); // TBD - score
		i.setTrackNumber(this.getTrackNumber()); // int
		i.setFillerPart(this.isFillerPart()); // bool
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
