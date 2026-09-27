package org.vibehistorian.vibecomposer;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Track;
import java.util.List;

import static org.vibehistorian.vibecomposer.PlaybackState.*;

/** Coordinates playback transport and its saved playhead position. */
public final class PlaybackController {
	public interface Context {
		void startMidiCcThread();
		boolean startFromBar();
		int currentBpm();
		boolean hasGeneratedChordData();
	}

	private final Context context;

	public PlaybackController(Context context) {
		this.context = context;
	}

	public void playMidi(boolean replay) {
		LG.i("Starting Midi..");
		if (sequencer != null) {
			boolean wasRunning = sequencer.isRunning();
			if (!replay) {
				sequencer.stop();
			}

			if (wasRunning) {
				long startPos = context.startFromBar()
						? sliderMeasureStartTimes.get(pausedMeasureCounter)
						: pausedSliderPosition;
				if (startPos < slider.getValue()) {
					startPos = slider.getValue();
				}
				midiNavigate(startPos);
			} else {
				savePauseInfo();
				if (pausedSliderPosition > 0 && pausedSliderPosition < slider.getMaximum() - 100) {
					LG.d("Unpausing..");
					midiNavigate(pausedSliderPosition);
				} else {
					LG.d("Resetting..");
					resetSequencerTickPosition();
				}
			}

			LG.d("Position set..");
			if (!replay) {
				try {
					Thread.sleep(25);
				} catch (InterruptedException e) {
					LG.e(e);
				}
			} else {
				sequencer.setLoopCount(1);
			}

			sequencer.start();
			context.startMidiCcThread();
			sequencer.setLoopCount(0);
			LG.i("Started Midi: " + pausedSliderPosition + "/" + slider.getMaximum() + ", measure: "
					+ pausedMeasureCounter);
		} else {
			LG.i("Sequencer is NULL!");
		}
	}

	public void stopMidi() {
		LG.i("Stopping Midi..");
		if (sequencer != null) {
			sequencer.stop();
			flushMidiEvents();
			slider.setUpperValue(slider.getValue());
			resetPauseInfo();
			LG.i("Stopped Midi!");
		} else {
			LG.i("Sequencer is NULL!");
		}
	}

	public void pauseMidi() {
		LG.i("Pausing Midi..");
		if (sequencer != null) {
			sequencer.stop();
			flushMidiEvents();
			savePauseInfo();
			LG.i("Paused Midi: " + pausedSliderPosition + ", measure: " + pausedMeasureCounter);
		} else {
			LG.i("Sequencer is NULL!");
		}
	}

	public void savePauseInfo() {
		pausedSliderPosition = slider.getUpperValue();
		pausedBpm = context.currentBpm();
		pausedMeasureCounter = findCurrentSegmentIndex(pausedSliderPosition,
				sliderMeasureStartTimes);
	}

	public void saveStartInfo() {
		startSliderPosition = slider.getValue();
		startBeatCounter = findCurrentSegmentIndex(startSliderPosition, sliderBeatStartTimes);
	}

	private int findCurrentSegmentIndex(int position, List<Integer> segmentStartTimes) {
		if (currentMidi == null || !context.hasGeneratedChordData()) {
			return 0; // forced
		}
		for (int i = 1; i < segmentStartTimes.size(); i++) {
			if (segmentStartTimes.get(i) >= position + 50) {
				return i - 1;
			}
		}
		return 0;
	}

	public void setPauseInfoResettable(boolean resettable) {
		pauseInfoResettable = resettable;
	}

	public void resetPauseInfo() {
		if (pauseInfoResettable) {
			pausedSliderPosition = 0;
			pausedMeasureCounter = 0;
		}
	}

	public void midiNavigate(long sliderValue) {
		midiNavigate(sliderValue, 25);
	}

	public void midiNavigate(long sliderValue, int offset) {
		long time = (sliderValue - offset) * 1000;
		long timeTicks = PlaybackState.msToSequencerTicks(time);
		if (!(time != 0 && timeTicks == 0) || time >= sequencer.getMicrosecondLength()) {
			sequencer.setMicrosecondPosition(Math.max(0, time));
		}
		flushMidiEvents();
	}

	public void resetSequencerTickPosition() {
		if (slider.getValue() < slider.getMaximum()) {
			midiNavigate(slider.getValue());
		} else {
			slider.setValue(0);
			midiNavigate(0);
		}
	}

	public void flushMidiEvents() {
		if (sequencer == null || !sequencer.isOpen() || midiEventsToRemove.isEmpty()) {
			return;
		}

		midiEventsToRemove.forEach((trackIndex, events) -> {
			Track[] tracks = sequencer.getSequence().getTracks();
			if (trackIndex < tracks.length) {
				Track track = tracks[trackIndex];
				for (MidiEvent event : events) {
					track.remove(event);
				}
			}
		});
		midiEventsToRemove.clear();
	}
}
