package org.vibehistorian.vibecomposer;

import com.sun.media.sound.AudioSynthesizer;
import org.vibehistorian.vibecomposer.Popups.TemporaryInfoPopup;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Track;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.IOException;

/** Renders the generated MIDI sequence to a WAV file. */
public final class MidiExportController {
	public interface Context {
		Sequence getSequence();
		double getBpm();
		boolean isTransmitterMode();
		void setTransmitterMode(boolean enabled);
		Soundbank getSoundbank();
		void sendAllMidiCc();
	}

	private final Context context;

	public MidiExportController(Context context) {
		this.context = context;
	}

	@SuppressWarnings("restriction")
	public void writeWaveFile(String wavFileName, Synthesizer normalSynth)
			throws IOException {
		AudioSynthesizer synth = null;
		AudioInputStream stream1 = null;
		AudioInputStream stream2 = null;
		try {
			synth = (AudioSynthesizer) normalSynth;
			synth.close();

			stream1 = synth.openStream(null, null);
			synth.open();
			boolean transmitterMode = context.isTransmitterMode();
			if (transmitterMode) {
				context.setTransmitterMode(false);
			} else {
				Soundbank soundbank = context.getSoundbank();
				if (soundbank != null) {
					synth.unloadAllInstruments(soundbank);
					synth.loadAllInstruments(soundbank);
				}
			}

			double totalLength;
			try {
				totalLength = sendOutputSequenceMidiEvents(synth.getReceiver());
			} finally {
				if (transmitterMode) {
					context.setTransmitterMode(true);
				}
			}

			// Allow two seconds for reverb to fade out.
			totalLength += 2;
			long len = (long) (stream1.getFormat().getFrameRate() * totalLength);
			stream2 = new AudioInputStream(stream1, stream1.getFormat(), len);
			AudioSystem.write(stream2, AudioFileFormat.Type.WAVE, new File(wavFileName));
		} catch (Exception e) {
			LG.e("TERRIBLE WAV ERROR!", e);
			new TemporaryInfoPopup("Failed to export .wav file: " + e.getMessage(), 10000);
		} finally {
			if (stream1 != null) {
				stream1.close();
			}
			if (stream2 != null) {
				stream2.close();
			}
			if (synth != null) {
				synth.close();
			}
		}
	}

	private double sendOutputSequenceMidiEvents(Receiver receiver) {
		Sequence sequence = context.getSequence();
		// This method is only designed to handle PPQ division type.
		assert sequence.getDivisionType() == Sequence.PPQ : sequence.getDivisionType();

		int microsecondsPerQuarterNote = (int) (500000 * 120 / context.getBpm());
		int sequenceResolution = sequence.getResolution();
		long totalTime = 0;
		context.sendAllMidiCc();
		for (Track track : sequence.getTracks()) {
			long lastTick = 0;
			long currentTime = 0;
			for (int i = 0; i < track.size(); i++) {
				MidiEvent event = track.get(i);
				long tick = event.getTick();
				currentTime += ((tick - lastTick) * microsecondsPerQuarterNote) / sequenceResolution;
				lastTick = tick;
				MidiMessage message = event.getMessage();
				if (!(message instanceof javax.sound.midi.MetaMessage)) {
					receiver.send(message, currentTime);
				}
			}
			totalTime = Math.max(currentTime, totalTime);
		}
		return totalTime / 1000000.0;
	}
}
