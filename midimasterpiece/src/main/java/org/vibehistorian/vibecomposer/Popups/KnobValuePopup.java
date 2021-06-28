package org.vibehistorian.vibecomposer.Popups;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JFrame;

import org.vibehistorian.vibecomposer.Helpers.JKnob;
import org.vibehistorian.vibecomposer.Panels.NumPanel;

public class KnobValuePopup {
	final JFrame frame = new JFrame();
	private JKnob knob = null;
	private NumPanel numPanel = null;

	public KnobValuePopup(JKnob knob) {
		this.knob = knob;
		numPanel = new NumPanel("Knob", knob.getCurr(), knob.getMin(), knob.getMax());
		frame.add(numPanel);
		addFrameWindowOperation();

		frame.setTitle("Knob Value Setting");
		frame.pack();
		frame.setVisible(true);

	}

	private void addFrameWindowOperation() {
		frame.addWindowListener(new WindowListener() {

			@Override
			public void windowOpened(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowClosing(WindowEvent e) {
				knob.setValue(numPanel.getInt());

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
