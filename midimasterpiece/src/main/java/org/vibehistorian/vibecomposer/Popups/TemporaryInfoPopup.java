package org.vibehistorian.vibecomposer.Popups;

import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

import javax.swing.*;

public class TemporaryInfoPopup {
	final JDialog frame = new JDialog();

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

		LG.i(VibeComposerGUI.vibeComposerGUI.getLocation().toString());
		SwingUtils.setFrameLocation(frame, SwingUtils.getMouseLocation());
		if (hideWindowControls) {
			frame.setUndecorated(hideWindowControls);
		}
		frame.add(panel);

		if (timeoutMs != null && timeoutMs > 0) {
			Timer tmr = new Timer(timeoutMs, e -> frame.dispose());
			tmr.start();
		}
		if (timeoutMs == null || timeoutMs < 0 || showOkButton) {
			JButton okButton = SwingUtils.makeButton("OK", e -> {
				frame.dispose();
			});
			panel.add(okButton);
		}
		frame.pack();
		frame.setVisible(true);
	}
}
