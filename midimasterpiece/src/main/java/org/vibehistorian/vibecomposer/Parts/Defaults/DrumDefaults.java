package org.vibehistorian.vibecomposer.Parts.Defaults;

import static org.vibehistorian.vibecomposer.Enums.RhythmPattern.ALT;
import static org.vibehistorian.vibecomposer.Enums.RhythmPattern.ONEPER4;
import static org.vibehistorian.vibecomposer.Enums.RhythmPattern.ONESIX;
import static org.vibehistorian.vibecomposer.Enums.RhythmPattern.TRESILLO;

import java.util.Random;

import org.vibehistorian.vibecomposer.InstUtils;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Parts.DrumPart;

public class DrumDefaults {
	/*public static final DrumPart kick = makeSimpleDrum(36, 8, 1);
	public static final DrumPart snare = makeSimpleDrum(38, 8, 1);
	public static final DrumPart hat = makeSimpleDrum(42, 8, 1, RhythmPattern.FULL, 40, 75);
	public static final DrumPart ride = makeSimpleDrum(53, 8, 1, RhythmPattern.SINGLE, 50, 85);
	public static final DrumPart percs = makeSimpleDrum(60, 8, 1, RhythmPattern.TRESILLO, 40, 75);
	public static final DrumPart[] drums = new DrumPart[] { kick, snare, hat, ride, percs };*/

	public static final int[] instrumentThresholds = new int[] { 37, 41, 45, 53, 60 };

	public static DrumPart getDefaultDrumPart(int drum) {
		//  0,1,2,3,4 == kick, snare, hat, ride, percs
		switch (drum) {
		case 0:
			return makeSimpleDrum(36, 8, 1);
		case 1:
			return makeSimpleDrum(38, 8, 1);
		case 2:
			return makeSimpleDrum(42, 8, 1, RhythmPattern.FULL, 40, 75);
		case 3:
			return makeSimpleDrum(53, 8, 1, RhythmPattern.SINGLE, 50, 85);
		case 4:
			return makeSimpleDrum(60, 8, 1, RhythmPattern.TRESILLO, 40, 75);
		default:
			throw new IllegalArgumentException("Drum number too high!");
		}
	}

	public static DrumSettings[] drumSettings;

	public static DrumSettings kickSettings;
	public static DrumSettings snareSettings;
	public static DrumSettings hatSettings;
	public static DrumSettings rideSettings;
	public static DrumSettings percsSettings;

	static {
		kickSettings = new DrumSettings();
		kickSettings.setPatterns(
				new RhythmPattern[] { ONEPER4, ONESIX, TRESILLO, ALT, ALT, RhythmPattern.CUSTOM });
		kickSettings.setHits(new Integer[] { 8, 8, 8, 8, 8, 16 });
		kickSettings.setChords(new Integer[] { 1, 1, 1, 2, 1, 1 });
		kickSettings.setShift(new Integer[] { 0, 0, 0, 0, 0, 0 });
		kickSettings.maxExc = 2;

		snareSettings = new DrumSettings();
		snareSettings.setPatterns(
				new RhythmPattern[] { ONEPER4, ONESIX, ALT, RhythmPattern.SINGLE, ONEPER4 });
		snareSettings.setHits(new Integer[] { 8, 8, 8, 8, 8 });
		snareSettings.setChords(new Integer[] { 1, 1, 2, 1, 1 });
		snareSettings.setShift(new Integer[] { 2, 2, 1, 6, 3 });
		snareSettings.maxExc = 0;

		hatSettings = new DrumSettings();
		hatSettings.setPatterns(
				new RhythmPattern[] { ONEPER4, ONESIX, TRESILLO, ALT, RhythmPattern.FULL });
		hatSettings.setHits(new Integer[] { 16, 16, 8, 8, 8 });
		hatSettings.setChords(new Integer[] { 1, 1, 1, 2, 2 });
		hatSettings.setShift(new Integer[] { 2, 2, 2, 1, 0 });
		hatSettings.setVariableShift(true);
		hatSettings.dynamicable = true;
		hatSettings.fillable = true;
		hatSettings.maxPause = 20;
		hatSettings.swingable = true;
		hatSettings.melodyable = true;

		rideSettings = new DrumSettings();
		rideSettings.setPatterns(new RhythmPattern[] { ONEPER4, RhythmPattern.SINGLE });
		rideSettings.setHits(new Integer[] { 8, 8 });
		rideSettings.setChords(new Integer[] { 1, 1 });
		rideSettings.setShift(new Integer[] { 2, 6 });
		rideSettings.setVariableShift(true);
		rideSettings.maxExc = 0;
		//rideSettings.melodyable = true;

		percsSettings = new DrumSettings();
		percsSettings.setPatterns(
				new RhythmPattern[] { ONEPER4, ONESIX, TRESILLO, ALT, RhythmPattern.FULL });
		percsSettings.setHits(new Integer[] { 16, 16, 8, 8, 8 });
		percsSettings.setChords(new Integer[] { 1, 1, 1, 2, 2 });
		percsSettings.setShift(new Integer[] { 3, 3, 5, 1, 0 });
		percsSettings.setVariableShift(true);
		percsSettings.dynamicable = true;
		percsSettings.fillable = true;
		percsSettings.maxPause = 20;
		percsSettings.swingable = true;
		percsSettings.melodyable = true;

		drumSettings = new DrumSettings[] { kickSettings, snareSettings, hatSettings, rideSettings,
				percsSettings };
	}

	//public static List<DrumPart> xyzCollection;

	public static DrumPart makeSimpleDrum(int instrument, int hits, int chords) {
		return makeSimpleDrum(instrument, hits, chords, RhythmPattern.FULL, 65, 100);
	}

	public static DrumPart makeSimpleDrum(int instrument, int hits, int chords,
			RhythmPattern pattern, int minVel, int maxVel) {
		DrumPart dp = new DrumPart();
		dp.setInstrument(instrument);
		dp.setHitsPerPattern(hits);
		dp.setChordSpan(chords);
		dp.setPattern(pattern);
		dp.setVelocityMin(minVel);
		dp.setVelocityMax(maxVel);

		dp.setPauseChance(0);
		dp.setExceptionChance(0);
		dp.setVelocityPattern(false);

		return dp;
	}

	public static DrumPart getRandomDrum() {
		Integer instrument = MidiUtils.getRandom(new Random(), InstUtils.DRUM_INST_NUMBERS);
		return getDrumFromInstrument(instrument);
	}

	public static int getOrder(Integer instrument) {
		int order = 1;
		while (instrument > instrumentThresholds[order - 1]
				&& order < instrumentThresholds.length) {
			order++;
		}
		order--;
		return order;
	}

	public static DrumPart getDrumFromInstrument(Integer instrument) {
		int order = getOrder(instrument);
		DrumPart dp = getDefaultDrumPart(order);
		dp.setInstrument(instrument);
		if (instrument == 37) {
			//side stick adjustment
			dp.setVelocityMin(50);
			dp.setVelocityMax(75);
		}
		return dp;
	}

	/*public static DrumPart getDrum(int order) {
		DrumPanel dpp = new DrumPanel(null);
		dpp.setFromInstPart(getDefaultDrumPart(order));
		DrumPart dpCopy = dpp.toDrumPart(0);
		return dpCopy;
	}*/

}
