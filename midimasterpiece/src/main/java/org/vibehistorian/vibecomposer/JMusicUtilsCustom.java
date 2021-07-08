package org.vibehistorian.vibecomposer;

import java.util.Vector;

import jm.music.data.Note;
import jm.music.data.Part;
import jm.music.data.Phrase;

public class JMusicUtilsCustom {
	public static void consolidate(Part p) {

		Phrase[] phr = p.getPhraseArray();
		if (phr.length < 2)
			return;
		// the new phrase has the start time of the earliest one
		Phrase nphr = new Phrase(phr[0].getStartTime());

		Note n;
		boolean finished = false;
		double prevsst = phr[0].getStartTime();
		while (!finished) {
			double sst = Double.POSITIVE_INFINITY; // smallest start time
			// a temporary phrase (pointer to the one in the array
			Phrase tphr = null;

			//find the phrase with the smallest start time
			for (int i = 0; i < phr.length; i++) {

				if (phr[i].getSize() > 0 && phr[i].getStartTime() < sst) {
					sst = phr[i].getStartTime();
					tphr = phr[i];
				}
			}
			if (tphr == null) {
				finished = true;
				break;
			}
			//get a note out of that phrase and, if it is not a rest,
			// put it into the new phrase, adjusting the rhythmValue
			// of the previous note accordingly
			n = tphr.getNote(0);

			if (!n.isRest()) {
				if (nphr.getSize() > 0) { // if it is not the first note
					nphr.getNote(nphr.getSize() - 1)
							.setRhythmValue(((int) ((sst - prevsst) * 100000 + 0.5)) / 100000.0);
				} else {// if it is the first note to go in, set the startime
					nphr.setStartTime(sst);
				}

			}
			nphr.addNote(n);
			// adjust the start time and remove the note
			tphr.setStartTime(((int) ((sst + n.getRhythmValue()) * 100000 + 0.5)) / 100000.0);
			tphr.removeNote(0);

			prevsst = sst;

		}
		p.empty();
		p.addPhrase(nphr);
	}

	public static void addRestsToRhythmValues(Phrase phr) {
		Vector<Note> noteList = phr.getNoteList();
		Vector<Note> newNoteList = new Vector<>();

		Note prev = null;
		for (Note n : noteList) {
			if (n.isRest()) {
				if (prev != null) {
					prev.setRhythmValue(prev.getRhythmValue() + n.getRhythmValue());
				}
			} else {
				prev = n;
				newNoteList.add(n);
			}
		}
		phr.setNoteList(newNoteList);

	}

}
