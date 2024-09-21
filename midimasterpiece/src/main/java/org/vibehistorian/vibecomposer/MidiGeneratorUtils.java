package org.vibehistorian.vibecomposer;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;

import jm.music.data.Note;
import jm.music.data.Phrase;

public class MidiGeneratorUtils {

	static List<Integer> generateMelodyOffsetDirectionsFromChordProgression(List<int[]> progression,
			boolean roots, int randomSeed) {
		Random rand = new Random(randomSeed);
		List<Integer> dirs = new ArrayList<>();
		dirs.add(0);
		int current = roots ? progression.get(0)[0]
				: progression.get(0)[rand.nextInt(progression.get(0).length)];
		for (int i = 1; i < progression.size(); i++) {
			int next = roots ? progression.get(i)[0]
					: progression.get(i)[rand.nextInt(progression.get(i).length)];
			dirs.add(Integer.compare(next, current));
			current = next;
		}
		return dirs;
	}

	static List<Integer> randomizedChordDirections(int chords, int randomSeed) {
		Random rand = new Random(randomSeed);

		List<Integer> chordDirs = new ArrayList<>(MelodyUtils.CHORD_DIRECTIONS
				.get(rand.nextInt(MelodyUtils.CHORD_DIRECTIONS.size())));
		while (chordDirs.size() < chords) {
			chordDirs.addAll(chordDirs);
		}
		chordDirs = chordDirs.subList(0, chords);
		return chordDirs;
		/*
		List<Integer> dirs = new ArrayList<>();
		//dirs.add(0);
		for (int i = 0; i < chords; i++) {
			dirs.add(rand.nextInt(3) - 1);
		}
		return dirs;*/
	}

	static List<Integer> convertRootsToOffsets(List<Integer> roots, int targetMode) {
		List<Integer> offsets = new ArrayList<>();
		for (int i = 0; i < roots.size(); i++) {
			int value = -1 * roots.get(i);
			if (targetMode == 0) {
				value /= 2;
			}
			offsets.add(value);
		}
		return offsets;
	}

	static List<Integer> multipliedDirections(List<Integer> directions, int randomSeed,
			int targetNoteVariation) {
		if (targetNoteVariation < 1) {
			targetNoteVariation = 1;
		}
		Random rand = new Random(randomSeed);
		List<Integer> multiDirs = new ArrayList<>();
		for (Integer o : directions) {
			int multiplied = (rand.nextInt(targetNoteVariation) + 1) * o;
			if (o < 0) {
				// small correction for too low dips
				multiplied++;
			}
			multiDirs.add(multiplied);
		}
		return multiDirs;
	}

	static List<Integer> getRootIndexes(List<int[]> chords) {
		List<Integer> rootIndexes = new ArrayList<>();
		for (int i = 0; i < chords.size(); i++) {
			int root = chords.get(i)[0];
			int rootIndex = MidiUtils.MAJ_SCALE.indexOf(root % 12);
			if (rootIndex < 0) {
				int closestPitch = MidiUtils.getClosestFromList(MidiUtils.MAJ_SCALE, root % 12);
				rootIndex = MidiUtils.MAJ_SCALE.indexOf(closestPitch % 12);
			}
			rootIndexes.add(rootIndex);
		}
		return rootIndexes;
	}

	public static int adjustChanceParamForTransition(int param, Section sec, int chordNum,
			int chordSize, int maxEffect, double affectedMeasure, boolean reverseEffect) {
		if (chordSize < 2 || !sec.isTransition()) {
			return param;
		}

		int minAffectedChord = OMNI.clamp((int) (affectedMeasure * chordSize) - 1, 1,
				chordSize - 1);
		//LG.d("Min affected: " + minAffectedChord);
		if (chordNum < minAffectedChord) {
			return param;
		}
		//LG.d("Old param: " + param);

		int chordRange = chordSize - 1 - minAffectedChord;
		double effect = (chordRange > 0) ? ((chordNum - minAffectedChord) / ((double) chordRange))
				: 1.0;

		int transitionType = sec.getTransitionType();

		int multiplier = reverseEffect ? -1 : 1;
		if (transitionType == 1) {
			param += maxEffect * effect * multiplier;
		} else {
			param -= maxEffect * effect * multiplier;
		}
		param = OMNI.clampChance(param);
		//LG.d("New param: " + param);
		return param;
	}

	static Map<Integer, List<Integer>> getChordNoteChoicesFromChords(List<int[]> chords) {
		Map<Integer, List<Integer>> choiceMap = new HashMap<>();
		int counter = 0;
		for (int[] c : chords) {
			List<Integer> choices = new ArrayList<>();
			for (int pitch : c) {
				int index = MidiUtils.MAJ_SCALE.indexOf(pitch % 12);
				if (index >= 0) {
					choices.add(index);
					choices.add(index - 7);
				}
			}
			if (choices.isEmpty()) {
				//choices.add(0);
				for (int pitch : c) {
					choices.add(MidiUtils.MAJ_SCALE.indexOf(MidiUtils.getClosestPitchFromList(MidiUtils.MAJ_SCALE, pitch)));
				}
				LG.i("No chord note present in Chord: " + Arrays.toString(c));
			}
			choiceMap.put(counter++, choices);
		}
		return choiceMap;
	}

	public static List<Integer> generateOffsets(List<String> chordStrings, int randomSeed,
			int targetMode, int targetNoteVariation, Boolean isPublic) {
		List<int[]> chords = new ArrayList<>();
		for (int i = 0; i < chordStrings.size(); i++) {
			chords.add(MidiUtils.mappedChord(chordStrings.get(i)));
		}
		return MidiGeneratorUtils.generateOffsets(chords, randomSeed, targetMode,
				targetNoteVariation);
	}

	static Pair<Integer, Integer> normalizeNotePitch(int startingNote, int startingPitch) {
		if (startingNote >= 7) {
			int divided = startingNote / 7;
			startingPitch += (12 * divided);
			startingNote -= (7 * divided);
		} else if (startingNote < 0) {
			int divided = 1 + ((-1 * (startingNote + 1)) / 7);
			startingPitch -= (12 * divided);
			startingNote += (7 * divided);
		}
		return Pair.of(startingNote, startingPitch);
	}

	static List<Integer> generateOffsets(List<int[]> chords, int randomSeed, int targetMode,
			int targetNoteVariation) {
		List<Integer> chordOffsets = convertRootsToOffsets(getRootIndexes(chords), targetMode);
		List<Integer> multipliedDirections = multipliedDirections(
				MidiGenerator.gc != null && MidiGenerator.gc.isMelodyUseDirectionsFromProgression()
						? generateMelodyOffsetDirectionsFromChordProgression(chords, true,
								randomSeed)
						: randomizedChordDirections(chords.size(), randomSeed),
				randomSeed + 1, targetNoteVariation);
		List<Integer> offsets = new ArrayList<>();
		for (int i = 0; i < chordOffsets.size(); i++) {
			/*LG.d("Chord offset: " + chordOffsets.get(i) + ", multiDir: "
					+ multipliedDirections.get(i));*/
			if (targetMode == 1) {
				offsets.add(chordOffsets.get(i) + multipliedDirections.get(i));
			} else {
				offsets.add(multipliedDirections.get(i));
			}

		}
		if (targetMode >= 1) {
			Map<Integer, List<Integer>> choiceMap = getChordNoteChoicesFromChords(chords);
			Random offsetRandomizer = new Random(randomSeed);
			for (int i = 0; i < chordOffsets.size(); i++) {
				List<Integer> choices = choiceMap.get(i);
				int offset = (targetMode == 1) ? offsets.get(i) - chordOffsets.get(i)
						: offsets.get(i);
				int chordTargetNote = choices.contains(offset) ? offset
						: MidiUtils.getClosestFromList(choices,
								offset + (offsetRandomizer.nextInt(100) < 75 ? 1 : 0));
				LG.d("Offset old: " + offset + ", C T NOte: " + chordTargetNote);
				offsets.set(i, (targetMode == 1) ? chordTargetNote + chordOffsets.get(i)
						: chordTargetNote);
			}
			if (targetMode == 2) {
				int last = offsets.get(offsets.size() - 1);
				if (offsets.size() > 3 && (last == offsets.get(offsets.size() - 3))) {
					last += (new Random(randomSeed).nextBoolean() ? 2 : -2);
					offsets.set(offsets.size() - 1,
							MidiUtils.getClosestFromList(choiceMap.get(offsets.size() - 1), last));
					LG.i("Last offset moved!");
				}
			}
		} else {
			int last = offsets.get(offsets.size() - 1);
			if (offsets.size() > 3 && (last == offsets.get(offsets.size() - 3))) {
				last += (new Random(randomSeed).nextInt(100) < 75 ? 1 : -1);
				offsets.set(offsets.size() - 1, last);
			}
		}
		// try to set one of the offsets as the root note, if a close one is available and no offset is root yet
		if (new Random(randomSeed).nextInt(100) < 90 && (targetMode == 2) && !offsets.contains(0)) {
			LG.i("Trying to insert root note into note targets..");
			List<Integer> randomIterationOrder = IntStream.range(0, offsets.size()).boxed().collect(Collectors.toList());
			Collections.shuffle(randomIterationOrder, new Random(randomSeed + 1324));
			for (int i = 0; i < offsets.size(); i++) {
					if (MidiUtils.containsRootNote(chords.get(i % chords.size())) && Math.abs(offsets.get(i)) <= 2) {
						offsets.set(i, 0);
						LG.i("Root note inserted into note targets! At index: " + i);
						break;
					}
			}
		}

		//int min = offsets.stream().min((e1, e2) -> e1.compareTo(e2)).get();
		/*if (min == -1) {
			for (int i = 0; i < offsets.size(); i++) {
				offsets.set(i, offsets.get(i) + 1);
			}
		}*/

		LG.d("RANDOMIZED OFFSETS");
		return offsets;
	}

	static List<Boolean> generateMelodyDirectionsFromChordProgression(List<int[]> progression,
			boolean roots) {

		List<Boolean> ascDirectionList = new ArrayList<>();

		for (int i = 0; i < progression.size(); i++) {
			if (roots) {
				int current = progression.get(i)[0];
				int next = progression.get((i + 1) % progression.size())[0];
				ascDirectionList.add(Boolean.valueOf(current <= next));
			} else {
				int current = progression.get(i)[progression.get(i).length - 1];
				int next = progression.get((i + 1)
						% progression.size())[progression.get((i + 1) % progression.size()).length
								- 1];
				ascDirectionList.add(Boolean.valueOf(current <= next));
			}

		}

		return ascDirectionList;
	}

	static List<Double> generateMelodyDirectionChordDividers(int chords, Random dirGen) {
		List<Double> map = new ArrayList<>();
		for (int i = 0; i < chords; i++) {
			double divider = dirGen.nextDouble() * 0.80 + 0.20;
			map.add(divider);

		}
		return map;
	}

	public static boolean isDottedNote(double note) {
		if (MidiUtils.roughlyEqual(MidiGenerator.Durations.DOTTED_QUARTER_NOTE, note))
			return true;
		if (MidiUtils.roughlyEqual(MidiGenerator.Durations.DOTTED_WHOLE_NOTE, note))
			return true;
		if (MidiUtils.roughlyEqual(MidiGenerator.Durations.DOTTED_HALF_NOTE, note))
			return true;
		if (MidiUtils.roughlyEqual(MidiGenerator.Durations.DOTTED_EIGHTH_NOTE, note))
			return true;
		if (MidiUtils.roughlyEqual(MidiGenerator.Durations.DOTTED_SIXTEENTH_NOTE, note))
			return true;
		return false;
	}

	public static int getAllowedPitchFromRange(int min, int max, double posInChord,
			Random splitNoteGen) {

		boolean allowBs = posInChord > 0.66;
		//LG.i("Min: " + min + ", max: " + max);
		int normMin = min % 12;
		int normMax = max % 12;
		if (normMax <= normMin) {
			normMax += 12;
		}

		List<Integer> allowedPitches = new ArrayList<>(MidiUtils.MAJ_SCALE);
		int allowedPitchSize = allowedPitches.size();
		for (int i = 0; i < allowedPitchSize; i++) {

			allowedPitches.add(allowedPitches.get(i) + 12);
		}
		//LG.i("Size: " + allowedPitches.size());
		final int finalNormMax = normMax;
		//LG.i("Contents: " + StringUtils.join(allowedPitches, ", "));
		allowedPitches.removeIf(e -> !(normMin <= e && e <= finalNormMax));
		if (!allowBs) {
			allowedPitches.remove(Integer.valueOf(11));
			allowedPitches.remove(Integer.valueOf(23));
		}
		//LG.i("Contents: " + StringUtils.join(allowedPitches, ", "));
		int normReturnPitch = allowedPitches.get(splitNoteGen.nextInt(allowedPitches.size()));
		//LG.i("Return n: " + normReturnPitch);
		while (normReturnPitch < min) {
			normReturnPitch += 12;
		}
		return normReturnPitch;
	}

	private static boolean fits(int pitch, int min, int max, boolean isInclusive) {
		if (isInclusive) {
			if (pitch >= min && pitch <= max) {
				return true;
			} else {
				return false;
			}
		} else {
			if (pitch > min && pitch < max) {
				return true;
			} else {
				return false;
			}
		}
	}

	public static List<Double> getSustainedDurationsFromPattern(List<Integer> pattern,
			double addDur) {
		List<Double> durations = new ArrayList<>();
		double dur = 0;
		int end = pattern.size();
		for (int i = 0; i < end; i++) {
			if (pattern.get(i) < 1) {
				dur += addDur;
			} else {
				dur = addDur;
			}
			if (i < end - 1 && pattern.get(i + 1) == 1) {
				durations.add(dur);
			}
		}
		durations.add(dur);

		return durations;
	}

	public static int multiplyVelocity(int velocity, int multiplierPercentage, int maxAdjust,
			int minAdjust) {
		if (multiplierPercentage == 100) {
			return velocity;
		} else if (multiplierPercentage > 100) {
			return Math.min(127 - maxAdjust, velocity * multiplierPercentage / 100);
		} else {
			return Math.max(0 + minAdjust, velocity * multiplierPercentage / 100);
		}
	}

	static int getStartingNote(List<int[]> stretchedChords, List<Integer> blockChordNoteChoices,
			int chordNum, int BLOCK_TARGET_MODE) {

		int chordNumIndex = chordNum % stretchedChords.size();
		int chordNoteChoiceIndex = (BLOCK_TARGET_MODE == 2
				&& chordNum == blockChordNoteChoices.size()) ? (chordNum - 1)
						: (chordNum % blockChordNoteChoices.size());
		int[] chord = stretchedChords.get(chordNumIndex);

		int startingPitch = (BLOCK_TARGET_MODE == 0)
				? MidiUtils.getXthChordNote(blockChordNoteChoices.get(chordNoteChoiceIndex), chord)
				: ((BLOCK_TARGET_MODE == 1) ? chord[0] : (5 * 12));
		int startingOct = startingPitch / 12;
		int startingNote = MidiUtils.MAJ_SCALE.indexOf(startingPitch % 12);
		if (startingNote < 0) {
			throw new IllegalArgumentException("BAD STARTING NOTE!");
		}
		return startingNote + startingOct * 7
				+ ((BLOCK_TARGET_MODE > 0) ? blockChordNoteChoices.get(chordNoteChoiceIndex) : 0);
	}

	static void applyNoteLengthMultiplier(List<Note> notes, int noteLengthMultiplier) {
		if (noteLengthMultiplier == 100) {
			return;
		}
		boolean avoidSamePitchCollision = true;
		List<Pair<Double, Note>> sn = JMusicUtilsCustom.makeNoteStartTimes(notes);
		for (int i = 0; i < sn.size(); i++) {
			Note n = sn.get(i).getRight();
			double duration = n.getDuration() * noteLengthMultiplier / 100.0;
			if (avoidSamePitchCollision && noteLengthMultiplier > 100) {
				if (i < sn.size() - 1 && n.getPitch() == sn.get(i + 1).getRight().getPitch()) {
					double difference = sn.get(i + 1).getLeft() - sn.get(i).getLeft();
					duration = Math.min(duration, difference);
				} else if (i < sn.size() - 2
						&& n.getPitch() == sn.get(i + 2).getRight().getPitch()) {
					double difference = sn.get(i + 2).getLeft() - sn.get(i).getLeft();
					duration = Math.min(duration, difference);
				}
			}
			n.setDuration(duration);
		}
	}

	static int addAccent(int velocity, Random accentGenerator, int accent) {
		// 80 + 15 +- 5 + 100/20 -> 95-105 vel.
		int newVelocity = velocity + MidiGenerator.BASE_ACCENT + accentGenerator.nextInt(11) - 5
				+ accent / 20;
		return OMNI.clampMidi(newVelocity);
	}

	static void applyCrescendoMultiplierMinimum(List<Note> notes, double maxDuration,
			double crescendoStartPercentage, double maxMultiplierAdd, double minimum) {
		double dur = 0.0;
		double start = maxDuration * crescendoStartPercentage;
		for (Note n : notes) {
			if (dur > start) {
				double multiplier = minimum
						+ maxMultiplierAdd * ((dur - start) / (maxDuration - start));
				if (multiplier < 0.1) {
					n.setPitch(Note.REST);
				} else {
					n.setDynamic(OMNI.clampVel(n.getDynamic() * multiplier));
				}
				//LG.d("Applied multiplier: " + multiplier);
			}
			dur += n.getRhythmValue();
		}
	}

	static void applyCrescendoMultiplier(List<Note> notes, double maxDuration,
			double crescendoStartPercentage, double maxMultiplierAdd) {
		applyCrescendoMultiplierMinimum(notes, maxDuration, crescendoStartPercentage,
				maxMultiplierAdd, 1);
	}

	static void processSectionTransition(Section sec, List<Note> notes, double maxDuration,
			double crescendoStartPercentage, double maxMultiplierAdd, double muteStartPercentage) {
		if (sec.isTransition()) {
			applyCrescendoMultiplier(notes, maxDuration, crescendoStartPercentage,
					maxMultiplierAdd);
			if (sec.getTransitionType() == 3) {
				applyCrescendoMultiplierMinimum(notes, maxDuration, muteStartPercentage, 0.05,
						0.05);
			}
		}
	}

	static void applyBadIntervalRemoval(List<Note> fullMelody) {
		if (fullMelody.isEmpty()) {
			return;
		}
		int previousPitch = fullMelody.get(0).getPitch();
		if (previousPitch < 0) {
			previousPitch = -1;
		}
		for (int i = 1; i < fullMelody.size(); i++) {
			Note n = fullMelody.get(i);
			int pitch = n.getPitch();
			if (pitch < 0) {
				continue;
			}
			// remove all instances of B-F and F-B (the only interval of 6 within the key)
			if (previousPitch % 12 == 11 && Math.abs(pitch - previousPitch) == 6) {
				n.setPitch(pitch - 1);
			} else if (pitch % 12 == 11 && Math.abs(pitch - previousPitch) == 6) {
				n.setPitch(pitch + 1);
			} else if ((i > 0) && (pitch - previousPitch >= 12)) {
				// set G as a step for too wild intervals
				int avgPitch = (pitch + previousPitch) / 2;
				int avgSemi = avgPitch % 12;
				if (avgSemi > 9) {
					n.setPitch(avgPitch - avgSemi + 12);
				} else if (avgSemi < 3) {
					n.setPitch(avgPitch - avgSemi);
				} else {
					n.setPitch(avgPitch - avgSemi + 7);
				}
				LG.i("Reducing interval - changing note to: " + n.getPitch());
				//n.setPitch(previousPitch - (previousPitch % 12) + 7);
			}
			previousPitch = n.getPitch();
		}


	}

	static void replaceAvoidNotes(Map<Integer, List<Note>> fullMelodyMap, List<int[]> chords,
			int randomSeed, int notesToAvoid) {
		Random rand = new Random(randomSeed);
		for (int i = 0; i < fullMelodyMap.keySet().size(); i++) {
			Set<Integer> avoidNotes = MidiUtils.avoidNotesFromChord(chords.get(i % chords.size()),
					notesToAvoid);
			//LG.d(StringUtils.join(avoidNotes, ","));
			List<Note> notes = fullMelodyMap.get(i);
			for (int j = 0; j < notes.size(); j++) {
				Note n = notes.get(j);
				int oldPitch = n.getPitch();
				if (oldPitch < 0) {
					continue;
				}
				//LG.d("Note: " + n.getPitch() + ", RV: " + n.getRhythmValue());
				boolean avoidAllLengths = true;
				if (avoidAllLengths || (n.getRhythmValue() > MidiGenerator.Durations.EIGHTH_NOTE
						- MidiGenerator.DBL_ERR)) {
					if (avoidNotes.contains(oldPitch % 12)) {
						int normalizedPitch = oldPitch % 12;
						int pitchIndex = MidiUtils.MAJ_SCALE.indexOf(normalizedPitch);
						int upOrDown = rand.nextBoolean() ? 1 : -1;
						int newPitch = oldPitch - normalizedPitch;
						newPitch += MidiUtils.MAJ_SCALE.get((pitchIndex + upOrDown + 7) % 7);
						if (pitchIndex == 6 && upOrDown == 1) {
							newPitch += 12;
						} else if (pitchIndex == 0 && upOrDown == -1) {
							newPitch -= 12;
						}
						n.setPitch(newPitch);
						/*LG.d("Avoiding note: " + j + ", in chord: " + i + ", pitch change: "
								+ (oldPitch - newPitch));*/
					}
				}

			}
		}

	}

	static int pickRandomBetweenIndexesInclusive(int[] chord, int startIndex, int endIndex,
			Random generator, double posInChord) {
		//clamp
		if (startIndex < 0)
			startIndex = 0;
		if (endIndex > chord.length - 1) {
			endIndex = chord.length - 1;
		}
		if (((chord[startIndex] % 12 == 11) && (chord[endIndex] % 12 == 11)) || posInChord > 0.66) {
			// do nothing
			//LG.d("The forced B case, " + posInChord);
		} else if (chord[startIndex] % 12 == 11) {
			startIndex++;
			//LG.d("B start avoided");
		} else if (chord[endIndex] % 12 == 11) {
			endIndex--;
			//LG.d("B end avoided");
		}
		int index = generator.nextInt(endIndex - startIndex + 1) + startIndex;
		return chord[index];
	}

	static int selectClosestIndexFromChord(int[] chord, int previousNotePitch,
			boolean directionUp) {
		if (directionUp) {
			for (int i = 0; i < chord.length; i++) {
				if (previousNotePitch < chord[i]) {
					return i;
				}
			}
			return chord.length - 1;
		} else {
			for (int i = chord.length - 1; i > 0; i--) {
				if (previousNotePitch > chord[i]) {
					return i;
				}
			}
			return 0;
		}

	}

	static String generateSpicyChordString(Random spiceGenerator, String chordString,
			List<String> spicyChordList) {
		List<String> spicyChordListCopy = new ArrayList<>(spicyChordList);
		String firstLetter = chordString.substring(0, 1);
		List<Integer> targetScale = Arrays.asList(ScaleMode.IONIAN.noteAdjustScale);
		int transposeByLetter = targetScale.get(MidiUtils.CHORD_FIRST_LETTERS.indexOf(firstLetter));
		if (MidiGenerator.gc != null && MidiGenerator.gc.isSpiceForceScale()) {
			spicyChordListCopy
					.removeIf(e -> !MidiUtils.isSpiceValid(transposeByLetter, e, targetScale));
		}

		//LG.d(StringUtils.join(spicyChordListCopy, ", "));

		if (spicyChordListCopy.isEmpty()) {
			return chordString;
		}
		String spicyChordString = firstLetter
				+ spicyChordListCopy.get(spiceGenerator.nextInt(spicyChordListCopy.size()));
		if (chordString.endsWith("m") && spicyChordString.contains("maj")) {
			// keep formerly minor as minor
			spicyChordString = spicyChordString.replace("maj", "m");
		} else if (chordString.length() == 1 && spicyChordString.contains("m")
				&& !spicyChordString.contains("dim") && !spicyChordString.contains("maj")
				&& !spicyChordString.contains("mM")) {
			// keep formerly major as major
			spicyChordString = spicyChordString.replace("m", "maj");
		}
		return spicyChordString;
	}

	static void multiDelayPhrase(Phrase phr, int delayCount, double delayAmnt,
			double volMultiplier) {
		if (delayCount <= 0) {
			return;
		}
		List<Double> delays = DoubleStream.iterate(delayAmnt, e -> e + delayAmnt).limit(delayCount)
				.boxed().collect(Collectors.toList());
		List<Double> volMultipliers = DoubleStream.iterate(volMultiplier, e -> e * volMultiplier)
				.limit(delays.size()).boxed().collect(Collectors.toList());
		multiDelayPhrase(phr, delays, volMultipliers);

	}

	static void multiDelayPhrase(Phrase phr, List<Double> delays) {
		if (delays.isEmpty()) {
			return;
		}
		List<Double> volMultipliers = DoubleStream.iterate(0.8, e -> e * 0.8).limit(delays.size())
				.boxed().collect(Collectors.toList());
		multiDelayPhrase(phr, delays, volMultipliers);
	}

	static void multiDelayPhrase(Phrase phr, List<Double> delays, List<Double> volMultipliers) {
		if (delays.isEmpty() || (delays.size() != volMultipliers.size())) {
			return;
		}

		List<Note> notes = phr.getNoteList();
		int size = notes.size();
		int currIndex = 0;
		for (int i = 0; i < size; i++) {
			Note n = notes.get(currIndex);
			for (int j = 0; j < delays.size(); j++) {
				Double delay = delays.get(j);
				Double volMult = volMultipliers.get(j);
				Note nd = new Note(n.getPitch(), 0, OMNI.clampVel(n.getDynamic() * volMult));
				nd.setDuration(n.getDuration());
				nd.setOffset(n.getOffset() + delay);
				notes.add(currIndex, nd);
			}
			currIndex += 1 + delays.size();
		}
	}

}
