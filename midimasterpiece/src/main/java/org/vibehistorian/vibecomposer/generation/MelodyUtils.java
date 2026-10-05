package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import org.vibehistorian.vibecomposer.Constants;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class MelodyUtils {

	public enum NoteTargetDirection {
		ANY(0), ASC(1), DESC(-1);

		public int direction;

		NoteTargetDirection(int direction) {
			this.direction = direction;
		}
	}

	//public static final List<Integer> chordyNotes = Arrays.asList(new Integer[] { 0, 2, 4, 7 });
	public static final List<Integer> cMajorSubstituteNotes = Arrays
			.asList(0, 2, 3, 5);
	public static List<List<Integer>> MELODY_PATTERNS = new ArrayList<>();
	public static List<List<Integer>> SOLO_MELODY_PATTERNS = new ArrayList<>();
	public static List<Integer> ALT_PATTERN_INDEXES = Arrays.asList(0, 1, 8, 9, 10, 12, 15, 16);
	public static List<Integer> BLOCK_CHANGE_JUMP_PREFERENCE = Arrays.asList(0, 7, 4, 3, 1, 2, 5, 6);

	public static List<List<Integer>> CHORD_DIRECTIONS = new ArrayList<>();

	public static final int NUM_LISTS = 4;

	static {

		CHORD_DIRECTIONS.add(Arrays.asList(-1, -1, 1, 2));
		CHORD_DIRECTIONS.add(Arrays.asList(-1, 0, 0, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, -1, -1, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, -1, 1, 0));
		CHORD_DIRECTIONS.add(Arrays.asList(0, -1, 0, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, -1, 1, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, 1, -1, 0));
		CHORD_DIRECTIONS.add(Arrays.asList(0, 1, -1, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, 1, 1, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, 1, 1, 2));
		//CHORD_DIRECTIONS.add(Arrays.asList(new Integer[] { 0, 0, 1, -1 }));
		CHORD_DIRECTIONS.add(Arrays.asList(0, 0, -1, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(1, -1, 1, 2));
		CHORD_DIRECTIONS.add(Arrays.asList(1, -1, 0, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(2, 0, 1, 2));
		CHORD_DIRECTIONS.add(Arrays.asList(2, 0, -1, 1));

		// ALT PATTERNS: 0, 1, 8, 9, 10, 12, 15, 16
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 1, 3));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 1, 3, 1, 2, 1, 4));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 1, 3, 2, 2, 3, 4));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 3, 4, 1, 2, 1, 3));
		MELODY_PATTERNS.add(Arrays.asList(1, 1, 2, 3));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 3, 1));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 3, 2));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 3, 3));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 2, 1));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 2, 3));
		MELODY_PATTERNS.add(Arrays.asList(1, 1, 2, 1)); // 7
		//MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 1, 1, 2, 2 }));
		MELODY_PATTERNS.add(Arrays.asList(1, 1, 1, 2));
		//MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 1, 1, 1, 1 }));
		// inverse patterns
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, 1, -1, 2));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, 2, -2, -1));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, 2, -1, 2));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, -1, 2, 3));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, -1, 2, 2));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, -1, -1, 1));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, -1, 1, 2));
		MELODY_PATTERNS.addAll(SOLO_MELODY_PATTERNS);


	}

	public static Integer[] getRandomForType(Integer type, Random melodyBlockGenerator) {
		return MelodyBlockUtils.getRandomForType(type, melodyBlockGenerator);
	}

	public static int[] normalizedCumulativeWeights(Integer... weights) {
		return MelodyBlockUtils.normalizedCumulativeWeights(weights);
	}

	public static List<Note> sortNotesByRhythmicImportance(List<Note> notes, MidiTiming timing) {
		List<Note> sorted = new ArrayList<>();
		List<Note> main8th = new ArrayList<>();
		List<Note> main16th = new ArrayList<>();
		List<Note> others = new ArrayList<>();

		double currTime = 0;
		for (Note n : notes) {
			if (MidiUtils.isMultiple(currTime + n.getOffset(), timing.eighthNote)) {
				main8th.add(n);
			} else if (MidiUtils.isMultiple(currTime + n.getOffset(), timing.sixteenthNote)) {
				main16th.add(n);
			} else {
				others.add(n);
			}
			currTime += n.getRhythmValue();
		}
		main8th.sort(Comparator.comparing(Note::getRhythmValue));
		main16th.sort(Comparator.comparing(Note::getRhythmValue));
		others.sort(Comparator.comparing(Note::getRhythmValue));
		sorted.addAll(others);
		sorted.addAll(main16th);
		sorted.addAll(main8th);
		LG.n("Others: " + others.size() + ", 16th: " + main16th.size() + ", 8th: "
				+ main8th.size());
		return sorted;
	}

	public static List<String> getChordsFromMelodyPitches(int orderOfMatch, List<Double> durations,
			Map<Integer, List<Note>> melodyMap, Map<String, Set<Integer>> freqMap, MidiTiming timing) {
		List<String> chordStrings = new ArrayList<>();
		String prevChordString = null;

		for (int i = 0; i < melodyMap.size(); i++) {
			List<Integer> chordFreqs = new ArrayList<>();
			double totalDuration = 0;
			for (Note n : melodyMap.get(i)) {
				double dur = n.getRhythmValue();
				double durCounter = 0.0;
				int index = i;
				if (index >= durations.size()) {
					index = durations.size() - 1;
				}
				while (durCounter < dur && totalDuration < durations.get(index)) {
					chordFreqs.add(n.getPitch() % 12);
					durCounter += timing.eighthNote;
					totalDuration += timing.eighthNote;
				}
			}

			Map<Integer, Long> freqCounts = chordFreqs.stream()
					.collect(Collectors.groupingBy(e -> e, Collectors.counting()));

			Map<Integer, Long> top3 = freqCounts.entrySet().stream()
					.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(4)
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
							(e1, e2) -> e1, LinkedHashMap::new));

			String chordString = MidiUtils.applyChordFreqMap(top3, orderOfMatch, prevChordString, freqMap);
			LG.d("Alternate chord #" + i + ": " + chordString);
			chordStrings.add(chordString);
			prevChordString = chordString;
		}
		return chordStrings;
	}

	public static Map<Integer, List<Integer>> patternsFromNotes(Map<Integer, List<Note>> fullMelodyMap,
			List<Double> progressionDurations, double beatDurationMultiplier, boolean flip,
			MidiTiming timing) {
		Map<Integer, List<Integer>> patterns = new HashMap<>();
		for (Integer chordKey : fullMelodyMap.keySet()) {
			patterns.put(chordKey, patternFromNotes(fullMelodyMap.get(chordKey), 1,
					progressionDurations.get(chordKey % progressionDurations.size()), beatDurationMultiplier, flip,
					timing));
		}
		return patterns;
	}

	private static List<Integer> patternFromNotes(List<Note> notes, int chordsTotal, Double measureTotal,
			double beatDurationMultiplier, boolean flip, MidiTiming timing) {
		int hits = (int) Math.round(
				chordsTotal * MidiTiming.MELODY_PATTERN_RESOLUTION * measureTotal / timing.wholeNote);
		measureTotal = (measureTotal == null) ? (chordsTotal * beatDurationMultiplier * timing.wholeNote)
				: measureTotal;
		double timeForHit = measureTotal / hits;
		List<Integer> pattern = new ArrayList<>();
		List<Double> durationBuckets = new ArrayList<>();
		for (int i = 1; i <= hits; i++) {
			durationBuckets.add(timeForHit * i - Constants.DBL_ERR);
			pattern.add(0);
		}

		if (notes == null || notes.isEmpty()) {
			return pattern;
		}

		int explored = 0;
		int counter = 0;
		List<Double> startTimes = new ArrayList<>();
		double current = 0.0;
		for (Note n : notes) {
			startTimes.add(current + n.getOffset());
			current += n.getRhythmValue();
		}

		boolean skipCounter = startTimes.get(0) < Constants.DBL_ERR;
		if (skipCounter) {
			pattern.set(0, (!notes.isEmpty() && notes.get(0).getPitch() < 0) ? 0
					: notes.get(0).getPitch());
		}

		for (Note n : notes) {
			if (counter == 0 && skipCounter) {
				counter++;
				continue;
			}
			for (int i = explored; i < hits; i++) {
				if (startTimes.get(counter) < durationBuckets.get(i)) {
					int nextPitch = Math.max(n.getPitch(), 0);
					pattern.set(i, nextPitch);
					explored = i;
					break;
				}
			}
			counter++;
		}
		if (flip) {
			pattern.replaceAll(integer -> integer > 0 ? 0 : 1);
		}
		return pattern;
	}

	public static List<Double> makeSurpriseTrioArpedDurations(List<Double> durations, MidiTiming timing) {
		List<Double> arpedDurations = new ArrayList<>(durations);
		for (int trioIndex = 0; trioIndex < arpedDurations.size() - 2; trioIndex++) {
			double sumThirds = arpedDurations.subList(trioIndex, trioIndex + 3).stream()
					.mapToDouble(e -> e).sum();
			boolean valid = false;
			if (MidiGeneratorUtils.isDottedNote(sumThirds, timing)) {
				sumThirds /= 3.0;
				for (int trio = trioIndex; trio < trioIndex + 3; trio++) {
					arpedDurations.set(trio, sumThirds);
				}
				valid = true;
			} else if (MidiUtils.isMultiple(sumThirds, timing.halfNote)) {
				if (sumThirds > timing.dottedHalfNote) {
					sumThirds /= 4.0;
					for (int trio = trioIndex; trio < trioIndex + 3; trio++) {
						arpedDurations.set(trio, sumThirds);
					}
					arpedDurations.add(trioIndex, sumThirds);
				} else {
					sumThirds /= 2.0;
					for (int trio = trioIndex + 1; trio < trioIndex + 3; trio++) {
						arpedDurations.set(trio, sumThirds);
					}
					arpedDurations.remove(trioIndex++);
				}
				valid = true;
			}

			if (valid) {
				return arpedDurations;
			}
		}
		return null;
	}
}
