package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Function;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.Popups.ButtonIntegerListValuePopup;

public class RandomIntegerListButton extends JButton {

	private static final long serialVersionUID = -2737936353529731016L;

	private Function<? super Object, String> textGenerator = null;

	public RandomIntegerListButton(String value) {
		this.setPreferredSize(new Dimension(100, 30));
		this.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					if (MidiGenerator.gc == null || MidiGenerator.chordInts.isEmpty()
							|| textGenerator == null) {
						return;
					}
					setValue(textGenerator.apply(new Object()));

				} else if (SwingUtilities.isRightMouseButton(e)) {
					setValue(value);
				} else if (SwingUtilities.isMiddleMouseButton(e)) {
					if (e.isControlDown()) {
						setEnabled(!isEnabled());
					} else if (isEnabled()) {
						new ButtonIntegerListValuePopup(RandomIntegerListButton.this);
					}

				}
			}
		});
		setValue(value);
	}

	public String getValue() {
		return getText();
	}

	public void setValue(String value) {
		if (!isEnabled()) {
			return;
		}
		setText(value);
	}

	public void setTextGenerator(Function<? super Object, String> txtGen) {
		textGenerator = txtGen;
	}

}
