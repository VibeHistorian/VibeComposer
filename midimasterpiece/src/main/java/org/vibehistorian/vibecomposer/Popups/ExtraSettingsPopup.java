package org.vibehistorian.vibecomposer.Popups;

import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.gui.ExtraSettingsGUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class ExtraSettingsPopup extends CloseablePopup {
	JScrollPane scroll;
	private final KnobPanel mainBpm;

	public ExtraSettingsPopup(KnobPanel mainBpm) {
		super("Extra settings", 2, new Point(-300, 50));
		this.mainBpm = mainBpm;
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
				int low = ExtraSettingsGUI.bpmLow.getInt();
				int high = ExtraSettingsGUI.bpmHigh.getInt();
				if (low > high) {
					high = low;
					ExtraSettingsGUI.bpmHigh.setInt(high);
				}
				//bpm = OMNI.clamp(bpm, low, high);
				mainBpm.getKnob().setMin(Math.min(mainBpm.getKnob().getMin(), low));
				mainBpm.getKnob().setMax(Math.max(mainBpm.getKnob().getMax(), high));
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
