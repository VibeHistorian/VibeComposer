package org.vibehistorian.vibecomposer;

import javax.sound.midi.*;
import javax.swing.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.vibehistorian.vibecomposer.ApplicationSessionState.soundfont;
import static org.vibehistorian.vibecomposer.PlaybackState.sequencer;
import static org.vibehistorian.vibecomposer.SoloMuteState.needToRecalculateSoloMutersAfterSequenceGenerated;

/** Owns MIDI output devices and synthesizer lifecycle. */
public final class MidiDeviceController {
	public interface Context {
		boolean isTransmitterMode();
		String getSelectedDeviceName();
		File getSoundbankFile();
		void stopPlayback();
		void showSequenceReadError();
	}

	private final Context context;
	private MidiDevice device;
	private Synthesizer synth;
	private boolean isSoundbankSynth;
	private boolean needSoundbankRefresh;

	public MidiDeviceController(Context context) {
		this.context = context;
	}

	public boolean hasMidiDevice() {
		return device != null;
	}

	public void markSoundbankRefreshNeeded() {
		needSoundbankRefresh = true;
	}

	public void softCloseSynth() {
		closeMidiDevice();
		synth = null;
	}

	public void closeMidiDevice() {
		context.stopPlayback();
		if (sequencer != null) {
			sequencer.close();
			sequencer = null;
		}

		LG.i("Closed sequencer!");
		MidiDevice oldDevice = device;
		device = null;
		if (oldDevice != null) {
			oldDevice.close();
		}
		LG.i("Closed oldDevice!");
		needToRecalculateSoloMutersAfterSequenceGenerated = true;
	}

	/** Releases the active endpoint before a new composition, preserving mode-specific behavior. */
	public void prepareForComposition() {
		if (context.isTransmitterMode()) {
			if (synth != null) {
				unloadSoundbankIfNeeded();
				synth.close();
				synth = null;
				System.gc();
			}
		} else if (device != null) {
			if (synth != null) {
				synth.close();
				synth = null;
			}
			if (sequencer != null) {
				sequencer.close();
				sequencer = null;
				LG.i("CLOSED SEQUENCER!");
			}
			device.close();
			device = null;
			LG.i("CLOSED DEVICE!");
		}
	}

	public Synthesizer getSynthesizerForWaveExport() throws MidiUnavailableException {
		Synthesizer exportSynth = (synth != null && !context.isTransmitterMode())
				? synth : MidiSystem.getSynthesizer();
		synth = exportSynth;
		return exportSynth;
	}

	public void clearAfterWaveExport() {
		synth = null;
		if (device != null) {
			device.close();
			device = null;
		}
	}

	public Synthesizer loadSynth() {
		Synthesizer synthesizer = null;
		try {
			File soundbankFile = context.getSoundbankFile();
			if (soundbankFile.isFile()) {
				if (synth == null || !isSoundbankSynth || needSoundbankRefresh) {
					if (synth != null && isSoundbankSynth && soundfont != null) {
						synth.unloadAllInstruments(soundfont);
						synth.close();
						synth = null;
						System.gc();
					}
					synth = null;
					soundfont = MidiSystem.getSoundbank(
							new BufferedInputStream(Files.newInputStream(soundbankFile.toPath())));
					synthesizer = MidiSystem.getSynthesizer();
					synthesizer.isSoundbankSupported(soundfont);
					synthesizer.open();
					synthesizer.loadAllInstruments(soundfont);
					needSoundbankRefresh = false;
				} else {
					// Keep using the already loaded soundbank synth on subsequent compositions.
					synthesizer = synth;
				}
				LG.i("Playing using soundbank: " + soundbankFile);
			} else {
				closeSoundbankSynth();
				synthesizer = null;
				synth = null;
				soundfont = null;
				LG.i("NO SOUNDBANK WITH THAT NAME FOUND!");
			}
		} catch (InvalidMidiDataException | IOException | MidiUnavailableException ex) {
			closeSoundbankSynth();
			synthesizer = null;
			synth = null;
			soundfont = null;
			LG.e(ex);
			LG.i("NO SOUNDBANK WITH THAT NAME FOUND!");
		}
		synth = synthesizer;
		return synthesizer;
	}

	public boolean prepareMidiPlayback(File sequenceFile, Synthesizer synthesizer)
			throws InvalidMidiDataException, MidiUnavailableException {
		Sequence sequence;
		try {
			sequence = MidiSystem.getSequence(sequenceFile);
		} catch (Exception e) {
			context.showSequenceReadError();
			return false;
		}
		sequencer.setSequence(sequence);

		if (context.isTransmitterMode()) {
			if (device == null) {
				closeSequencerTransmitters();
				for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
					if (info.toString().equalsIgnoreCase(context.getSelectedDeviceName())) {
						device = MidiSystem.getMidiDevice(info);
						LG.d(info + "| max recv: " + device.getMaxReceivers()
								+ ", max trm: " + device.getMaxTransmitters());
						if (device.getMaxReceivers() != 0) {
							LG.d("Found max receivers != 0, opening midi receiver device: " + info);
							device.open();
							break;
						}
					}
				}
				sequencer.getTransmitter().setReceiver(device.getReceiver());
			}
		} else if (synthesizer != null) {
			closeSequencerTransmitters();
			sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
			synth = synthesizer;
			isSoundbankSynth = true;
		} else if (synth == null) {
			LG.i("Using Default system Synthesizer!");
			closeSequencerTransmitters();
			synth = MidiSystem.getSynthesizer();
			synth.open();
			sequencer.getTransmitter().setReceiver(synth.getReceiver());
			isSoundbankSynth = false;
		}
		return true;
	}

	public void sendMessage(MidiMessage message) {
		if (context.isTransmitterMode() && device != null) {
			for (Receiver receiver : device.getReceivers()) {
				receiver.send(message, -1);
			}
		} else if (synth != null && synth.isOpen()) {
			for (Receiver receiver : synth.getReceivers()) {
				receiver.send(message, -1);
			}
		}
	}

	public void playNote(int midiChannel, int note, int velocity, int durationMs)
			throws InvalidMidiDataException {
		if (!context.isTransmitterMode()) {
			if (synth == null) {
				if (sequencer.isRunning()) {
					sequencer.stop();
				}
				synth = loadSynth();
				LG.i("Loaded new synth!");
			}
			MidiChannel channel = synth.getChannels()[midiChannel];
			channel.noteOn(note, velocity);
			Timer timer = new Timer(durationMs, e -> channel.noteOff(note));
			timer.setRepeats(false);
			timer.start();
		} else {
			if (device == null) {
				LG.i("Can't play into a null midi device!");
				return;
			}
			ShortMessage noteOn = new ShortMessage();
			noteOn.setMessage(ShortMessage.NOTE_ON, midiChannel, note, velocity);
			ShortMessage noteOff = new ShortMessage();
			noteOff.setMessage(ShortMessage.NOTE_OFF, midiChannel, note, 0);
			sendToDevice(noteOn);
			Timer timer = new Timer(durationMs, e -> sendToDevice(noteOff));
			timer.setRepeats(false);
			timer.start();
		}
	}

	public void sendVolumeMessage(ShortMessage message) {
		sendMessage(message);
	}

	private void sendToDevice(MidiMessage message) {
		for (Receiver receiver : device.getReceivers()) {
			receiver.send(message, -1);
		}
	}

	private void closeSequencerTransmitters() {
		for (Transmitter transmitter : sequencer.getTransmitters()) {
			transmitter.close();
		}
	}

	private void unloadSoundbankIfNeeded() {
		if (isSoundbankSynth && soundfont != null) {
			synth.unloadAllInstruments(soundfont);
		}
	}

	private void closeSoundbankSynth() {
		if (synth != null && isSoundbankSynth && soundfont != null) {
			synth.unloadAllInstruments(soundfont);
			synth.close();
			synth = null;
			System.gc();
		}
	}
}
