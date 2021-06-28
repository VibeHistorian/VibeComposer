package org.vibehistorian.vibecomposer.Panels;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.vibehistorian.vibecomposer.Helpers.JKnob;

public class NumPanel extends JPanel {

	private static final long serialVersionUID = -2145278227995141172L;

	private JLabel label = null;
	private JKnob knob = null;
	boolean needToReset = false;

	public NumPanel(String name, int value) {
		this(name, value, 0, 100);
	}

	public NumPanel(String name, int value, int minimum, int maximum) {
		this(name, value, minimum, maximum, 0);
	}

	public NumPanel(String name, int value, int minimum, int maximum, int tickSpacing) {
		this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		setOpaque(false);
		label = new JLabel(name + " ");
		knob = new JKnob(minimum, maximum, value, tickSpacing);
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
}
