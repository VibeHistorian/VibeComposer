package org.vibehistorian.vibecomposer.Popups;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JFrame;
import javax.swing.JScrollPane;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class NicheSettingsPopup {
	final JFrame frame = new JFrame();
	JScrollPane scroll;

	public NicheSettingsPopup() {
		scroll = new JScrollPane(VibeComposerGUI.nicheSettingsPanel,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		frame.add(scroll);
		frame.pack();
		frame.setVisible(true);
		addFrameWindowOperation();

		System.out.println("Opened Help page!");
	}

	public JFrame getFrame() {
		return frame;
	}

	private void addFrameWindowOperation() {
		frame.addWindowListener(new WindowListener() {

			@Override
			public void windowOpened(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowClosing(WindowEvent e) {
				int bpm = VibeComposerGUI.mainBpm.getInt();
				int low = VibeComposerGUI.bpmLow.getInt();
				int high = VibeComposerGUI.bpmHigh.getInt();
				if (low > high) {
					high = low;
					VibeComposerGUI.bpmHigh.setInt(high);
				}
				bpm = Math.max(low, Math.min(high, bpm));
				VibeComposerGUI.mainBpm.getKnob().setMin(low);
				VibeComposerGUI.mainBpm.getKnob().setMax(high);
				VibeComposerGUI.mainBpm.setInt(bpm);
			}

			@Override
			public void windowClosed(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowIconified(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowDeiconified(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowActivated(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowDeactivated(WindowEvent e) {
				// TODO Auto-generated method stub

			}

		});

	}
}
