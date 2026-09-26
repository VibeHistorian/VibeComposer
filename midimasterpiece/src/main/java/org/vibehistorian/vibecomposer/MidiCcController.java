package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.Panels.InstPanel;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;
import java.util.List;

/** Sends live instrument controls to MIDI output while the sequencer is playing. */
public final class MidiCcController {
	public interface Context {
		boolean useMidiCc();
		List<? extends InstPanel> getInstrumentPanels(int instrument);
		int getDrumVolume();
		int getGlobalVolume();
		int getGlobalReverb();
		int getGlobalChorus();
		int getGroupFilter(int instrument);
		boolean isSequencerRunning();
		void sendMidiMessage(ShortMessage message);
	}

	private final Context context;
	private Thread ccThread;

	public MidiCcController(Context context) {
		this.context = context;
	}

	public void startMidiCcThread() {
		if (ccThread != null && ccThread.isAlive()) {
			LG.i("MidiCcThread already exists!");
			return;
		}
		LG.i("Starting new MidiCcThread..!");
		ccThread = new Thread() {
			@Override
			public void run() {
				while (context.isSequencerRunning()) {
					sendAllMidiCc();
					try {
						sleep(25);
					} catch (InterruptedException e) {
						LG.e(e);
						return;
					}
				}
				LG.i("ENDED MidiCcThread!");
				ccThread = null;
			}
		};
		ccThread.start();
	}

	public void sendAllMidiCc() {
		if (!context.useMidiCc()) {
			return;
		}
		for (int instrument = 0; instrument < 4; instrument++) {
			List<? extends InstPanel> panels = context.getInstrumentPanels(instrument);
			for (InstPanel panel : panels) {
				double volume = panel.getVolSlider().getValue() / 100.0;
				int channel = panel.getMidiChannel() - 1;
				sendVolumeMessage(volume, channel);
				sendReverbMessage(1.0, channel);
				sendChorusMessage(1.0, channel);
				sendLowPassFilterMessage(1.0, channel, instrument);
				sendPanMessage(panel.getPanSlider().getValue(), channel);
			}
		}
		double drumVolume = context.getDrumVolume() / 100.0;
		sendVolumeMessage(drumVolume, 9);
		sendReverbMessage(0.5, 9);
		sendChorusMessage(0.1, 9);
		sendLowPassFilterMessage(1.0, 9, 4);
	}

	private void sendPanMessage(int pan100, int channel) {
		int value127 = context.useMidiCc() ? OMNI.clampMidi(pan100 * 127 / 100) : 64;
		sendMidiCcMessage(value127, channel, 10);
	}

	private void sendVolumeMessage(double volumeMultiplier, int channel) {
		int value127 = context.useMidiCc()
				? OMNI.clampVel(volumeMultiplier * context.getGlobalVolume() * 127 / 100.0)
				: 100;
		sendMidiCcMessage(value127, channel, 7);
	}

	private void sendReverbMessage(double reverbMultiplier, int channel) {
		int value127 = context.useMidiCc()
				? OMNI.clampVel(reverbMultiplier * context.getGlobalReverb())
				: 0;
		sendMidiCcMessage(value127, channel, 91);
	}

	private void sendChorusMessage(double chorusMultiplier, int channel) {
		int value127 = context.useMidiCc()
				? OMNI.clampVel(chorusMultiplier * context.getGlobalChorus())
				: 0;
		sendMidiCcMessage(value127, channel, 93);
	}

	private void sendLowPassFilterMessage(double filterMultiplier, int channel, int instrument) {
		int value127 = context.useMidiCc()
				? OMNI.clampVel(filterMultiplier * context.getGroupFilter(instrument))
				: 127;
		sendMidiCcMessage(value127, channel, 74);
	}

	private void sendMidiCcMessage(int value, int channel, int midiCc) {
		try {
			ShortMessage message = new ShortMessage();
			message.setMessage(ShortMessage.CONTROL_CHANGE, channel, midiCc, value);
			context.sendMidiMessage(message);
		} catch (InvalidMidiDataException e) {
			LG.e(e);
		}
	}
}
