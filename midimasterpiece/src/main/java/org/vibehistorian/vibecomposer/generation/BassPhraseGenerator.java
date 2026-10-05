package org.vibehistorian.vibecomposer.generation;

import jm.constants.Pitches;
import jm.music.data.Note;
import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.Constants;
import org.vibehistorian.vibecomposer.Enums.PatternJoinMode;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.Helpers.PhraseExt;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Parts.BassPart;
import org.vibehistorian.vibecomposer.Section;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Renders a bass part from its chord-root progression and rhythm pattern. */
final class BassPhraseGenerator extends InstPhraseGenerator<BassPart> {
    BassPhraseGenerator(GUIConfig gc, VariationGenerator variationGenerator) {
        super(gc, variationGenerator);
    }

    Result generate(BassPart ip, List<int[]> generatedRootProgression,
					List<Double> progressionDurations, Map<Integer, List<Integer>> melodyNotePatternMap,
					Section sec, List<Integer> variations, int sectionOrder, MidiTiming timing,
					double globalDurationMultiplier) {
		boolean genVars = variations == null;

		int measures = sec.getMeasures();

		double[] durationPool = new double[] { timing.sixteenthNote, timing.eighthNote,
				timing.quarterNote, timing.dottedQuarterNote, timing.halfNote,
				timing.eighthNote + timing.halfNote, timing.dottedHalfNote,
				timing.wholeNote };

		int[] durationWeights = new int[] { 5, 25, 45, 55, 75, 85, 95, 100 };

		int seed = ip.getPatternSeedWithPartOffset();

		Phrase phr = new PhraseExt(1, ip.getOrder(), sectionOrder);
		int volMultiplier = (gc.isScaleMidiVelocityInArrangement()) ? sec.getVol(1) : 100;
		int minVel = MidiGeneratorUtils.multiplyVelocity(ip.getVelocityMin(), volMultiplier, 0, 1);
		int maxVel = MidiGeneratorUtils.multiplyVelocity(ip.getVelocityMax(), volMultiplier, 1, 0);
		Random rhythmPauseGenerator = new Random(seed + sec.getTypeMelodyOffset());
		Random noteVariationGenerator = new Random(seed + sec.getTypeMelodyOffset() + 2);

		double rootAverage = 0;
        for (int[] ints : generatedRootProgression) {
            rootAverage += ints[0];
        }
		rootAverage /= generatedRootProgression.size();

		List<int[]> squishedChords = new ArrayList<>();
		for (int i = 0; i < generatedRootProgression.size(); i++) {
			double dist = generatedRootProgression.get(i)[0] - rootAverage;
			if (Math.abs(dist) < 5 - Constants.DBL_ERR) {
				squishedChords.add(generatedRootProgression.get(i));
			} else {
				int adjustment = dist > 0 ? -12 : 12;
				squishedChords
						.add(MidiUtils.transposeChord(generatedRootProgression.get(i), adjustment));
				rootAverage += (adjustment / (double) generatedRootProgression.size());

			}
		}


		List<Integer> bassVelocityPattern = new ArrayList<>();
		if (ip.getCustomVelocities() != null
				&& ip.getCustomVelocities().size() >= ip.getHitsPerPattern()) {
			int multiplier = gc.isScaleMidiVelocityInArrangement() ? sec.getVol(3) : 100;
			for (int k = 0; k < ip.getHitsPerPattern(); k++) {
				bassVelocityPattern.add(MidiGeneratorUtils
						.multiplyVelocity(ip.getCustomVelocities().get(k), multiplier, 0, 1));
			}
			bassVelocityPattern = MidiUtils.intersperse(null, ip.getChordSpan() - 1,
					bassVelocityPattern);
		}


		Random bassDynamics = new Random(ip.getPatternSeedWithPartOffset());
		boolean rhythmPauses = false;
		List<Integer> fillPattern = ip.getChordSpanFill()
				.getPatternByLength(progressionDurations.size(), ip.isFillFlip());
		//LG.d("Bass fill pattern:" + StringUtils.join(fillPattern, ", "));
		for (int i = 0; i < measures; i++) {
			int extraSeed = 0;
			int chordSpanPart = 0;
			int skipNotes = 0;

			bassDynamics.setSeed(ip.getPatternSeedWithPartOffset());
			for (int chordIndex = 0; chordIndex < squishedChords.size(); chordIndex++) {
				if (genVars && (chordIndex == 0) && sec.getTypeMelodyOffset() > 0) {
					variations = fillVariations(sec, ip, variations, 1);
				}
				double halfDurMulti = (chordIndex >= (squishedChords.size() + 1) / 2
						&& sec.getTransitionType() == 4) ? 2.0 : 1.0;
				if ((variations != null) && (chordIndex == 0)) {
					for (Integer var : variations) {
						if (i == measures - 1) {
							LG.d("Bass #1 variation: " + var);
						}

						switch (var) {
						case 0:
							extraSeed = 100;
							break;
						case 1:
							rhythmPauses = true;
							break;
						default:
							throw new IllegalArgumentException("Too much variation!");
						}
					}
				}


				if (fillPattern.get(chordIndex) < 1) {
					skipNotes = 0;
					chordSpanPart = (chordSpanPart + 1) % ip.getChordSpan();
					phr.addNote(new Note(Pitches.REST, progressionDurations.get(chordIndex)));
					continue;
				}
				int velSpace = maxVel - minVel;

				if (ip.isAlternatingRhythm()) {
					int counter = 0;
					int seedCopy = seed + extraSeed + (chordIndex % 2);
					Rhythm bassRhythm = new Rhythm(seedCopy, progressionDurations.get(chordIndex),
							durationPool, durationWeights);
					List<Double> durations = bassRhythm.regenerateDurations(4,
										timing.sixteenthNote / 2.0);

					for (Double dur : durations) {

						int randomNote = 0;
						// note variation for short notes, low chance, only after first
						int noteVaryChance = sec.isTransition()
							? PhraseEffectUtils.adjustChanceParamForTransition(
										ip.getNoteVariation(), sec, chordIndex,
										squishedChords.size(), 40, 0.25, false, true)
								: ip.getNoteVariation();
						if (counter > 0 && dur < (timing.quarterNote + Constants.DBL_ERR)
								&& noteVariationGenerator.nextInt(100) < noteVaryChance
								&& squishedChords.get(chordIndex).length > 1) {
							randomNote = noteVariationGenerator
									.nextInt(squishedChords.get(chordIndex).length - 1) + 1;
						}

						int pitch = (rhythmPauses && dur < timing.quarterNote
								&& rhythmPauseGenerator.nextInt(100) < 33) ? Pitches.REST
										: squishedChords.get(chordIndex)[randomNote];

						int velocity = bassDynamics.nextInt(velSpace) + minVel;
						Note n = new Note(pitch, dur, velocity);
						n.setDuration(dur * globalDurationMultiplier);
						phr.addNote(n);
						counter++;
					}
				} else {
					List<Integer> pattern = null;
					List<Integer> nextPattern = null;
					List<Integer> velocityPattern = null;
					PatternJoinMode joinMode = ip.getPatternJoinMode();
					int stretchedByNote = (joinMode == PatternJoinMode.JOIN) ? 1 : 0;
					if (ip.getPattern() == RhythmPattern.MELODY1 && melodyNotePatternMap != null) {
						pattern = new ArrayList<>(melodyNotePatternMap.get(chordIndex));
					} else {
						List<Integer> patternCopy = ip.getFinalPatternCopy();
						List<Integer> patternSub = patternCopy.subList(0, ip.getHitsPerPattern());

						pattern = MidiUtils.intersperse(-1, ip.getChordSpan() - 1, patternSub);
						pattern = PhrasePatternUtils.partOfListClean(chordSpanPart, ip.getChordSpan(), pattern);
						if (ip.getChordSpan() > 1 && joinMode != PatternJoinMode.NOJOIN) {
							if (chordSpanPart < ip.getChordSpan() - 1) {
								nextPattern = MidiUtils.intersperse(-1, ip.getChordSpan() - 1,
										patternSub);
								nextPattern = PhrasePatternUtils.partOfListClean(chordSpanPart + 1, ip.getChordSpan(),
										nextPattern);
							}
						}
						velocityPattern = !bassVelocityPattern.isEmpty()
								? PhrasePatternUtils.partOfList(chordSpanPart, ip.getChordSpan(), bassVelocityPattern)
								: null;
					}

					if (ip.isPatternFlip()) {
						for (int p = 0; p < pattern.size(); p++) {
							if (pattern.get(p) >= 0) {
								pattern.set(p, pattern.get(p) > 0 ? 0 : 1);
							}
						}
					}

					double duration = (ip.getPattern() == RhythmPattern.MELODY1
							&& melodyNotePatternMap != null) ? timing.sixteenthNote
									: timing.wholeNote / pattern.size();
					duration *= halfDurMulti;

					double durationNow = 0;
					int nextP = -1;

					int p = 0;
					while (durationNow + Constants.DBL_ERR < progressionDurations.get(chordIndex)) {
						int velocity = velocityPattern != null
								? velocityPattern.get(p % velocityPattern.size())
								: (bassDynamics.nextInt(velSpace) + minVel);
						int pitch = 0;
						double finalDuration = 0.0;
						if (pattern.get(p) < 1 || (p <= nextP && stretchedByNote == 1)
								|| skipNotes > 0) {
							if (skipNotes > 0) {
								skipNotes--;
							}
							pitch = Pitches.REST;
						}

						if (durationNow + duration > progressionDurations.get(chordIndex)
								- Constants.DBL_ERR) {
							double fillerDuration = progressionDurations.get(chordIndex)
									- durationNow;
							finalDuration = fillerDuration;
							duration = fillerDuration;
							if (fillerDuration < FILLER_NOTE_MIN_DURATION) {
								pitch = Pitches.REST;
							}
						} else {
							finalDuration = duration;
						}

						int durMultiplier = 1;
						boolean joinApplicable = joinMode != PatternJoinMode.NOJOIN
								&& (pattern.get(p) > 0) && (p >= nextP);
						if (joinApplicable) {
							nextP = p + 1;
							while (nextP < pattern.size()) {
								if (durationNow + duration * durMultiplier > progressionDurations
										.get(chordIndex)) {
									break;
								}

								if (Integer.signum(pattern.get(nextP)) == stretchedByNote
										|| pattern.get(nextP) == -1) {
									durMultiplier++;
									nextP++;
								} else {
									break;
								}
							}
						}

						if (nextP >= pattern.size() && ip.getChordSpan() > 1
								&& nextPattern != null) {
							skipNotes = PhrasePatternUtils.countStartingValueInList(stretchedByNote, nextPattern);
							durMultiplier += skipNotes;

						}
						//LG.d("Dur multiplier added: " + durMultiplier);
						finalDuration = duration * durMultiplier;

						if (pitch == 0) {
							int randomNote = 0;
							// note variation for short notes, low chance, only after first
							int noteVaryChance = sec.isTransition()
									? PhraseEffectUtils.adjustChanceParamForTransition(
											ip.getNoteVariation(), sec, chordIndex,
											squishedChords.size(), 40, 0.25, false, true)
									: ip.getNoteVariation();
							if (p > 0 && finalDuration < (timing.quarterNote + Constants.DBL_ERR)
									&& noteVariationGenerator.nextInt(100) < noteVaryChance
									&& squishedChords.get(chordIndex).length > 1) {
								randomNote = noteVariationGenerator
										.nextInt(squishedChords.get(chordIndex).length - 1) + 1;
							}
							pitch = (rhythmPauses && finalDuration < timing.quarterNote
									&& rhythmPauseGenerator.nextInt(100) < 33) ? Pitches.REST
											: squishedChords.get(chordIndex)[randomNote];
						}
						Note n = new Note(pitch, duration, velocity);
						n.setDuration(finalDuration * globalDurationMultiplier);
						phr.addNote(n);

						durationNow += duration;
						p = (p + 1) % pattern.size();
						if (p == 0) {
							nextP = -1;
						}
					}
					chordSpanPart = (chordSpanPart + 1) % ip.getChordSpan();
				}
			}
		}
        return new Result(phr, variations, genVars && variations != null);
    }
}
