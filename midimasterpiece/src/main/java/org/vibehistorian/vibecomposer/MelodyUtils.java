package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class MelodyUtils {


	public static List<Integer[]> SCALEY = new ArrayList<>();
	public static List<Integer[]> NEIGHBORY = new ArrayList<>();
	public static List<Integer[]> ARPY = new ArrayList<>();

	public static Map<Integer, List<Integer[]>> DISTANCE_BLOCKS = new HashMap<>();

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

		List<Integer[]> allBlocks = new ArrayList<>();
		allBlocks.addAll(SCALEY);
		allBlocks.addAll(NEIGHBORY);
		allBlocks.addAll(ARPY);

		DISTANCE_BLOCKS = allBlocks.stream().collect(Collectors.groupingBy(e -> blockDistance(e)));
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

	public static Integer[] getRandomForTypeAndDistanceAndLength(Integer type, int distance,
			int length, Random melodyBlockGenerator, int approx) {
		List<Integer[]> usedList = getBlocksForType(
				(type != null) ? type : melodyBlockGenerator.nextInt(NUM_LISTS));
		// length fits, note distance from First to Last less than distance+approx 
		List<Integer[]> filteredList = usedList.stream()
				.filter(e -> (e.length == length) && (blockDistance(e) < (distance + approx)))
				.collect(Collectors.toList());
		int rand2 = melodyBlockGenerator.nextInt(filteredList.size());
		return filteredList.get(rand2);
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

	public static Integer blockDistance(Integer[] block) {
		return Math.abs(block[0] - block[block.length - 1]);
	}
}
