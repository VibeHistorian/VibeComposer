package org.vibehistorian.midimasterpiece.midigenerator.Helpers;

import java.util.Random;
import java.util.function.Consumer;

import jm.music.data.Note;

public class UnsameNotes implements Consumer<Note[]> {
	
	@Override
	public void accept(Note[] t) {
		int sameNoteCounter = 0;
		Random gen = new Random();
		//1,2,3,4
		int sameNoteChange = gen.nextInt(4) + 1;
		sameNoteChange = (sameNoteChange <= 2) ? sameNoteChange * -1 : sameNoteChange - 2;
		int previousPitch = 0;
		for (Note n : t) {
			if (n.getPitch() == previousPitch) {
				sameNoteCounter++;
			} else {
				previousPitch = n.getPitch();
				sameNoteCounter = 0;
			}
			if (sameNoteCounter >= 2) {
				System.out.println("UNSAMING NOTE!: " + previousPitch + ", BY: " + sameNoteChange);
				n.setPitch(previousPitch + sameNoteChange);
				sameNoteCounter = 0;
				previousPitch = previousPitch + sameNoteChange;
				sameNoteChange = gen.nextInt(4) + 1;
				sameNoteChange = (sameNoteChange <= 2) ? sameNoteChange * -1 : sameNoteChange - 2;
			}
		}
		
	}
	
}
