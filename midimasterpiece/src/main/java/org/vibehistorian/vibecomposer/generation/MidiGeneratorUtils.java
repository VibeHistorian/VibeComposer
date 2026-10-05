package org.vibehistorian.vibecomposer.generation;

import jm.constants.Pitches;
import jm.music.data.Note;
import jm.music.data.Phrase;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.Constants;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.JMusicUtilsCustom;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Section;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class MidiGeneratorUtils {

	static List<Integer> makeRandomArpPattern(int hits, boolean repeatableNotes,
			Random uiGenerator2arpPattern) {
		int[] arpPatternArray = IntStream.iterate(0, e -> (e + 1) % MidiGenerator.MAXIMUM_PATTERN_LENGTH)
				.limit(hits * 2).toArray();
		List<Integer> arpPattern = Arrays.stream(arpPatternArray).boxed()
				.collect(Collectors.toList());
		if (repeatableNotes) {
			arpPattern.addAll(arpPattern);
		}
		arpPattern = arpPattern.subList(0, hits);
		Collections.shuffle(arpPattern, uiGenerator2arpPattern);
		return arpPattern;
	}

	static List<Integer> generateDrumPatternFromPart(DrumPart dp, List<Integer> melodyNotePattern,
			int chordCount) {
		Random uiGenerator1drumPattern = new Random(
				dp.getPatternSeedWithPartOffset() + dp.getOrderOffset() - 1);
		List<Integer> premadePattern;
		if (melodyNotePattern != null && dp.getPattern() == RhythmPattern.MELODY1) {
			dp.setHitsPerPattern(melodyNotePattern.size());
			premadePattern = melodyNotePattern;
			dp.setPatternShift(0);
			dp.setChordSpan(chordCount);
		} else {
			premadePattern = dp.getFinalPatternCopy();
		}

		List<Integer> drumPattern = new ArrayList<>();
		for (int j = 0; j < dp.getHitsPerPattern(); j++) {
			boolean blankDrum = uiGenerator1drumPattern.nextInt(100) < dp.getPauseChance()
					|| premadePattern.get(j) < 1;
			if (dp.isPatternFlip()) {
				blankDrum = !blankDrum;
			}
			if (blankDrum) {
				drumPattern.add(-1);
			} else if (dp.getInstrument() == 42
					&& uiGenerator1drumPattern.nextInt(100) < MidiGenerator.OPENHAT_CHANCE) {
				drumPattern.add(46);
			} else {
				drumPattern.add(dp.getInstrument());
			}
		}
		return drumPattern;
	}

	public static int adjustChanceParamForTransition(int param, Section sec, int chordNum,
                                                     int chordSize, int maxEffect, double affectedMeasure, boolean reverseEffect, boolean clampChance) {
		if (chordSize < 2 || !sec.isTransition()) {
			return param;
		}

		int minAffectedChord = OMNI.clamp((int) (affectedMeasure * chordSize) - 1, 1,
				chordSize - 1);
		if (chordNum < minAffectedChord) {
			return param;
		}

		int chordRange = chordSize - 1 - minAffectedChord;
		double effect = (chordRange > 0) ? ((chordNum - minAffectedChord) / ((double) chordRange))
				: 1.0;

		int transitionType = sec.getTransitionType();

		int multiplier = reverseEffect ? -1 : 1;
		if (transitionType == 1) {
			param += (int) (maxEffect * effect * multiplier);
		} else {
			param -= (int) (maxEffect * effect * multiplier);
		}
		if (clampChance) {
			param = OMNI.clampChance(param);
		}
		return param;
	}

	public static List<Integer> generateNoteTargetOffsets(List<String> chordStrings, int randomSeed,
			int targetMode, int targetNoteVariation, Boolean isPublic,
			MelodyUtils.NoteTargetDirection direction, boolean useDirectionsFromProgression) {
		return MelodyTargetUtils.generateNoteTargetOffsets(chordStrings, randomSeed, targetMode,
				targetNoteVariation, isPublic, direction, useDirectionsFromProgression);
	}

	static boolean isDottedNote(double note, MidiTiming timing) {
		return MidiUtils.roughlyEqual(timing.dottedQuarterNote, note)
				|| MidiUtils.roughlyEqual(timing.dottedWholeNote, note)
				|| MidiUtils.roughlyEqual(timing.dottedHalfNote, note)
				|| MidiUtils.roughlyEqual(timing.dottedEighthNote, note)
				|| MidiUtils.roughlyEqual(timing.dottedSixteenthNote, note);
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
			startingNote = MidiUtils.MAJ_SCALE.indexOf(MidiUtils.getClosestPitchFromList(MidiUtils.MAJ_SCALE, startingPitch % 12));
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

	public static void applySamePitchCollisionAvoidance(List<Note> notes) {
		List<Pair<Double, Note>> sn = JMusicUtilsCustom.makeNoteStartTimes(notes);
		for (int i = 0; i < sn.size(); i++) {
			Note n = sn.get(i).getRight();
			double duration = n.getDuration();
			if (i < sn.size() - 1 && n.getPitch() == sn.get(i + 1).getRight().getPitch()) {
				double difference = sn.get(i + 1).getLeft() - sn.get(i).getLeft();
				duration = Math.min(duration, difference);
			} else if (i < sn.size() - 2
					&& n.getPitch() == sn.get(i + 2).getRight().getPitch()) {
				double difference = sn.get(i + 2).getLeft() - sn.get(i).getLeft();
				duration = Math.min(duration, difference);
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
					n.setPitch(Pitches.REST);
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
			previousPitch = Pitches.REST;
		}
		for (int i = 1; i < fullMelody.size(); i++) {
			Note n = fullMelody.get(i);
			int pitch = n.getPitch();
			if (pitch < 0 || previousPitch == Pitches.REST) {
				previousPitch = pitch;
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

	static void replaceNearChordNotes(Map<Integer, List<Note>> fullMelodyMap, List<int[]> chords,
									  int randomSeed, int notesToAvoid, MidiTiming timing) {
		Random rand = new Random(randomSeed);
		for (int i = 0; i < fullMelodyMap.keySet().size(); i++) {
			Set<Integer> avoidNotes = MidiUtils.getNearNotesFromChord(chords.get(i % chords.size()),
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
				if (avoidAllLengths || (n.getRhythmValue() > timing.eighthNote
						- Constants.DBL_ERR)) {
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
			List<String> spicyChordList, boolean forceScale) {
		List<String> spicyChordListCopy = new ArrayList<>(spicyChordList);
		String firstLetter = chordString.substring(0, 1);
		List<Integer> targetScale = Arrays.asList(ScaleMode.IONIAN.noteAdjustScale);
		int transposeByLetter = targetScale.get(MidiUtils.CHORD_FIRST_LETTERS.indexOf(firstLetter));
		if (forceScale) {
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

	public static List<Double> generateRhythmOffsets(int size, double offsetVariation, long randomSeed) {
		if (size < 1) {
			return new ArrayList<>();
		} else if (size == 1) {
			return Collections.singletonList(0.0);
		}

		// Initialize random number generator with the seed
		Random random = new Random(randomSeed);

		// List to store the offsets
		List<Double> offsets = new ArrayList<>();

		// Generate size - 1 random offsets between -offsetVariation and +offsetVariation
		double sum = 0;
		for (int i = 0; i < size - 1; i++) {
			double offset = -offsetVariation + (2 * offsetVariation) * random.nextDouble();
			offsets.add(offset);
			sum += offset;
		}

		// Try to correct the sum by using the last element
		double lastOffset = -sum;

		// If last offset exceeds bounds, adjust several elements iteratively
		if (Math.abs(sum) > offsetVariation) {
			// Randomly adjust values that are not already at the boundary
			double excess = sum;
			//LG.i("Excess before: " + excess);
			for (int i = 0; i < size - 1 && Math.abs(excess) > 0.00001; i++) {
				double current = offsets.get(i);
				double adjustment = (random.nextDouble() - 0.5) * 2 * Math.min(Math.abs(excess), offsetVariation - Math.abs(current));

				// Apply adjustment only if it reduces excess
				if ((excess < 0 && adjustment < 0) || (excess > 0 && adjustment > 0)) {
					adjustment *= -1;
				}
				offsets.set(i, current + adjustment);
				excess += adjustment;
			}
			// Now, set the last value to the remaining excess
			lastOffset = -excess;
		}

		// Make sure the last value is within bounds
		if (lastOffset < -offsetVariation) lastOffset = -offsetVariation;
		if (lastOffset > offsetVariation) lastOffset = offsetVariation;

		// Add the final corrected value
		offsets.add(lastOffset);

		// Shuffle the offsets list to randomize order
		Collections.shuffle(offsets, random);

		return offsets;
	}

}
