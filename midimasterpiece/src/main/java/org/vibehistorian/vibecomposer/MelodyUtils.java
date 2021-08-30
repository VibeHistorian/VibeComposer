package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class MelodyUtils {


	public static List<Integer[]> SCALEY = new ArrayList<>();
	public static List<Integer[]> NEIGHBORY = new ArrayList<>();
	public static List<Integer[]> ARPY = new ArrayList<>();

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
	}

	public static Integer[] getRandom(Random generator) {
		int rand = generator.nextInt(3);
		switch (rand) {
		case 0:
			int rand2 = generator.nextInt(SCALEY.size());
			return SCALEY.get(rand2);
		case 1:
			int rand3 = generator.nextInt(NEIGHBORY.size());
			return NEIGHBORY.get(rand3);
		case 2:
			int rand4 = generator.nextInt(ARPY.size());
			return ARPY.get(rand4);
		default:
			throw new IllegalArgumentException("Too random");
		}
	}

	public static Integer[] getRandomForLength(Random melodyBlockGenerator, int size) {
		int rand = melodyBlockGenerator.nextInt(3);
		switch (rand) {
		case 0:
			List<Integer[]> realS = SCALEY.stream().filter(e -> e.length == size)
					.collect(Collectors.toList());
			int rand2 = melodyBlockGenerator.nextInt(realS.size());
			return realS.get(rand2);
		case 1:
			realS = NEIGHBORY.stream().filter(e -> e.length == size).collect(Collectors.toList());
			rand2 = melodyBlockGenerator.nextInt(realS.size());
			return realS.get(rand2);
		case 2:
			realS = ARPY.stream().filter(e -> e.length == size).collect(Collectors.toList());
			rand2 = melodyBlockGenerator.nextInt(realS.size());
			return realS.get(rand2);
		default:
			throw new IllegalArgumentException("Too random");
		}
	}
}
