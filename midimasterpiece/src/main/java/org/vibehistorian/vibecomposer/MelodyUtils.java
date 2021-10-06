package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.Helpers.OMNI;

public class MelodyUtils {

	//public static final List<Integer> chordyNotes = Arrays.asList(new Integer[] { 0, 2, 4, 7 });
	public static final List<Integer> cMajorSubstituteNotes = Arrays
			.asList(new Integer[] { 0, 2, 3, 5 });

	public static List<Integer[]> SCALEY = new ArrayList<>();
	public static List<Integer[]> NEIGHBORY = new ArrayList<>();
	public static List<Integer[]> ARPY = new ArrayList<>();
	public static List<Integer[]> CHORDY = new ArrayList<>();

	public static Map<Integer, List<Pair<Integer, Integer[]>>> BLOCK_CHANGE_MAP = new HashMap<>();
	public static Map<Integer, Set<Integer>> AVAILABLE_BLOCK_CHANGES_PER_TYPE = new HashMap<>();
	public static List<List<Integer>> MELODY_PATTERNS = new ArrayList<>();

	public static final int NUM_LISTS = 3;

	static {
		MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 0, 1, 0, 2 }));
		MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 0, 1, 0, 2, 0, 1, 0, 3 }));
		MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 0, 0, 1, 2 }));
		MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 0, 1, 2, 0 }));
		MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 0, 1, 2, 1 }));
		MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 0, 1, 2, 2 }));
		MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 0, 1, 1, 0 }));
		MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 0, 0, 1, 0 }));
		//MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 0, 0, 1, 1 }));
		MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 0, 0, 0, 1 }));
		MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 0, 0, 0, 0 }));


		// TODO: way too crazy idea - use permutations of the array presets for extreme variation (first 0 locked, the rest varies wildly)

		SCALEY.add(new Integer[] { 0, 1, 2 });
		SCALEY.add(new Integer[] { 0, 1, 2, 3 });
		SCALEY.add(new Integer[] { 0, 1, 4 });
		SCALEY.add(new Integer[] { 0, 1, 2, 1 });
		SCALEY.add(new Integer[] { 0, 4, 2 });
		SCALEY.add(new Integer[] { 0, 1, 2, 0 });
		SCALEY.add(new Integer[] { 0, 1, 2, 4 });

		NEIGHBORY.add(new Integer[] { 0, -1, 0 });
		NEIGHBORY.add(new Integer[] { 0, 1, 0, 1 });
		NEIGHBORY.add(new Integer[] { 0, 1, -1 });
		NEIGHBORY.add(new Integer[] { 0, -1, 0, 1 });
		NEIGHBORY.add(new Integer[] { 0, -1, 2 });
		NEIGHBORY.add(new Integer[] { 0, 1, -1, 0 });
		NEIGHBORY.add(new Integer[] { 0, -1, 0, 2 });
		NEIGHBORY.add(new Integer[] { 0, 1, 3, 2 });


		CHORDY.add(new Integer[] { 0, 2, 4 });
		CHORDY.add(new Integer[] { 0, 4, 2 });
		CHORDY.add(new Integer[] { 0, 4, 2, 7 });
		CHORDY.add(new Integer[] { 0, 7, 4, 2 });

		ARPY.add(new Integer[] { 0, 2, 0, 2 });
		ARPY.add(new Integer[] { 0, 2, 1 });
		ARPY.add(new Integer[] { 0, 2, 4, 2 });
		ARPY.add(new Integer[] { 0, 2, 1, 3 });
		ARPY.add(new Integer[] { 0, 2, 3 });
		ARPY.add(new Integer[] { 0, 3, 1, 2 });
		ARPY.add(new Integer[] { 0, 1, 4, 5 });
		ARPY.add(new Integer[] { 0, 1, 7, 6 });
		ARPY.add(new Integer[] { 0, 1, 6, 7 });
		/*ARPY.add(new Integer[] { 0, 3, 5 });
		ARPY.add(new Integer[] { 0, 4, 6 });
		ARPY.add(new Integer[] { 0, 4, 7 });*/


		List<Pair<Integer, Integer[]>> allBlocks = new ArrayList<>();
		SCALEY.forEach(e -> allBlocks.add(Pair.of(0, e)));
		NEIGHBORY.forEach(e -> allBlocks.add(Pair.of(1, e)));
		ARPY.forEach(e -> allBlocks.add(Pair.of(2, e)));
		CHORDY.forEach(e -> allBlocks.add(Pair.of(3, e)));

		AVAILABLE_BLOCK_CHANGES_PER_TYPE.put(0,
				SCALEY.stream().map(e -> blockChange(e)).collect(Collectors.toSet()));
		AVAILABLE_BLOCK_CHANGES_PER_TYPE.put(1,
				NEIGHBORY.stream().map(e -> blockChange(e)).collect(Collectors.toSet()));
		AVAILABLE_BLOCK_CHANGES_PER_TYPE.put(2,
				ARPY.stream().map(e -> blockChange(e)).collect(Collectors.toSet()));
		AVAILABLE_BLOCK_CHANGES_PER_TYPE.put(3,
				CHORDY.stream().map(e -> blockChange(e)).collect(Collectors.toSet()));


		BLOCK_CHANGE_MAP = allBlocks.stream()
				.collect(Collectors.groupingBy(e -> blockChange(e.getRight())));
	}

	public static Integer[] getRandomForType(Integer type, Random melodyBlockGenerator) {
		List<Integer[]> usedList = getBlocksForType(type);
		int rand2 = melodyBlockGenerator.nextInt(usedList.size());
		return usedList.get(rand2);
	}

	public static Integer[] getRandomForTypeAndLength(Integer type, Random melodyBlockGenerator,
			int length) {
		List<Integer[]> usedList = getBlocksForType(type);
		List<Integer[]> filteredList = usedList.stream().filter(e -> e.length == length)
				.collect(Collectors.toList());
		if (filteredList.size() == 0) {
			return null;
		}
		int rand2 = melodyBlockGenerator.nextInt(filteredList.size());
		return filteredList.get(rand2);
	}

	public static Integer[] getRandomForTypeAndBlockChangeAndLength(Integer type, int blockChange,
			int length, Random melodyBlockGenerator, int approx) {
		List<Integer[]> usedList = getBlocksForType(type);
		// length fits, note distance and distance roughly equal (diff < approx)
		List<Integer[]> filteredList = usedList.stream()
				.filter(e -> (e.length == length)
						&& (Math.abs(blockChange(e) - Math.abs(blockChange)) <= approx))
				.collect(Collectors.toList());
		if (filteredList.size() == 0) {
			return null;
		}
		int rand2 = melodyBlockGenerator.nextInt(filteredList.size());
		Integer[] block = filteredList.get(rand2);
		if (blockChange(block) == -1 * blockChange) {
			return inverse(block);
		} else {
			return block;
		}
	}

	public static Pair<Integer, Integer[]> getRandomByApproxBlockChangeAndLength(int blockChange,
			int approx, Random melodyBlockGenerator, Integer length) {
		int chosenChange = blockChange + melodyBlockGenerator.nextInt(approx * 2 + 1) - approx;
		chosenChange = OMNI.clamp(chosenChange, -7, 7);
		//System.out.println("Chosen change: " + chosenChange);
		List<Pair<Integer, Integer[]>> viableBlocks = new ArrayList<>();
		if (BLOCK_CHANGE_MAP.containsKey(chosenChange)) {
			for (Pair<Integer, Integer[]> typeBlock : BLOCK_CHANGE_MAP.get(chosenChange)) {
				Integer[] block = typeBlock.getRight();
				if (length == null || block.length == length) {
					viableBlocks.add(typeBlock);
				}
			}

		}
		if (BLOCK_CHANGE_MAP.containsKey(chosenChange * -1)) {
			List<Pair<Integer, Integer[]>> invertedBlocks = new ArrayList<>();
			for (Pair<Integer, Integer[]> typeBlock : BLOCK_CHANGE_MAP.get(chosenChange * -1)) {
				Integer[] block = typeBlock.getRight();
				if (length == null || block.length == length) {
					invertedBlocks.add(Pair.of(typeBlock.getLeft(), inverse(block)));
				}
			}
			viableBlocks.addAll(invertedBlocks);
		}
		//viableBlocks.forEach(e -> System.out.println(StringUtils.join(e, ',')));
		if (viableBlocks.size() == 0) {
			Integer[] block = getRandomForTypeAndBlockChangeAndLength(null, blockChange, length,
					melodyBlockGenerator, 4);
			return Pair.of(blockOfList(block), block);
		}
		return viableBlocks.get(melodyBlockGenerator.nextInt(viableBlocks.size()));

	}

	public static List<Integer[]> getBlocksForType(Integer type) {
		if (type == null) {
			List<Integer[]> blocks = new ArrayList<>();
			blocks.addAll(SCALEY);
			blocks.addAll(NEIGHBORY);
			blocks.addAll(ARPY);
			blocks.addAll(CHORDY);
			return blocks;
		}

		switch (type) {
		case 0:
			return new ArrayList<>(SCALEY);
		case 1:
			return new ArrayList<>(NEIGHBORY);
		case 2:
			return new ArrayList<>(ARPY);
		case 3:
			return new ArrayList<>(CHORDY);
		default:
			throw new IllegalArgumentException("Blocks type too random!");
		}
	}

	public static Integer[] inverse(Integer[] block) {
		Integer[] newBlock = new Integer[block.length];
		for (int i = 0; i < block.length; i++) {
			newBlock[i] = block[i] * -1;
		}
		return newBlock;
	}

	public static Integer blockChange(Integer[] block) {
		return block[block.length - 1] - block[0];
	}

	public static List<Integer> blockChangeSequence(int chord1, int chord2, int randSeed,
			int numBlocks, int maxBlockChange) {
		Random rand = new Random(randSeed);
		List<Integer> changeList = new ArrayList<>();

		// how many notes need to be corrected | change = 5 -> sum of block change sequence must be -5
		int change = chord1 - chord2;
		//System.out.println("Change: " + change);
		List<Integer> reducableIndices = new ArrayList<>();
		for (int i = 0; i < numBlocks; i++) {
			int chg = rand.nextInt(maxBlockChange * 2 + 1) - maxBlockChange;
			changeList.add(chg);
			change += chg;
			reducableIndices.add(i);
		}
		//System.out.println("Initial: " + StringUtils.join(changeList, ","));
		if (change > 0) {
			reducableIndices.removeIf(e -> changeList.get(e) == -1 * maxBlockChange);
		} else if (change < 0) {
			reducableIndices.removeIf(e -> changeList.get(e) == maxBlockChange);
		}

		rand.setSeed(randSeed);
		int increment = (change > 0) ? -1 : 1;
		for (int i = 0; i < Math.abs(change); i++) {
			if (reducableIndices.size() == 0)
				break;
			int redI = rand.nextInt(reducableIndices.size());
			int redIndex = reducableIndices.get(redI);
			int newValue = changeList.get(redIndex) + increment;
			changeList.set(redIndex, newValue);
			if (Math.abs(newValue) == maxBlockChange) {
				Integer removed = reducableIndices.remove(redI);
			}
		}
		rand.setSeed(randSeed);

		//System.out.println("Decr: " + StringUtils.join(changeList, ","));
		Collections.shuffle(changeList, rand);

		//System.out.println("Shuffled: " + StringUtils.join(changeList, ","));
		return changeList;
	}

	public boolean blockContainsJump(Integer[] block, int jump) {
		for (int i = 1; i < block.length; i++) {
			if (Math.abs(block[i] - block[i - 1]) == jump) {
				return true;
			}
		}
		return false;
	}

	public static int blockOfList(Integer[] block) {
		Integer[] invertedBlock = inverse(block);
		if (SCALEY.contains(block) || SCALEY.contains(invertedBlock)) {
			return 0;
		} else if (NEIGHBORY.contains(block) || NEIGHBORY.contains(invertedBlock)) {
			return 1;
		} else if (ARPY.contains(block) || ARPY.contains(invertedBlock)) {
			return 2;
		} else if (CHORDY.contains(block) || CHORDY.contains(invertedBlock)) {
			return 3;
		}
		throw new IllegalArgumentException("Unknown block!");
	}

	public static List<Integer> getRandomMelodyPattern(int altPatternChance, Integer randomSeed) {
		Random rand = new Random();
		if (randomSeed != null) {
			rand.setSeed(randomSeed);
		}
		if (rand.nextInt(100) < altPatternChance) {
			List<Integer> altPatternIndices = Arrays.asList(0, 1);
			return new ArrayList<>(MELODY_PATTERNS
					.get(altPatternIndices.get(rand.nextInt(altPatternIndices.size()))));
		}
		return new ArrayList<>(MELODY_PATTERNS.get(rand.nextInt(MELODY_PATTERNS.size())));
	}
}
