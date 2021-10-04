package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Popups.ButtonIntegerListValuePopup;
import org.vibehistorian.vibecomposer.Popups.CloseablePopup;

public class RandomIntegerListButton extends JButton {

	private static final long serialVersionUID = -2737936353529731016L;

	public RandomIntegerListButton(String value) {
		this.setPreferredSize(new Dimension(100, 30));
		this.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					if (MidiGenerator.gc == null || MidiGenerator.chordInts.isEmpty()) {
						return;
					}
					Random rand = new Random();
					setText(StringUtils.join(
							MidiGenerator.generateOffsets(MidiGenerator.chordInts, rand.nextInt(),
									VibeComposerGUI.melodyBlockTargetMode.getSelectedIndex(), null),
							", "));
				} else if (SwingUtilities.isRightMouseButton(e)) {
					setText("0,2,2,4");
				} else if (SwingUtilities.isMiddleMouseButton(e)) {
					CloseablePopup popup = new ButtonIntegerListValuePopup(
							RandomIntegerListButton.this);
				}
			}
		});
		setValue(value);
	}

	public String getValue() {
		return getText();
	}

	public void setValue(String value) {
		setText(value);
	}

}
