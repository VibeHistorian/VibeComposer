package org.vibehistorian.vibecomposer.Popups;

import java.awt.Point;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JFrame;
import javax.swing.JScrollPane;

import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class ExtraSettingsPopup extends CloseablePopup {
	JScrollPane scroll;

	public ExtraSettingsPopup() {
		super("Extra settings", 2, new Point(-300, 50));
		scroll = new JScrollPane(VibeComposerGUI.extraSettingsPanel,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		frame.add(scroll);
		frame.pack();
		frame.setVisible(true);

		LG.d("Opened Extra Settings page!");
	}

	public JFrame getFrame() {
		return frame;
	}

	protected void addFrameWindowOperation() {
		frame.addWindowListener(new WindowListener() {

			@Override
			public void windowOpened(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowClosing(WindowEvent e) {
				//int bpm = VibeComposerGUI.mainBpm.getInt();
				int low = VibeComposerGUI.bpmLow.getInt();
				int high = VibeComposerGUI.bpmHigh.getInt();
				if (low > high) {
					high = low;
					VibeComposerGUI.bpmHigh.setInt(high);
				}
				//bpm = OMNI.clamp(bpm, low, high);
				//VibeComposerGUI.mainBpm.getKnob().setMin(low);
				//VibeComposerGUI.mainBpm.getKnob().setMaxRaw(high);
				//VibeComposerGUI.mainBpm.setInt(bpm);
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
