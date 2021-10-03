package org.vibehistorian.vibecomposer.Helpers;

import java.util.List;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class MidiHandler {

	public MidiHandler() {
		MidiDevice device = null;
		MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
		for (int i = 0; i < infos.length; i++) {
			try {
				device = MidiSystem.getMidiDevice(infos[i]);
				//does the device have any transmitters?
				//if it does, add it to the device list
				System.out.println(infos[i]);

				//get all transmitters
				List<Transmitter> transmitters = device.getTransmitters();
				//and for each transmitter

				for (int j = 0; j < transmitters.size(); j++) {
					//create a new receiver
					transmitters.get(j).setReceiver(
							//using my own MidiInputReceiver
							new MidiInputReceiver(device.getDeviceInfo().toString()));
				}

				Transmitter trans = device.getTransmitter();
				trans.setReceiver(new MidiInputReceiver(device.getDeviceInfo().toString()));

				//open each device
				device.open();
				//if code gets this far without throwing an exception
				//print a success message
				System.out.println(device.getDeviceInfo() + " Was Opened");


			} catch (MidiUnavailableException e) {
				System.out.println(device.getDeviceInfo() + " CAN'T be opened!");
			}
		}


	}

	class MidiInputReceiver implements Receiver {
		public String name;

		public MidiInputReceiver(String name) {
			this.name = name;
		}

		public void send(MidiMessage msg, long timeStamp) {
			if (msg instanceof ShortMessage) {
				ShortMessage shortMessage = (ShortMessage) msg;
				//int command = shortMessage.getCommand();
				//int status = shortMessage.getStatus();
				System.out.printf("Keyboard: %d, %d, %d\n", shortMessage.getChannel(),
						shortMessage.getData1(), shortMessage.getData2());
				if (shortMessage.getChannel() == 15 && shortMessage.getData2() > 0) {

					VibeComposerGUI.mainBpm.setInt(shortMessage.getData2());
				}

			} else {
				System.out.println("Bad msg");
			}
		}

		@Override
		public void close() {
			// Auto-generated method stub

		}
	}
}
