package org.vibehistorian.vibecomposer;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.vibehistorian.vibecomposer.Components.CheckButton;
import org.vibehistorian.vibecomposer.Components.PlayheadRangeSlider;

/** Holds playback runtime state shared by playback controls and displays. */
public final class PlaybackState {
	private PlaybackState() {
	}

	public static Sequencer sequencer;
	public static Map<Integer, List<MidiEvent>> midiEventsToRemove = new HashMap<>();
	public static File currentMidi;
	public static File currentSequenceMidi;
	public static Map<String, Integer> partAndOrderLastNoteIndexes = new HashMap<>();

	static CheckButton loopBeat;
	public static JPanel sliderPanel;
	public static PlayheadRangeSlider slider;
	public static int sliderExtended;
	public static List<Integer> sliderMeasureStartTimes;
	public static List<Integer> sliderBeatStartTimes;
	public static JLabel currentTime;
	public static int currentSectionIndex = -1;
	public static JLabel sectionText;
	public static boolean isDragging;

	static boolean pauseInfoResettable = true;
	static int pausedBpm = 50;
	static int pausedSliderPosition;
	static int pausedMeasureCounter;
	static int startBpm = -1;
	static int startSliderPosition;
	static int startBeatCounter;
	public static double currentBeatMultiplier = 1.0;
	public static long lastPlayedMs;

	public static long msToSequencerTicks(long ms) {
		if (ms == 0 || sequencer == null || sequencer.getSequence() == null)
			return 0;
		float fps = sequencer.getSequence().getDivisionType();
		try {
			if (fps == Sequence.PPQ)
				return (long) (ms * sequencer.getTempoInBPM()
						* sequencer.getSequence().getResolution() / 60000000);
			else if (fps > Sequence.PPQ)
				return (long) (ms * fps * sequencer.getSequence().getResolution() / 1000000);
			else
				throw new Exception();
		} catch (Exception e) {
			return 0;
		}
	}

	public static int getNextNoteIndex(int part, int partOrder) {
		String key = part + "#" + partOrder;
		Integer noteIndex = partAndOrderLastNoteIndexes.get(key);
		if (noteIndex == null) {
			noteIndex = -1;
		}
		partAndOrderLastNoteIndexes.put(key, ++noteIndex);
		return noteIndex;
	}
}
