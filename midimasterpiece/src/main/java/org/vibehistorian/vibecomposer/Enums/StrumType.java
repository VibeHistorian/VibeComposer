package org.vibehistorian.vibecomposer.Enums;

import jm.music.data.Note;
import org.vibehistorian.vibecomposer.MelodyUtils;
import org.vibehistorian.vibecomposer.OMNI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public enum StrumType {
	ARP_U(Strums.STRUM_ARP), ARP_D(Strums.STRUM_ARP), RAND_U(Strums.STRUM_MED),
	RAND(Strums.STRUM_MED), RAND_D(Strums.STRUM_MED), RAND_WU(Strums.STRUM_MED),
	HUMAN_U(Strums.STRUM_HUMAN), HUMAN(Strums.STRUM_HUMAN), HUMAN_D(Strums.STRUM_HUMAN);

	public List<Integer> CHOICES;

	StrumType(List<Integer> strums) {
		CHOICES = strums;
	}

	public static final List<StrumType> ARPY = Arrays.asList(new StrumType[] { ARP_U, ARP_D });
	public static final List<StrumType> RANDY = Arrays.asList(new StrumType[] { RAND_WU });
	public static final List<StrumType> HUMANY = Arrays
			.asList(new StrumType[] { HUMAN_U, HUMAN, HUMAN_D });

	public static final int[] STRUMMINESS_WEIGHTS = MelodyUtils.normalizedCumulativeWeights(27, 3, 70);

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
		//LG.d("Processing: " + type.toString());
		boolean sort = false;
		boolean reverse = false;
		switch (type) {
		case ARP_U:
			noteIndexes.forEach(e -> noteOffsets.add(flam * e));
			reverse = true;
			break;
		case ARP_D:
			noteIndexes.forEach(e -> noteOffsets.add(flam * e));
			break;
		case RAND_U:
			// X times random between 0 and flam*size - UP
			noteIndexes.forEach(e -> noteOffsets.add(gen.nextDouble() * flam * notes.size()));
			sort = true;
			reverse = true;
			break;
		case RAND:
			// RAND1 but unsorted
			noteIndexes.forEach(e -> noteOffsets.add(gen.nextDouble() * flam * notes.size()));
			break;
		case RAND_D:
			// X times random between 0 and flam*size - DOWN
			noteIndexes.forEach(e -> noteOffsets.add(gen.nextDouble() * flam * notes.size()));
			sort = true;
			break;
		case RAND_WU:
			// X times random within bucket 0-1, 1-2 etc.
			noteIndexes.forEach(e -> noteOffsets.add(e * flam + gen.nextDouble() * flam));
			sort = true;
			reverse = true;
			break;
		case HUMAN_U:
			// X times random between 0 and flam - UP
			noteIndexes.forEach(e -> noteOffsets.add(gen.nextDouble() * flam));
			sort = true;
			reverse = true;
			break;
		case HUMAN:
			// X times random between 0 and flam
			noteIndexes.forEach(e -> noteOffsets.add(gen.nextDouble() * flam));
			break;
		case HUMAN_D:
			// X times random between 0 and flam - DOWN
			noteIndexes.forEach(e -> noteOffsets.add(gen.nextDouble() * flam));
			sort = true;
			break;
		default:
			throw new IllegalArgumentException("Unknown type");
		}

		if (sort) {
			Collections.sort(noteOffsets);
		}
		if (reverse) {
			Collections.reverse(noteOffsets);
		}

		for (int i = notes.size() - 1; i >= 0; i--) {
			notes.get(i).setOffset(noteOffsets.get(i));
		}
		if (type != RAND) {
			if (reverse) {
				notes.get(notes.size() - 1).setOffset(0);
			} else {
				notes.get(0).setOffset(0);
			}
		} else {
			// zero-out random note
			notes.get(gen.nextInt(notes.size())).setOffset(0);
		}

	}
}

class Strums {

	public static final List<Integer> STRUM_ARP = Arrays
			.asList(new Integer[] { 250, 333, 375, 500, 666, 1000, 1500, 2000 });
	public static final List<Integer> STRUM_MED = Arrays
			.asList(new Integer[] { 62, 125, 250, 333, 375 });
	public static final List<Integer> STRUM_HUMAN = Arrays.asList(new Integer[] { 31, 62, 125 });
}
