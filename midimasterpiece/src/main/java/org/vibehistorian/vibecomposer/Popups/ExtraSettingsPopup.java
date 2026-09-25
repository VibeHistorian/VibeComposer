package org.vibehistorian.vibecomposer.Popups;

import org.vibehistorian.vibecomposer.ExtraSettingsGUI;
import org.vibehistorian.vibecomposer.GenerationGUI;
import org.vibehistorian.vibecomposer.LG;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class ExtraSettingsPopup extends CloseablePopup {
	JScrollPane scroll;

	public ExtraSettingsPopup() {
		super("Extra settings", 2, new Point(-300, 50));
		scroll = new JScrollPane(ExtraSettingsGUI.extraSettingsPanel,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		frame.add(scroll);
		frame.pack();
		frame.setVisible(true);

		LG.d("Opened Extra Settings page!");
	}

	protected void addFrameWindowOperation() {
		frame.addWindowListener(new WindowListener() {

			@Override
			public void windowOpened(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowClosing(WindowEvent e) {
				//int bpm = GenerationGUI.mainBpm.getInt();
				int low = ExtraSettingsGUI.bpmLow.getInt();
				int high = ExtraSettingsGUI.bpmHigh.getInt();
				if (low > high) {
					high = low;
					ExtraSettingsGUI.bpmHigh.setInt(high);
				}
				//bpm = OMNI.clamp(bpm, low, high);
				GenerationGUI.mainBpm.getKnob()
						.setMin(Math.min(GenerationGUI.mainBpm.getKnob().getMin(), low));
				GenerationGUI.mainBpm.getKnob()
						.setMax(Math.max(GenerationGUI.mainBpm.getKnob().getMax(), high));
				//GenerationGUI.mainBpm.getKnob().setMaxRaw(high);
				//GenerationGUI.mainBpm.setInt(bpm);
			}

			@Override
			public void windowClosed(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowIconified(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowDeiconified(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowActivated(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowDeactivated(WindowEvent e) {
				// Auto-generated method stub

			}

		});

	}
}
