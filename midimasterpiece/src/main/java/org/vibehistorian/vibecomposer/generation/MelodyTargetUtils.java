package org.vibehistorian.vibecomposer.generation;

import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Direction construction and chord-aware melody target selection. */
public final class MelodyTargetUtils {
    private MelodyTargetUtils() {
    }
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
	}

	static List<Integer> convertRootsToOffsets(List<Integer> roots, int targetMode) {
		List<Integer> offsets = new ArrayList<>();
        for (Integer root : roots) {
            int value = -1 * root;
            if (targetMode == 0) {
                value /= 2;
            }
            offsets.add(value);
        }
		return offsets;
	}

	static List<Integer> multipliedDirections(List<Integer> directions, int randomSeed,
											  int targetNoteVariation, MelodyUtils.NoteTargetDirection direction) {
		if (targetNoteVariation < 1) {
			targetNoteVariation = 1;
		}
		if (direction != MelodyUtils.NoteTargetDirection.ANY) {
			targetNoteVariation *= 2;
		}
		Random rand = new Random(randomSeed);
		List<Integer> multiDirs = new ArrayList<>();
		for (Integer o : directions) {
			if (direction == MelodyUtils.NoteTargetDirection.ASC) {
				o = Math.abs(o);
			} else if (direction == MelodyUtils.NoteTargetDirection.DESC) {
				o = Math.abs(o) * -1;
			}
			int multiplied = (rand.nextInt(targetNoteVariation) + 1) * o;
			if (direction == MelodyUtils.NoteTargetDirection.ANY && o < 0) {
				// small correction for too low dips
				multiplied++;
			}
			multiDirs.add(multiplied);
		}
		if (direction == MelodyUtils.NoteTargetDirection.ASC) {
			Collections.sort(multiDirs);
		} else if (direction == MelodyUtils.NoteTargetDirection.DESC) {
			multiDirs.sort((e1, e2) -> Integer.compare(e2, e1));
		}
		return multiDirs;
	}

	static List<Integer> getRootIndexes(List<int[]> chords) {
		List<Integer> rootIndexes = new ArrayList<>();
        for (int[] chord : chords) {
            int root = chord[0];
            int rootIndex = MidiUtils.MAJ_SCALE.indexOf(root % 12);
            if (rootIndex < 0) {
                int closestPitch = MidiUtils.getClosestFromList(MidiUtils.MAJ_SCALE, root % 12);
                rootIndex = MidiUtils.MAJ_SCALE.indexOf(closestPitch % 12);
            }
            rootIndexes.add(rootIndex);
        }
		return rootIndexes;
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
				for (int pitch : c) {
					choices.add(MidiUtils.MAJ_SCALE.indexOf(MidiUtils.getClosestPitchFromList(MidiUtils.MAJ_SCALE, pitch)));
				}
				LG.i("No chord note present in Chord: " + Arrays.toString(c));
			}
			Collections.sort(choices);
			choiceMap.put(counter++, choices);
		}
		return choiceMap;
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

	public static List<Integer> generateNoteTargetOffsets(List<String> chordStrings, int randomSeed,
															  int targetMode, int targetNoteVariation, Boolean isPublic, MelodyUtils.NoteTargetDirection direction,
															  boolean useDirectionsFromProgression) {
		List<int[]> chords = new ArrayList<>();
        for (String chordString : chordStrings) {
            chords.add(MidiUtils.mappedChord(chordString));
        }
		return MelodyTargetUtils.generateNoteTargetOffsets(chords, randomSeed, targetMode,
				targetNoteVariation, direction, useDirectionsFromProgression);
	}

	static List<Integer> generateNoteTargetOffsets(List<int[]> chords, int randomSeed, int targetMode,
														   int targetNoteVariation, MelodyUtils.NoteTargetDirection noteTargetDir,
														   boolean useDirectionsFromProgression) {
		List<Integer> chordOffsets = convertRootsToOffsets(getRootIndexes(chords), targetMode);
		List<Integer> multipliedDirections = multipliedDirections(
				useDirectionsFromProgression
						? generateMelodyOffsetDirectionsFromChordProgression(chords, true,
								randomSeed)
						: randomizedChordDirections(chords.size(), randomSeed),
				randomSeed + 1, targetNoteVariation, noteTargetDir);
		List<Integer> offsets = new ArrayList<>();
		if (targetMode == 1) {
			for (int i = 0; i < chordOffsets.size(); i++) {
				offsets.add(chordOffsets.get(i) + multipliedDirections.get(i));
			}
		} else {
			offsets = multipliedDirections;
		}

		if (targetMode >= 1) {
			Map<Integer, List<Integer>> choiceMap = getChordNoteChoicesFromChords(chords);
			Random offsetRandomizer = new Random(randomSeed);
			for (int i = 0; i < chordOffsets.size(); i++) {
				List<Integer> choices = choiceMap.get(i);
				int offset = (targetMode == 1) ? offsets.get(i) - chordOffsets.get(i)
						: offsets.get(i);
				int randomChange = noteTargetDir.direction >= 0 ? 1 : -1;
				int chordTargetNote = choices.contains(offset) ? offset
						: MidiUtils.getClosestFromList(choices,
								offset + (offsetRandomizer.nextInt(100) < 75 ? randomChange : 0), noteTargetDir.direction);
				LG.d("Offset old: " + offset + ", Chord Target Note: " + chordTargetNote);
				offsets.set(i, (targetMode == 1) ? chordTargetNote + chordOffsets.get(i)
						: chordTargetNote);
			}
			if (targetMode == 2) {
				int last = offsets.get(offsets.size() - 1);
				if (offsets.size() > 3 && (last == offsets.get(offsets.size() - 3))) {
					int offsetChange = noteTargetDir.direction != 0 ? noteTargetDir.direction*2
								: (new Random(randomSeed).nextBoolean() ? 2 : -2);
					last += offsetChange;
					offsets.set(offsets.size() - 1,
							MidiUtils.getClosestFromList(choiceMap.get(offsets.size() - 1), last, noteTargetDir.direction));
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
			int offsetsToConsider = noteTargetDir == MelodyUtils.NoteTargetDirection.ANY ? offsets.size() : offsets.size()/2;
			List<Integer> offsetIterationOrder = IntStream.range(0, offsetsToConsider).boxed().collect(Collectors.toList());
			if (noteTargetDir == MelodyUtils.NoteTargetDirection.ANY) {
				Collections.shuffle(offsetIterationOrder, new Random(randomSeed + 1324));
			}

			for (int i = 0; i < offsetIterationOrder.size(); i++) {
					int order = offsetIterationOrder.get(i);
					if (MidiUtils.containsRootNote(chords.get(order % chords.size())) && Math.abs(offsets.get(order)) <= 2) {
						offsets.set(i, 0);
						LG.i("Root note inserted into note targets! At index: " + i);
						break;
					}
			}
		}
		return offsets;
	}

	static List<Boolean> generateMelodyDirectionsFromChordProgression(List<int[]> progression,
			boolean roots) {
		List<Boolean> ascDirectionList = new ArrayList<>();

		for (int i = 0; i < progression.size(); i++) {
			if (roots) {
				int current = progression.get(i)[0];
				int next = progression.get((i + 1) % progression.size())[0];
				ascDirectionList.add(current <= next);
			} else {
				int current = progression.get(i)[progression.get(i).length - 1];
				int next = progression.get((i + 1)
						% progression.size())[progression.get((i + 1) % progression.size()).length
								- 1];
				ascDirectionList.add(current <= next);
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

}
