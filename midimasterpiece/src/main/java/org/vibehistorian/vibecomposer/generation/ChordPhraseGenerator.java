package org.vibehistorian.vibecomposer.generation;

import jm.constants.Pitches;
import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.Chord;
import org.vibehistorian.vibecomposer.Enums.PatternJoinMode;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.Helpers.PhraseExt;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Section;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Renders a chord part as notes from a progression and chord rhythm pattern. */
final class ChordPhraseGenerator extends InstPhraseGenerator<ChordPart> {
    private static final double DEFAULT_CHORD_SPLIT = 625;

    static final class ChordResult extends Result {
        private final List<Chord> chords;
        private final double flamming;
        private final int measures;

        private ChordResult(Phrase phrase, List<Chord> chords, List<Integer> variations,
                            double flamming, int measures, boolean storeVariations) {
            super(phrase, variations, storeVariations);
            this.chords = chords;
            this.flamming = flamming;
            this.measures = measures;
        }

        List<Chord> getChords() {
            return chords;
        }

        double getFlamming() {
            return flamming;
        }

        int getMeasures() {
            return measures;
        }

    }

    ChordPhraseGenerator(GUIConfig gc, VariationGenerator variationProvider) {
        super(gc, variationProvider);
    }

    ChordResult generate(ChordPart ip, List<int[]> actualProgression, List<Double> progressionDurations,
                    Map<Integer, List<Integer>> melodyNotePatternMap, Section sec,
                    int sectionOrder, List<Integer> variations, int measures, Timing timing) {
		boolean genVars = variations == null;



		int orderSeed = ip.getPatternSeedWithPartOffset() + ip.getOrderOffset();
		Phrase phr = new PhraseExt(2, ip.getOrder(), sectionOrder);
		List<Chord> chords = new ArrayList<>();
		Random variationGenerator = new Random(
				gc.getArrangement().getSeed() + ip.getOrderOffset() + sec.getTypeSeedOffset());
		Random flamGenerator = new Random(orderSeed + 30);
		Random pauseGenerator = new Random(orderSeed + 50);
		// chord strum
		double flamming = 0.0;
		if (gc.getChordGenSettings().isUseStrum()) {

			if (ip.getStrum() == 666) {
				flamming = timing.noteMultiplier * 0.6666666666666;
			} else if (ip.getStrum() == 333) {
				flamming = timing.noteMultiplier * 0.3333333333333;
			} else if (ip.getStrum() == 31) {
				flamming = timing.noteMultiplier * 0.03125;
			} else if (ip.getStrum() == 62) {
				flamming = timing.noteMultiplier * 0.0625;
			} else {
				flamming = (timing.noteMultiplier * (double) ip.getStrum()) / 1000.0;
			}
			//LG.d("Chord strum CUSTOM! " + cp.getStrum() + ", flamming: " + flamming);
		}


		int stretch = ip.getChordNotesStretch();
		List<Integer> fillPattern = ip.getChordSpanFill()
				.getPatternByLength(actualProgression.size(), ip.isFillFlip());

		int volMultiplier = (gc.isScaleMidiVelocityInArrangement()) ? sec.getVol(2) : 100;
		int minVel = MidiGeneratorUtils.multiplyVelocity(ip.getVelocityMin(), volMultiplier, 0, 1);
		int maxVel = MidiGeneratorUtils.multiplyVelocity(ip.getVelocityMax(), volMultiplier, 1, 0);

		List<Integer> chordVelocityPattern = new ArrayList<>();
		if (ip.getCustomVelocities() != null
				&& ip.getCustomVelocities().size() >= ip.getHitsPerPattern()) {
			int multiplier = gc.isScaleMidiVelocityInArrangement() ? sec.getVol(3) : 100;
			for (int k = 0; k < ip.getHitsPerPattern(); k++) {
				chordVelocityPattern.add(MidiGeneratorUtils
						.multiplyVelocity(ip.getCustomVelocities().get(k), multiplier, 0, 1));
			}
			chordVelocityPattern = MidiUtils.intersperse(null, ip.getChordSpan() - 1,
					chordVelocityPattern);
		}

		for (int i = 0; i < measures; i++) {
			Random transitionGenerator = new Random(orderSeed);
			int extraTranspose = 0;
			boolean ignoreChordSpanFill = false;
			boolean skipSecondNote = false;
			int chordSpanPart = 0;
			int skipNotes = 0;
			// fill chords
			for (int chordIndex = 0; chordIndex < actualProgression.size(); chordIndex++) {
				if (genVars && (chordIndex == 0)) {
                    variations = fillVariations(sec, ip, variations, 2);
				}

				double halfDurMulti = (chordIndex >= (actualProgression.size() + 1) / 2
						&& sec.getTransitionType() == 4) ? 2.0 : 1.0;

				if ((variations != null) && (chordIndex == 0)) {
					for (Integer var : variations) {
						if (i == measures - 1) {
							//LG.d("Chord #" + cp.getOrder() + " variation: " + var);
						}

						switch (var) {
						case 0:
							//extraTranspose = 12;
							break;
						case 1:
							ignoreChordSpanFill = true;
							break;
						case 2:
							if (stretch < 6) {
								int randomStretchAdd = variationGenerator.nextInt(6 - stretch) + 1;
								stretch += randomStretchAdd;
							}
							break;
						case 3:
							skipSecondNote = true;
							break;
						case 4:
							switch (ip.getStrumType()) {
							case ARP_D:
							case ARP_U:
								flamming = timing.eighthNote;
								break;
							case HUMAN:
							case HUMAN_D:
							case HUMAN_U:
								flamming = timing.sixteenthNote / 4;
								break;
							case RAND:
							case RAND_D:
							case RAND_U:
							case RAND_WU:
								flamming = timing.sixteenthNote;
								break;
							default:
								throw new IllegalArgumentException("Unknown StrumType!");
							}
							break;
						default:
							throw new IllegalArgumentException("Too much variation!");
						}
					}
				}

				flamGenerator.setSeed(orderSeed + 30 + (chordIndex % 4));
				Chord c = Chord.EMPTY(progressionDurations.get(chordIndex));
				if (!ignoreChordSpanFill) {
					if (fillPattern.get(chordIndex) < 1) {
						chords.add(c);
						skipNotes = 0;
						chordSpanPart = (chordSpanPart + 1) % ip.getChordSpan();
						continue;
					}
				}
				Random velocityGenerator = new Random(orderSeed + chordIndex);

				boolean transition = transitionGenerator.nextInt(100) < ip.getTransitionChance();
				int transChord = (transitionGenerator.nextInt(100) < ip.getTransitionChance())
						? (chordIndex + 1) % actualProgression.size()
						: chordIndex;

				c.setStrumPauseChance(ip.getStrumPauseChance());
				c.setStrumType(ip.getStrumType());
				c.setDurationRatio((ip.getNoteLengthMultiplier() / 100.0) / halfDurMulti);

				int[] mainChordNotes = actualProgression.get(chordIndex);
				int[] transChordNotes = actualProgression.get(transChord);

				//only skip if not already an interval (2 notes)
				boolean copiedMain = false;
				boolean copiedTrans = false;
				if (skipSecondNote) {
					if (mainChordNotes.length > 2) {
						int[] newMainChordNotes = new int[mainChordNotes.length - 1];
						for (int m = 0; m < mainChordNotes.length; m++) {
							if (m == 1)
								continue;
							int index = (m > 1) ? m - 1 : m;
							newMainChordNotes[index] = mainChordNotes[m];

						}
						mainChordNotes = newMainChordNotes;
						copiedMain = true;
					}
					if (transChordNotes.length > 2) {
						int[] newTransChordNotes = new int[transChordNotes.length - 1];
						for (int m = 0; m < transChordNotes.length; m++) {
							if (m == 1)
								continue;
							int index = (m > 1) ? m - 1 : m;
							newTransChordNotes[index] = transChordNotes[m];
						}

						transChordNotes = newTransChordNotes;
						copiedTrans = true;
					}
				}
				boolean stretchOverride = (sec.isTransition()
						&& chordIndex >= actualProgression.size() - 2);

				if (stretchOverride || ip.isStretchEnabled()) {
					int stretchAmount = (stretchOverride)
							? (sec.getTransitionType() == 1 || sec.getTransitionType() == 4 ? 7 : 2)
							: stretch;
					mainChordNotes = MidiUtils.convertChordToLength(mainChordNotes, stretchAmount);
					transChordNotes = MidiUtils.convertChordToLength(transChordNotes, stretchAmount);
					copiedMain = true;
					copiedTrans = true;
				}
				if (!copiedMain) {
					mainChordNotes = Arrays.copyOf(mainChordNotes, mainChordNotes.length);
				}
				if (!copiedTrans) {
					transChordNotes = Arrays.copyOf(transChordNotes, transChordNotes.length);
				}

				c.setTranspose(extraTranspose);
				c.setNotes(mainChordNotes);

				// for transition:
				double splitTime = progressionDurations.get(chordIndex)
						* (gc.getChordGenSettings().isUseSplit() ? ip.getTransitionSplit()
								: DEFAULT_CHORD_SPLIT)
						/ 1000.0;
				//LG.d("Split time: " + splitTime);
				PatternJoinMode joinMode = ip.getPatternJoinMode();
				int stretchedByNote = (joinMode == PatternJoinMode.JOIN) ? 1 : 0;

				List<Integer> pattern = null;
				List<Integer> nextPattern = null;
				List<Integer> velocityPattern = null;
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
					velocityPattern = !chordVelocityPattern.isEmpty()
							? PhrasePatternUtils.partOfList(chordSpanPart, ip.getChordSpan(), chordVelocityPattern)
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
				int patternExtension = 0;
				while (durationNow + timing.doubleError < progressionDurations.get(chordIndex)) {

					//LG.d("Duration counter: " + durationCounter);
					Chord cC = Chord.copy(c);

					cC.setVelocity(velocityPattern != null
							? velocityPattern.get(p % velocityPattern.size())
							: (velocityGenerator.nextInt(maxVel - minVel) + minVel));
					// less plucky
					//cC.setDurationRatio(cC.getDurationRatio() + (1 - cC.getDurationRatio()) / 2);
					if (pattern.get(p) < 1
							|| ((p + patternExtension <= nextP) && stretchedByNote == 1)
							|| skipNotes > 0) {
						if (skipNotes > 0) {
							skipNotes--;
						}
						cC.setNotes(new int[] { Pitches.REST });
					} else if (transition && durationNow >= splitTime) {
						cC.setNotes(transChordNotes);
					}

					if (pauseGenerator.nextInt(100) < ip.getPauseChance()) {
						cC.setNotes(new int[] { Pitches.REST });
					}

					if (durationNow + duration > progressionDurations.get(chordIndex) - timing.doubleError) {
						double fillerDuration = progressionDurations.get(chordIndex) - durationNow;
						cC.setRhythmValue(fillerDuration);
						if (fillerDuration < timing.fillerNoteMinDuration) {
							cC.setNotes(new int[] { Pitches.REST });
						}
					} else {
						cC.setRhythmValue(duration);
					}

					int durMultiplier = 1;
					boolean joinApplicable = (pattern.get(p) > 0)
							&& (p + patternExtension >= nextP);
					if (joinApplicable) {
						nextP = p + 1;
						while (nextP < pattern.size()) {
							if (durationNow + duration * durMultiplier
									+ timing.doubleError > progressionDurations.get(chordIndex)) {
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
						nextP += patternExtension;
					}
					joinApplicable &= (joinMode != PatternJoinMode.NOJOIN);
					//LG.d("Dur multiplier be4: " + durMultiplier);
					// chord to spill by 15%
					double durationCapMax = (ip.getStrum() > 750) ? 1.15 : 5.00;
					double durationCap = durationCapMax
							* (progressionDurations.get(chordIndex) - durationNow);
					double durationRatioCap = durationCap / cC.getRhythmValue();

					if (nextP - patternExtension >= pattern.size() && ip.getChordSpan() > 1
							&& nextPattern != null) {
						skipNotes = PhrasePatternUtils.countStartingValueInList(stretchedByNote, nextPattern);
						durMultiplier += skipNotes;
						//LG.d("CHORD Dur multiplier added: " + durMultiplier);
					}

					cC.setDurationRatio(Math.min(durationRatioCap, Math.min(durMultiplier,
							cC.getDurationRatio() * (joinApplicable ? durMultiplier : 1.0))));
					//LG.d("Dur multiplier after: " + cC.getDurationRatio());
					cC.setFlam(flamming);
					cC.makeAndStoreNotesBackwards(flamGenerator, timing.globalDurationMultiplier);
					chords.add(cC);
					durationNow += duration;
					p = (p + 1) % pattern.size();
					if (p == 0) {
						patternExtension += pattern.size();
					}
				}
				chordSpanPart = (chordSpanPart + 1) % ip.getChordSpan();
			}
		}
        return new ChordResult(phr, chords, variations, flamming, measures,
                genVars && variations != null);
    }
}
