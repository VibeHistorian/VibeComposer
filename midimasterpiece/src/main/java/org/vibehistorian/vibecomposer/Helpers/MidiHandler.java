package org.vibehistorian.vibecomposer.Helpers;

import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.INST;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;
import java.util.List;

public class MidiHandler {
	public interface Context {
		void setBpm(int bpm);
		int getInstrumentPartCount(INST instrument);
		void playNextNote(int keyboardTranspose, int velocity, INST instrument, int partOrder);
		void playNote(int pitch, int durationMs, int velocity, INST instrument, int partOrder);
	}

	public static boolean REPLAY_MODE = true;
	private final Context context;

	public MidiHandler(Context context) {
		this.context = context;
		MidiDevice device = null;
		MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
		for (int i = 0; i < infos.length; i++) {
			try {
				device = MidiSystem.getMidiDevice(infos[i]);
				//does the device have any transmitters?
				//if it does, add it to the device list
				LG.i(infos[i].toString());

				//get all transmitters
				List<Transmitter> transmitters = device.getTransmitters();
				//and for each transmitter

				for (int j = 0; j < transmitters.size(); j++) {
					//create a new receiver
					transmitters.get(j).setReceiver(
							//using my own MidiInputReceiver
							new MidiInputReceiver(device.getDeviceInfo().toString(), context));
				}

				Transmitter trans = device.getTransmitter();
				trans.setReceiver(new MidiInputReceiver(device.getDeviceInfo().toString(), context));

				//open each device
				if (device.getDeviceInfo().getName().equalsIgnoreCase("pianoport") && !device.isOpen()) {
					device.open();
					LG.i(device.getDeviceInfo() + " Was OPENED");
				} else {
					LG.i(device.getDeviceInfo() + " SKIPPED");
				}
			} catch (MidiUnavailableException e) {
				LG.i(device.getDeviceInfo() + " CAN'T be opened!");
			}
		}


	}

	static class MidiInputReceiver implements Receiver {
		public String name;
		private final Context context;

		public MidiInputReceiver(String name, Context context) {
			this.name = name;
			this.context = context;
		}

		public void send(MidiMessage msg, long timeStamp) {
			if (msg instanceof ShortMessage) {
				ShortMessage shortMessage = (ShortMessage) msg;
				//int command = shortMessage.getCommand();
				//int status = shortMessage.getStatus();
				//LG.i("Keyboard: " + shortMessage.getChannel() + ", " + shortMessage.getData1() + ", " + shortMessage.getData2());
				if (shortMessage.getChannel() == 15 && shortMessage.getData2() > 0) {
					context.setBpm(shortMessage.getData2());
				} else if (shortMessage.getChannel() == 0 && shortMessage.getData2() > 0) {
					if (REPLAY_MODE) {
						int[] DBCAM = {4,1,2,3,0};
						int normalizedPitch5OctavePiano = OMNI.clamp(shortMessage.getData1()-36, 0, 59);
						int dbcamIndex = normalizedPitch5OctavePiano / 12;
						int remainder = normalizedPitch5OctavePiano % 12;
						INST instrument = INST.fromIndex(DBCAM[dbcamIndex]);
						int numParts = context.getInstrumentPartCount(instrument);
						if (numParts == 0) {
							LG.i("Nothing to replay!");
							return;
						}
						int partOrder = (remainder % numParts) + 1;
						int extraTranspose = dbcamIndex == 0 ? 0 : (remainder >= 6 ? 12 : 0);
						context.playNextNote(extraTranspose,
								(int)OMNI.clamp(shortMessage.getData2()*1.5, 40, 120),
								instrument,
								partOrder);
					} else {
						context.playNote(OMNI.clampPitch(shortMessage.getData1()), 1000,
								OMNI.clampMidi(shortMessage.getData2()), INST.MELODY, 1);
					}
				}

			} else {
				LG.i("Bad msg");
			}
		}

		@Override
		public void close() {
			// Auto-generated method stub

		}
	}
}
