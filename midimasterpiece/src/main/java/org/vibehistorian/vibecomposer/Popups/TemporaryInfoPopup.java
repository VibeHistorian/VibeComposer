package org.vibehistorian.vibecomposer.Popups;

import java.awt.MouseInfo;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class TemporaryInfoPopup {
	final JFrame frame = new JFrame();

	public TemporaryInfoPopup(String htmlText, Integer timeoutMs) {
		this(htmlText, timeoutMs, false, true);
	}

	public TemporaryInfoPopup(String htmlText, Integer timeoutMs, boolean hideWindowControls) {
		this(htmlText, timeoutMs, hideWindowControls, true);
	}

	public TemporaryInfoPopup(String htmlText, Integer timeoutMs, boolean hideWindowControls,
			boolean showOkButton) {
		JLabel textLabel = new JLabel("<html><br>" + htmlText + "<br></html>");
		JPanel panel = new JPanel();
		panel.add(textLabel);

		frame.setLocation(MouseInfo.getPointerInfo().getLocation());
		if (hideWindowControls) {
			frame.setUndecorated(hideWindowControls);
		}
		frame.add(panel);

		if (timeoutMs != null && timeoutMs > 0) {
			Timer tmr = new Timer(timeoutMs, new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					frame.dispose();
				}
			});
			tmr.start();
		}
		if (timeoutMs == null || timeoutMs < 0 || showOkButton) {
			JButton okButton = VibeComposerGUI.makeButton("OK", e -> {
				frame.dispose();
			});
			panel.add(okButton);
		}
		frame.pack();
		frame.setVisible(true);
	}
}
