package org.vibehistorian.vibecomposer.Panels;

import java.awt.Dimension;

import javax.swing.BoxLayout;
import javax.swing.JLabel;

import org.vibehistorian.vibecomposer.Helpers.JKnob;

public class KnobPanel extends TransparentablePanel {

	private static final long serialVersionUID = -2145278227995141172L;

	private JLabel label = null;
	private JKnob knob = null;
	boolean needToReset = false;
	boolean showTextInKnob = false;
	String name = "";

	public KnobPanel(String name, int value) {
		this(name, value, 0, 100);
	}

	public KnobPanel(String name, int value, int minimum, int maximum) {
		this(name, value, minimum, maximum, 0);
	}

	public KnobPanel(String name, int value, int minimum, int maximum, int tickSpacing) {
		this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

		setOpaque(false);
		if (name.contains("<br>")) {
			name = name.replaceAll("<br>", "&nbsp;<br>");
		}
		this.name = name;
		label = new JLabel("<html>" + name + "&nbsp;</html>");
		knob = new JKnob(minimum, maximum, value, tickSpacing);
		knob.setName(name);
		setMaximumSize(new Dimension(200, 50));
		add(label);
		add(knob);
	}


	public String getName() {
		return label.getText();
	}

	public int getInt() {
		return knob.getValue();
	}

	public void setInt(int val) {
		knob.setValue(val);
	}

	public JKnob getKnob() {
		return knob;
	}

	public boolean isShowTextInKnob() {
		return showTextInKnob;
	}

	public void setShowTextInKnob(boolean showTextInKnob) {
		this.showTextInKnob = showTextInKnob;
		knob.setShowTextInKnob(showTextInKnob);
		if (showTextInKnob) {
			label.setVisible(false);
		} else {
			label.setVisible(true);
		}
	}

}
