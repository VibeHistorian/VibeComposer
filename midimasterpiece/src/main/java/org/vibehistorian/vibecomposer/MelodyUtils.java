package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.List;

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
}
