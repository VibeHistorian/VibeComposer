package org.vibehistorian.vibecomposer.Components;

import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Popups.VisualArrayPopup;

public class RandomIntegerListButton extends JButton {

	private static final long serialVersionUID = -2737936353529731016L;

	private Function<? super Object, String> textGenerator = null;
	Function<? super Object, List<Integer>> randGenerator = null;
	Function<? super Object, Map<Integer, Set<Integer>>> highlighterGenerator = null;
	InstPanel parent = null;

	public RandomIntegerListButton(String value, InstPanel parent) {
		this.parent = parent;
		this.setPreferredSize(new Dimension(100, 30));
		this.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					if (isEnabled()) {
						String[] valueSplit = getValue().split(",");
						List<Integer> values = new ArrayList<>();
						for (String s : valueSplit) {
							values.add(Integer.valueOf(s.trim()));

						}
						VisualArrayPopup vap = new VisualArrayPopup(-10, 10, values);
						vap.linkButton(RandomIntegerListButton.this);
					}

				} else if (SwingUtilities.isRightMouseButton(e)) {
					if (isEnabled()) {
						setValue(value);
					}

				} else if (SwingUtilities.isMiddleMouseButton(e)) {
					if (e.isControlDown()) {
						setEnabled(!isEnabled());
					} else if (isEnabled()) {
						if (MidiGenerator.gc == null || MidiGenerator.chordInts.isEmpty()
								|| textGenerator == null) {
							return;
						}
						setValue(textGenerator.apply(new Object()));
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

	public Function<? super Object, String> getTextGenerator() {
		return textGenerator;
	}

	public void setTextGenerator(Function<? super Object, String> txtGen) {
		textGenerator = txtGen;
	}

	public Function<? super Object, List<Integer>> getRandGenerator() {
		return randGenerator;
	}

	public void setRandGenerator(Function<? super Object, List<Integer>> rndGen) {
		randGenerator = rndGen;
	}

	public Function<? super Object, Map<Integer, Set<Integer>>> getHighlighterGenerator() {
		return highlighterGenerator;
	}

	public void setHighlighterGenerator(
			Function<? super Object, Map<Integer, Set<Integer>>> highlighterGenerator) {
		this.highlighterGenerator = highlighterGenerator;
	}


}
