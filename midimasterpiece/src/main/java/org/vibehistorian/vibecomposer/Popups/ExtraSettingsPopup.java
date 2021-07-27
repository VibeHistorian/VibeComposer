package org.vibehistorian.vibecomposer.Popups;

import java.awt.MouseInfo;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JFrame;
import javax.swing.JScrollPane;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class ExtraSettingsPopup {
	final JFrame frame = new JFrame();
	JScrollPane scroll;

	public ExtraSettingsPopup() {
		scroll = new JScrollPane(VibeComposerGUI.extraSettingsPanel,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		frame.setLocation(MouseInfo.getPointerInfo().getLocation());
		frame.add(scroll);
		frame.setTitle("Extra");
		frame.pack();
		frame.setVisible(true);
		addFrameWindowOperation();

		System.out.println("Opened Extra Settings page!");
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
