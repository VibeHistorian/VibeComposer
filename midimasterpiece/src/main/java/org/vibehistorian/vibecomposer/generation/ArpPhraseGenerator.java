package org.vibehistorian.vibecomposer.generation;

import jm.constants.Pitches;
import jm.music.data.Note;
import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.Constants;
import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.Helpers.PhraseExt;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Section;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Renders an arpeggio phrase from chord progression, pattern, and section settings. */
final class ArpPhraseGenerator extends InstPhraseGenerator<ArpPart> {
    private static final String ARP_PATTERN_KEY = "ARP_PATTERN";
    private static final String ARP_OCTAVE_KEY = "ARP_OCTAVE";
    private static final String ARP_PAUSES_KEY = "ARP_PAUSES";

    static final class ArpResult extends Result {
        final boolean fillLastBeat;
        final int minVelocity;
        final int maxVelocity;
        final ArpPart originalPartState;

        private ArpResult(Phrase phrase, List<Integer> variations, boolean storeVariations,
                          boolean fillLastBeat, int minVelocity, int maxVelocity,
                          ArpPart originalPartState) {
            super(phrase, variations, storeVariations);
            this.fillLastBeat = fillLastBeat;
            this.minVelocity = minVelocity;
            this.maxVelocity = maxVelocity;
            this.originalPartState = originalPartState;
        }
    }

    ArpPhraseGenerator(GUIConfig gc, VariationGenerator variationGenerator) {
        super(gc, variationGenerator);
    }

    ArpResult generate(ArpPart ip, List<int[]> actualProgression,
                       List<int[]> generatedRootProgression, List<Double> progressionDurations,
                       List<Integer> baseMelodyPattern,
                       Map<Integer, List<Integer>> melodyPatternMap,
                       int chordCount, Section sec, List<Integer> variations, int sectionOrder,
                       Timing timing) {
		boolean genVars = variations == null;

		int measures = sec.getMeasures();

		Phrase phr = new PhraseExt(3, ip.getOrder(), sectionOrder);

		ArpPart apClone = (ArpPart) ip.clone();
		int seed = ip.getPatternSeedWithPartOffset() + ip.getOrderOffset();
		Map<String, List<Integer>> arpMap = generateArpMap(seed, ip, baseMelodyPattern, chordCount);

		List<Integer> arpPattern = arpMap.get(ARP_PATTERN_KEY);
		List<Integer> arpOctavePattern = arpMap.get(ARP_OCTAVE_KEY);
		List<Integer> arpPausesPattern = arpMap.get(ARP_PAUSES_KEY);

		List<Integer> arpVelocityPattern = new ArrayList<>();
		if (ip.getCustomVelocities() != null
				&& ip.getCustomVelocities().size() >= ip.getHitsPerPattern()) {
			int multiplier = gc.isScaleMidiVelocityInArrangement() ? sec.getVol(3) : 100;
			for (int k = 0; k < ip.getHitsPerPattern(); k++) {
				arpVelocityPattern.add(MidiGeneratorUtils
						.multiplyVelocity(ip.getCustomVelocities().get(k), multiplier, 0, 1));
			}
			arpVelocityPattern = MidiUtils.intersperse(null, ip.getChordSpan() - 1,
					arpVelocityPattern);
		}

		List<Boolean> directions = null;


		// TODO: divide
		int repeatedArpsPerChord = ip.getHitsPerPattern() * ip.getPatternRepeat();

		/*if (melodic) {
			repeatedArpsPerChord /= ap.getChordSpan();
		}*/

		int volMultiplier = (gc.isScaleMidiVelocityInArrangement()) ? sec.getVol(3) : 100;
		int minVel = MidiGeneratorUtils.multiplyVelocity(ip.getVelocityMin(), volMultiplier, 0, 1);
		int maxVel = MidiGeneratorUtils.multiplyVelocity(ip.getVelocityMax(), volMultiplier, 1, 0);

		boolean fillLastBeat = false;
		List<Integer> fillPattern = ip.getChordSpanFill()
				.getPatternByLength(actualProgression.size(), ip.isFillFlip());
		for (int i = 0; i < measures; i++) {
			int chordSpanPart = 0;
			int spannedPulseCounter = 0;
			int extraTranspose = 0;
			boolean ignoreChordSpanFill = false;
			boolean forceRandomOct = false;

			Random velocityGenerator = new Random(seed);
			Random exceptionGenerator = new Random(seed + 1);
			for (int chordIndex = 0; chordIndex < actualProgression.size(); chordIndex++) {
				if (genVars && (chordIndex == 0)) {
					List<Double> chanceMultipliers = sec.isTransition()
							? Arrays.asList(new Double[] { 1.0, 1.0, 1.0, 2.0, 1.0 })
							: null;
					variations = fillVariations(sec, ip, variations, 3, chanceMultipliers);
				}

				double halfDurMulti = (chordIndex >= (actualProgression.size() + 1) / 2
						&& sec.getTransitionType() == 4) ? 2.0 : 1.0;

				if ((variations != null) && (chordIndex == 0)) {
					for (Integer var : variations) {

						switch (var) {
						case 0:
							//extraTranspose = 12;
							break;
						case 1:
							ignoreChordSpanFill = true;
							break;
						case 2:
							forceRandomOct = true;
							break;
						case 3:
							fillLastBeat = true;
							break;
						case 4:
							if (directions == null) {
								directions = MidiGeneratorUtils
										.generateMelodyDirectionsFromChordProgression(
												actualProgression, true);
							}
							break;
						default:
							throw new IllegalArgumentException("Too much variation!");
						}
					}
				}

				double chordDurationArp = (ip.getPattern() == RhythmPattern.MELODY1
						&& melodyPatternMap != null) ? timing.sixteenthNote
								: timing.wholeNote / ((double) repeatedArpsPerChord);
				int[] chord = MidiUtils.convertChordToLength(actualProgression.get(chordIndex),
						ip.getChordNotesStretch(), ip.isStretchEnabled());
				/*List<Integer> chordNotes = Arrays.stream(chord).boxed().map(e -> e % 12)
						.collect(Collectors.toList());*/

				if (directions != null) {
					ArpPattern pat = (directions.get(chordIndex)) ? ArpPattern.UP : ArpPattern.DOWN;
					arpPattern = pat.getPatternByLength(ip.getHitsPerPattern(), chord.length,
							ip.getPatternRepeat(), ip.getArpPatternRotate());
					arpPattern = MidiUtils.intersperse(0, ip.getChordSpan() - 1, arpPattern);
				} else {
					if (ip.getArpPattern() != ArpPattern.RANDOM) {
						if (ip.getArpPattern() == ArpPattern.CUSTOM) {
							arpPattern = ip.getArpPattern().getPatternByLength(
									ip.getHitsPerPattern(), chord.length, ip.getPatternRepeat(),
									ip.getArpPatternRotate(), ip.getArpPatternCustom());
						} else {
							arpPattern = ip.getArpPattern().getPatternByLength(
									ip.getHitsPerPattern(), chord.length, ip.getPatternRepeat(),
									ip.getArpPatternRotate());
						}

						arpPattern = MidiUtils.intersperse(0, ip.getChordSpan() - 1, arpPattern);
					} else {
						ip.setArpPatternCustom(arpPattern);
					}
				}

				int actualPatternSize = (int) Math.round(arpPattern.size()
						* progressionDurations.get(chordIndex) / timing.wholeNote);
				/*if (arpPattern.size() > 0) {
					actualPatternSize = Math.max(1, actualPatternSize);
				}*/

				chordDurationArp *= halfDurMulti;

				// reset every 2
				//if (chordIndex % 2 == 0) {
					//exceptionGenerator.setSeed(seed + 1);
				//}
				// sublistIfPossible - fix for shorter patterns due to chord duration splitting unevenly

				List<Integer> pitchPatternSpanned = PhrasePatternUtils.partOfListClean(chordSpanPart,
						ip.getChordSpan(),
						MidiUtils.sublistIfPossible(arpPattern, actualPatternSize));
				List<Integer> octavePatternSpanned = PhrasePatternUtils.partOfListClean(chordSpanPart,
						ip.getChordSpan(),
						MidiUtils.sublistIfPossible(arpOctavePattern, actualPatternSize));
				List<Integer> melodyPattern = (melodyPatternMap != null)
						? melodyPatternMap.get(chordIndex)
						: null;
				List<Integer> pausePatternSpanned = (ip.getPattern() == RhythmPattern.MELODY1
						&& melodyPattern != null) ? new ArrayList<>(melodyPattern)
								: PhrasePatternUtils.partOfListClean(chordSpanPart, ip.getChordSpan(), MidiUtils
										.sublistIfPossible(arpPausesPattern, actualPatternSize));
				List<Integer> velocityPatternSpanned = !arpVelocityPattern.isEmpty()
						? PhrasePatternUtils.partOfListClean(chordSpanPart, ip.getChordSpan(), arpVelocityPattern)
						: null;
				List<Integer> contour = ip.getArpContour();
				List<Integer> arpContourSpanned = (contour != null && !contour.isEmpty()) ? contour
						: null;
				Integer contourInterval = (arpContourSpanned != null)
						? Math.max(repeatedArpsPerChord / arpContourSpanned.size(), 1)
						: null;

				double melodySubdivisions = -1;
				if (melodyPattern != null && !melodyPattern.isEmpty()) {
					melodySubdivisions = progressionDurations.get(chordIndex)
							/ melodyPattern.size();
				}
				int lastMelodyIndex = 0;

				int pulseListSize = Math.min(repeatedArpsPerChord, pitchPatternSpanned.size());
				int pulse = 0;
				double durationNow = 0;
				while (!pitchPatternSpanned.isEmpty() && (durationNow + Constants.DBL_ERR < progressionDurations.get(chordIndex))) {
					int velocity = velocityPatternSpanned != null
							? velocityPatternSpanned.get(pulse % velocityPatternSpanned.size())
							: (velocityGenerator.nextInt(maxVel - minVel) + minVel);

					Integer noteInChord = pitchPatternSpanned.get(pulse);

					int pitch = MidiUtils.getXthChordNote(noteInChord, chord);
					if ((contourInterval != null) && (spannedPulseCounter % contourInterval == 0)) {
						int newPitch = 24 + MidiUtils.getXthChordNote(arpContourSpanned.get(
								(spannedPulseCounter / contourInterval) % arpContourSpanned.size()),
								MidiUtils.cChromatic);
						if (ip.isArpContourChordMode()) {
							newPitch += generatedRootProgression.get(chordIndex)[0] % 12;
							newPitch = MidiUtils.octavePitch(newPitch) + MidiUtils
									.getClosestPitchFromList(MidiUtils.MAJ_SCALE, newPitch);
						}
						pitch = newPitch;
						/*LG.i("Replaced with ARP contour pitch: " + pitch + ", at pulse: " + pulse
								+ ", spanned: " + spannedPulseCounter + ", #: "
								+ spannedPulseCounter / contourInterval);*/
					}

					if (gc.isUseOctaveAdjustments() || forceRandomOct) {
						int octaveAdjustGenerated = octavePatternSpanned.get(pulse);
						int octaveAdjustmentFromPattern = (noteInChord < 2) ? -12
								: ((noteInChord < 6) ? 0 : 12);
						pitch += octaveAdjustmentFromPattern + octaveAdjustGenerated;
					}

					if (gc.isRandomArpCorrectMelodyNotes()) {
						Integer melodyPitch = null;
						if (melodySubdivisions > 0) {
							for (int mInd = lastMelodyIndex; mInd < melodyPattern.size(); mInd++) {
								melodyPitch = melodyPattern.get(mInd);
								if (melodyPitch != null) {
									break;
								}
								if (mInd * melodySubdivisions > durationNow + chordDurationArp) {
									break;
								}
							}
							lastMelodyIndex = (int) Math
									.round((durationNow + chordDurationArp) / melodySubdivisions);
						}

						if (melodyPitch != null) {

							LG.i("Last MelodyIndex: " + lastMelodyIndex + ", pitch: "
									+ melodyPitch);
							if (MidiUtils.getSemitonalDistance(melodyPitch, pitch) == 1) {
								LG.i("Old pitch: " + pitch);
								pitch = MidiUtils.octavePitch(pitch) + (melodyPitch % 12)
										+ ((pitch % 12 >= 6) && (melodyPitch % 12) < 6 ? 12 : 0);
								LG.i("new pitch: " + pitch);
							}
						}
					}

					boolean isPause = pausePatternSpanned
							.get(pulse % pausePatternSpanned.size()) == 0;
					if (ip.isPatternFlip()) {
						isPause = !isPause;
					}

					pitch += extraTranspose;
					if (!fillLastBeat || chordIndex < actualProgression.size() - 1) {
						if (isPause) {
							pitch = Pitches.REST;
						} else if (!ignoreChordSpanFill) {
							if (fillPattern.get(chordIndex) < 1) {
								pitch = Pitches.REST;
							}
						}
					}
					double usedDuration = chordDurationArp;
					if (durationNow + usedDuration - Constants.DBL_ERR > progressionDurations
							.get(chordIndex)) {
						usedDuration = progressionDurations.get(chordIndex) - durationNow;
						if (usedDuration < timing.fillerNoteMinDuration) {
							pitch = Pitches.REST;
						}
					}
					double durMultiplier = timing.globalDurationMultiplier * ip.getChordSpan();
					if (exceptionGenerator.nextInt(100) < ip.getExceptionChance() && pitch >= 0) {
						double splitDuration = usedDuration / 2;
						int patternNum2 = pitchPatternSpanned.get((pulse + 1) % pulseListSize);
						int pitch2 = MidiUtils.getXthChordNote(patternNum2, chord) + extraTranspose;
						if (pitch2 >= 0) {
							pitch2 = MidiUtils.transposeNote((pitch + pitch2) / 2,
									ScaleMode.IONIAN.noteAdjustScale,
									ScaleMode.IONIAN.noteAdjustScale);
						} else {
							pitch2 = pitch;
						}
						//LG.d("Splitting arp!"); 
						Note n1 = new Note(pitch, splitDuration, velocity);
						n1.setDuration(splitDuration * durMultiplier);
						phr.addNote(n1);
						Note n2 = new Note(pitch2, splitDuration, Math.max(0, velocity - 15));
						n2.setDuration(splitDuration * durMultiplier);
						phr.addNote(n2);
					} else {
						Note n1 = new Note(pitch, usedDuration, velocity);
						n1.setDuration(usedDuration * durMultiplier);
						phr.addNote(n1);
					}
					durationNow += usedDuration;
					pulse = (pulse + 1) % pulseListSize;
					spannedPulseCounter++;
				}

				chordSpanPart++;
				if (chordSpanPart >= ip.getChordSpan()) {
					chordSpanPart = 0;
					spannedPulseCounter = 0;
				}
			}
		}

        return new ArpResult(phr, variations, genVars && variations != null, fillLastBeat,
                minVel, maxVel, apClone);
    }
	private void processPausePattern(ArpPart ap, List<Integer> arpPausesPattern,
			Random pauseGenerator) {
		for (int i = 0; i < ap.getHitsPerPattern(); i++) {
			if (pauseGenerator.nextInt(100) < ap.getPauseChance()) {
				arpPausesPattern.set(i, 0);
			}
		}
	}

	private Map<String, List<Integer>> generateArpMap(int mainGeneratorSeed,
			ArpPart ap, List<Integer> baseMelodyPattern, int chordCount) {
		Random uiGenerator2arpPattern = new Random(mainGeneratorSeed + 1);
		Random uiGenerator3arpOctave = new Random(mainGeneratorSeed + 2);
		Random uiGenerator4arpPauses = new Random(mainGeneratorSeed + 3);

		List<Integer> arpPausesPattern = new ArrayList<>();
		if (ap.getPattern() == RhythmPattern.FULL) {
			for (int i = 0; i < ap.getHitsPerPattern(); i++) {
				arpPausesPattern.add(1);
			}
			Collections.rotate(arpPausesPattern, ap.getPatternShift());
		} else if (ap.getPattern() == RhythmPattern.MELODY1 && baseMelodyPattern != null) {
			// TODO: already set from melodyPatternMap in later processing, remove?
			//LG.d("Setting note pattern!");
			arpPausesPattern = baseMelodyPattern;
			ap.setPatternShift(0);
			//dp.setVelocityPattern(false);
			ap.setChordSpan(chordCount);
			ap.setHitsPerPattern(baseMelodyPattern.size());
			ap.setPatternRepeat(1);
		} else {
			arpPausesPattern = ap.getFinalPatternCopy();
			arpPausesPattern = arpPausesPattern.subList(0, ap.getHitsPerPattern());
		}

		processPausePattern(ap, arpPausesPattern, uiGenerator4arpPauses);

		int[] arpOctaveArray = IntStream.iterate(0, e -> (e + 12) % 24)
				.limit(ap.getHitsPerPattern() * 2).toArray();


		List<Integer> arpOctavePattern = Arrays.stream(arpOctaveArray).boxed()
				.collect(Collectors.toList());

		// TODO: note pattern, different from rhythm pattern
		//if (ap.getPattern() == RhythmPattern.RANDOM) {
		Collections.shuffle(arpOctavePattern, uiGenerator3arpOctave);
		//}
		// always generate ap.getHitsPerPattern(), 
		// cut off however many are needed (support for seed randoms)
		if (!(ap.getPattern() == RhythmPattern.MELODY1 && baseMelodyPattern != null)) {
			arpPausesPattern = arpPausesPattern.subList(0, ap.getHitsPerPattern());
		}

		List<Integer> arpPattern = (ap.getArpPattern() != ArpPattern.RANDOM) ? new ArrayList<>()
				: MidiGeneratorUtils.makeRandomArpPattern(ap.getHitsPerPattern(), true, uiGenerator2arpPattern);
		arpOctavePattern = arpOctavePattern.subList(0, ap.getHitsPerPattern());

		Collections.rotate(arpPattern, -1 * ap.getArpPatternRotate());

		if (ap.getChordSpan() > 1) {
			if (!(ap.getPattern() == RhythmPattern.MELODY1 && baseMelodyPattern != null)) {
				arpPausesPattern = MidiUtils.intersperse(0, ap.getChordSpan() - 1,
						arpPausesPattern);
			}
			arpPattern = MidiUtils.intersperse(0, ap.getChordSpan() - 1, arpPattern);
			arpOctavePattern = MidiUtils.intersperse(0, ap.getChordSpan() - 1, arpOctavePattern);
		}

		// pattern repeat

		List<Integer> repArpPattern = new ArrayList<>();
		List<Integer> repOctPattern = new ArrayList<>();
		List<Integer> repPausePattern = new ArrayList<>();
		for (int i = 0; i < ap.getPatternRepeat(); i++) {
			repArpPattern.addAll(arpPattern);
			repOctPattern.addAll(arpOctavePattern);
			repPausePattern.addAll(arpPausesPattern);
		}


		Map<String, List<Integer>> arpMap = new HashMap<>();
		arpMap.put(ARP_PATTERN_KEY, repArpPattern);
		arpMap.put(ARP_OCTAVE_KEY, repOctPattern);
		arpMap.put(ARP_PAUSES_KEY, repPausePattern);


		return arpMap;
	}

}
