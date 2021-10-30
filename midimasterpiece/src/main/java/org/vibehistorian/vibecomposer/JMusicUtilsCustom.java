package org.vibehistorian.vibecomposer;

import java.util.Enumeration;
import java.util.Random;
import java.util.Vector;

import org.vibehistorian.vibecomposer.MidiGenerator.Durations;
import org.vibehistorian.vibecomposer.Helpers.PartExt;

import jm.music.data.Note;
import jm.music.data.Part;
import jm.music.data.Phrase;
import jm.music.data.Score;
import jm.music.tools.Mod;

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

			//if (!n.isRest()) {
			if (nphr.getSize() > 0) { // if it is not the first note
				nphr.getNote(nphr.getSize() - 1)
						.setRhythmValue(((int) ((sst - prevsst) * 100000 + 0.5)) / 100000.0);
			} else {// if it is the first note to go in, set the startime
				nphr.setStartTime(sst);
			}

			//}
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

	public static Phrase doublePhrase(Phrase phr, int doubleTranspose, boolean pauseSquish,
			int dynamicChange) {
		Phrase phr2 = phr.copy();
		Mod.transpose(phr2, doubleTranspose);
		Mod.increaseDynamic(phr2, dynamicChange);
		Part phrPart = new Part();
		phrPart.add(phr2);
		phrPart.add(phr);
		if (pauseSquish) {
			Mod.consolidate(phrPart);
		} else {
			JMusicUtilsCustom.consolidate(phrPart);
		}

		phr = phrPart.getPhrase(0);
		return phr;
	}

	public static Score scoreCopy(Score score) {
		Score scrCopy = new Score();
		Enumeration<?> enum1 = score.getPartList().elements();
		while (enum1.hasMoreElements()) {
			PartExt part = (PartExt) enum1.nextElement();
			scrCopy.addPart(part.copy());
		}

		return scrCopy;

	}

	public static void humanize(Part part, Random generator, double rhythmVariation,
			boolean isDrum) {
		if (part == null) {
			return;
		}
		boolean left = true;
		Enumeration enum1 = part.getPhraseList().elements();
		while (enum1.hasMoreElements()) {
			Phrase phr = (Phrase) enum1.nextElement();
			humanize(phr, 0, rhythmVariation, 0, generator, isDrum);
		}
	}

	public static void humanize(Phrase phrase, int pitchVariation, double rhythmVariation,
			int dynamicVariation, Random generator, boolean isDrum) {
		if (phrase == null) {
			return;
		}
		Enumeration enum1 = phrase.getNoteList().elements();
		while (enum1.hasMoreElements()) {
			Note n = (Note) enum1.nextElement();
			// create new pitch value
			if (pitchVariation > 0) {
				n.setPitch(n.getPitch()
						+ (int) (generator.nextDouble() * (pitchVariation * 2) - pitchVariation));
			}
			// create new rhythm and duration values
			if (rhythmVariation > 0.0) {
				double var = (generator.nextDouble() * (rhythmVariation * 2) - rhythmVariation);
				double dur = n.getDuration();
				if (!isDrum && dur < Durations.SIXTEENTH_NOTE + MidiGenerator.DBL_ERR) {
					n.setOffset(n.getOffset() + var / 5);
					n.setDuration(n.getDuration() + var / 5);
				} else {
					n.setOffset(n.getOffset() + var);
					n.setDuration(n.getDuration() + var);
				}

			}
			// create new dynamic value
			if (dynamicVariation > 0) {
				n.setDynamic(n.getDynamic() + (int) (generator.nextDouble() * (dynamicVariation * 2)
						- dynamicVariation));
			}
		}
	}

}
