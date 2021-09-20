package org.vibehistorian.vibecomposer.Enums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jm.music.data.Note;

public enum StrumType {
	UP, DOWN, RAND_U, RAND, RAND_D, RAND_W, X_RAND_U, X_RAND, X_RAND_D;

	public static final List<StrumType> viableTypes = Arrays
			.asList(new StrumType[] { UP, DOWN, RAND_U, RAND_D, X_RAND });

	public static void adjustNoteOffsets(StrumType type, List<Note> notes, double flam,
			Random gen) {

		if (gen == null && (type != UP && type != DOWN)) {
			return;
		}

		List<Double> noteOffsets = new ArrayList<>();
		List<Integer> noteIndexes = IntStream.iterate(0, e -> e + 1).limit(notes.size())
				.mapToObj(e -> e).collect(Collectors.toList());
		//System.out.println("Processing: " + type.toString());
		switch (type) {
		case UP:
			noteIndexes.forEach(e -> noteOffsets.add(flam * e));
			Collections.reverse(noteOffsets);
			break;
		case DOWN:
			noteIndexes.forEach(e -> noteOffsets.add(flam * e));
			break;
		case RAND_U:
			// X times random between 0 and flam*size - UP
			noteIndexes.forEach(e -> noteOffsets.add(gen.nextDouble() * flam * notes.size()));
			Collections.sort(noteOffsets);
			Collections.reverse(noteOffsets);
			break;
		case RAND:
			// RAND1 but unsorted
			noteIndexes.forEach(e -> noteOffsets.add(gen.nextDouble() * flam * notes.size()));
			break;
		case RAND_D:
			// X times random between 0 and flam*size - DOWN
			noteIndexes.forEach(e -> noteOffsets.add(gen.nextDouble() * flam * notes.size()));
			Collections.sort(noteOffsets);
			break;
		case RAND_W:
			// X times random within bucket 0-1, 1-2 etc.
			noteIndexes.forEach(e -> noteOffsets.add(e * flam + gen.nextDouble() * flam));
			Collections.sort(noteOffsets);
			Collections.reverse(noteOffsets);
			break;
		case X_RAND_U:
			// X times random between 0 and flam - UP
			noteIndexes.forEach(e -> noteOffsets.add(gen.nextDouble() * flam));
			Collections.sort(noteOffsets);
			Collections.reverse(noteOffsets);
			break;
		case X_RAND:
			// X times random between 0 and flam
			noteIndexes.forEach(e -> noteOffsets.add(gen.nextDouble() * flam));
			break;
		case X_RAND_D:
			// X times random between 0 and flam - DOWN
			noteIndexes.forEach(e -> noteOffsets.add(gen.nextDouble() * flam));
			Collections.sort(noteOffsets);
			break;
		default:
			throw new IllegalArgumentException("Unknown type");
		}

		for (int i = notes.size() - 1; i >= 0; i--) {
			notes.get(i).setOffset(noteOffsets.get(i));
		}
	}
}
