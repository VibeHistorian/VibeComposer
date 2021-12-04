package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.Popups.VisualArrayPopup;

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
						String[] valueSplit = getValue().split(",");
						List<Integer> values = new ArrayList<>();
						for (String s : valueSplit) {
							values.add(Integer.valueOf(s.trim()));

						}
						VisualArrayPopup vap = new VisualArrayPopup(-10, 10, values);
						vap.linkButton(RandomIntegerListButton.this);

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
