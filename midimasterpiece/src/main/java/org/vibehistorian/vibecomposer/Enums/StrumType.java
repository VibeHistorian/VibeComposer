package org.vibehistorian.vibecomposer.Enums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.vibehistorian.vibecomposer.Rhythm;
import org.vibehistorian.vibecomposer.Helpers.OMNI;

import jm.music.data.Note;

public enum StrumType {
	ARP_U(Strums.STRUM_ARP), ARP_D(Strums.STRUM_ARP), RAND_U(Strums.STRUM_MED),
	RAND(Strums.STRUM_MED), RAND_D(Strums.STRUM_MED), RAND_W(Strums.STRUM_MED),
	HUMAN_U(Strums.STRUM_HUMAN), HUMAN(Strums.STRUM_HUMAN), HUMAN_D(Strums.STRUM_HUMAN);

	public List<Integer> CHOICES;

	StrumType(List<Integer> strums) {
		CHOICES = strums;
	}

	public static final List<StrumType> ARPY = Arrays
			.asList(new StrumType[] { ARP_U, ARP_D, RAND_U, RAND_D, HUMAN });
	public static final List<StrumType> RANDY = Arrays
			.asList(new StrumType[] { RAND_U, RAND, RAND_D, RAND_W });
	public static final List<StrumType> HUMANY = Arrays
			.asList(new StrumType[] { HUMAN_U, HUMAN, HUMAN_D });

	public static final int[] STRUMMINESS_WEIGHTS = Rhythm
			.normalizedCumulativeWeights(new int[] { 30, 10, 60 });

	public static List<StrumType> getWeighted(int value) {
		List<StrumType>[] lists = new List[] { ARPY, RANDY, HUMANY };
		return OMNI.getWeightedValue(lists, value, STRUMMINESS_WEIGHTS);
	}

	public static void adjustNoteOffsets(StrumType type, List<Note> notes, double flam,
			Random gen) {

		if (gen == null && (type != ARP_U && type != ARP_D)) {
			return;
		}

		List<Double> noteOffsets = new ArrayList<>();
		List<Integer> noteIndexes = IntStream.iterate(0, e -> e + 1).limit(notes.size())
				.mapToObj(e -> e).collect(Collectors.toList());
		//System.out.println("Processing: " + type.toString());
		switch (type) {
		case ARP_U:
			noteIndexes.forEach(e -> noteOffsets.add(flam * e));
			Collections.reverse(noteOffsets);
			break;
		case ARP_D:
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
		case HUMAN_U:
			// X times random between 0 and flam - UP
			noteIndexes.forEach(e -> noteOffsets.add(gen.nextDouble() * flam));
			Collections.sort(noteOffsets);
			Collections.reverse(noteOffsets);
			break;
		case HUMAN:
			// X times random between 0 and flam
			noteIndexes.forEach(e -> noteOffsets.add(gen.nextDouble() * flam));
			break;
		case HUMAN_D:
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

class Strums {

	public static final List<Integer> STRUM_ARP = Arrays
			.asList(new Integer[] { 125, 166, 250, 333, 500, 750, 1000 });
	public static final List<Integer> STRUM_MED = Arrays
			.asList(new Integer[] { 62, 125, 166, 250 });
	public static final List<Integer> STRUM_HUMAN = Arrays.asList(new Integer[] { 16, 31, 62 });
}
