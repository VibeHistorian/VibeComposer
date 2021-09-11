package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.vibehistorian.vibecomposer.Helpers.OMNI;

public class MelodyUtils {


	public static List<Integer[]> SCALEY = new ArrayList<>();
	public static List<Integer[]> NEIGHBORY = new ArrayList<>();
	public static List<Integer[]> ARPY = new ArrayList<>();

	public static Map<Integer, List<Integer[]>> BLOCK_CHANGE_MAP = new HashMap<>();

	public static final int NUM_LISTS = 3;

	static {
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

		ARPY.add(new Integer[] { 0, 2, 4 });
		ARPY.add(new Integer[] { 0, 2, 0, 2 });
		ARPY.add(new Integer[] { 0, 2, 1 });
		ARPY.add(new Integer[] { 0, 2, 4, 2 });
		ARPY.add(new Integer[] { 0, 4, 2 });
		ARPY.add(new Integer[] { 0, 2, 1, 3 });
		ARPY.add(new Integer[] { 0, 2, 3 });
		ARPY.add(new Integer[] { 0, 4, 2, 7 });
		ARPY.add(new Integer[] { 0, 3, 1, 2 });
		ARPY.add(new Integer[] { 0, 1, 4, 5 });
		ARPY.add(new Integer[] { 0, 1, 7, 6 });
		/*ARPY.add(new Integer[] { 0, 3, 5 });
		ARPY.add(new Integer[] { 0, 4, 6 });
		ARPY.add(new Integer[] { 0, 4, 7 });*/


		List<Integer[]> allBlocks = new ArrayList<>();
		allBlocks.addAll(SCALEY);
		allBlocks.addAll(NEIGHBORY);
		allBlocks.addAll(ARPY);

		BLOCK_CHANGE_MAP = allBlocks.stream().collect(Collectors.groupingBy(e -> blockChange(e)));
	}

	public static Integer[] getRandomForType(Integer type, Random melodyBlockGenerator) {
		List<Integer[]> usedList = getBlocksForType(
				(type != null) ? type : melodyBlockGenerator.nextInt(NUM_LISTS));
		int rand2 = melodyBlockGenerator.nextInt(usedList.size());
		return usedList.get(rand2);
	}

	public static Integer[] getRandomForTypeAndLength(Integer type, Random melodyBlockGenerator,
			int length) {
		List<Integer[]> usedList = getBlocksForType(
				(type != null) ? type : melodyBlockGenerator.nextInt(NUM_LISTS));
		List<Integer[]> filteredList = usedList.stream().filter(e -> e.length == length)
				.collect(Collectors.toList());
		int rand2 = melodyBlockGenerator.nextInt(filteredList.size());
		return filteredList.get(rand2);
	}

	public static Integer[] getRandomForTypeAndDistanceAndLength(Integer type, int blockChange,
			int length, Random melodyBlockGenerator, int approx) {
		List<Integer[]> usedList = getBlocksForType(
				(type != null) ? type : melodyBlockGenerator.nextInt(NUM_LISTS));
		// length fits, note distance and distance roughly equal (diff < approx)
		List<Integer[]> filteredList = usedList.stream().filter(
				e -> (e.length == length) && (Math.abs(blockChange(e) - blockChange) <= approx))
				.collect(Collectors.toList());
		int rand2 = melodyBlockGenerator.nextInt(filteredList.size());
		return filteredList.get(rand2);
	}

	public static Integer[] getRandomByApproxBlockChangeAndLength(int blockChange, int approx,
			Random melodyBlockGenerator, Integer length) {
		int chosenChange = blockChange + melodyBlockGenerator.nextInt(approx * 2 + 1) - approx;
		chosenChange = OMNI.clamp(chosenChange, -7, 7);
		//System.out.println("Chosen change: " + chosenChange);
		List<Integer[]> viableBlocks = new ArrayList<>();
		if (BLOCK_CHANGE_MAP.containsKey(chosenChange)) {
			for (Integer[] block : BLOCK_CHANGE_MAP.get(chosenChange)) {
				if (length == null || block.length == length) {
					viableBlocks.add(block);
				}
			}

		}
		if (BLOCK_CHANGE_MAP.containsKey(chosenChange * -1)) {
			List<Integer[]> invertedBlocks = new ArrayList<>();
			for (Integer[] block : BLOCK_CHANGE_MAP.get(chosenChange * -1)) {
				if (length == null || block.length == length) {
					invertedBlocks.add(inverse(block));
				}
			}
			viableBlocks.addAll(invertedBlocks);
		}
		//viableBlocks.forEach(e -> System.out.println(StringUtils.join(e, ',')));
		return viableBlocks.get(melodyBlockGenerator.nextInt(viableBlocks.size()));

	}

	public static List<Integer[]> getBlocksForType(int type) {
		switch (type) {
		case 0:
			return new ArrayList<>(SCALEY);
		case 1:
			return new ArrayList<>(NEIGHBORY);
		case 2:
			return new ArrayList<>(ARPY);
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
}
