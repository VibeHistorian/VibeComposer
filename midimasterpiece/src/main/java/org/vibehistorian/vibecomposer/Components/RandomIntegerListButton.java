package org.vibehistorian.vibecomposer.Components;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Popups.VisualArrayPopup;
import org.vibehistorian.vibecomposer.SwingUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class RandomIntegerListButton extends JButton {

	private static final long serialVersionUID = -2737936353529731016L;

	private Function<? super Object, String> textGenerator = null;
	Function<? super Object, List<Integer>> randGenerator = null;
	Function<? super Object, Map<Integer, Set<Integer>>> highlighterGenerator = null;
	Consumer<Object> postFunc = null;
	InstPanel parent = null;
	public int min = -10;
	public int max = 10;
	public boolean editableCount = true;

	public RandomIntegerListButton(String value, InstPanel parent) {
		this.parent = parent;
		this.setPreferredSize(new Dimension(100, 30));
		this.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					if (isEnabled()) {
						List<Integer> values = getValues();
						VisualArrayPopup vap = new VisualArrayPopup(min, max, values,
								editableCount);
						vap.linkButton(RandomIntegerListButton.this);
					}

				} else if (SwingUtilities.isRightMouseButton(e)) {
					if (isEnabled()) {
						setValue(value);
					}

				} else if (SwingUtilities.isMiddleMouseButton(e)) {
					if (e.isControlDown()) {
						boolean disableButton = !isEnabled();
						setEnabled(disableButton);
						if (e.isShiftDown()) {
							InstPanel instParent = SwingUtils
									.getInstParent(RandomIntegerListButton.this);
							instParent.getAllComponentsLike(RandomIntegerListButton.this,
									RandomIntegerListButton.class).forEach(butt -> {
										butt.setEnabled(disableButton);
										butt.repaint();
									});
						}
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

	public List<Integer> getValues() {
		if (getValue().isEmpty()) {
			return new ArrayList<>(Arrays.asList(0));
		}
		String[] valueSplit = getValue().split(",");
		List<Integer> values = new ArrayList<>();
		for (String s : valueSplit) {
			values.add(Integer.valueOf(s.trim()));

		}
		return values;
	}

	public String getValue() {
		String txt = getText();
		return "?".equals(txt) ? "" : txt;
	}

	public void setValues(List<Integer> values) {
		setValues(values, true);
	}

	public void setValues(List<Integer> values, boolean checkPost) {
		setValue((values != null) ? StringUtils.join(values, ",") : "?", checkPost);
	}

	public void setValue(String value) {
		setValue(value, true);
	}

	public void setValue(String value, boolean checkPost) {
		if (!isEnabled()) {
			return;
		}
		if (StringUtils.isEmpty(value)) {
			value = "?";
		}
		setText(value);
		if (checkPost && !"?".equals(value) && (postFunc != null)) {
			postFunc.accept(new Object());
		}
	}

	public void setPostFunc(Consumer<Object> func) {
		postFunc = func;
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
