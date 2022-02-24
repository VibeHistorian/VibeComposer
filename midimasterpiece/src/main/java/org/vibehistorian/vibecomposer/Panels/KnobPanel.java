package org.vibehistorian.vibecomposer.Panels;

import java.awt.Dimension;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;

import org.vibehistorian.vibecomposer.Components.JKnob;
import org.vibehistorian.vibecomposer.Components.LockComponentButton;

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

		if (minimum != 0) {
			knob.setAllowValuesOutsideRange(true);
		}
		setMaximumSize(new Dimension(200, 50));
		add(label);
		JLayeredPane knobLockPane = new JLayeredPane();
		knobLockPane.setPreferredSize(new Dimension(40, 40));
		knobLockPane.setOpaque(false);
		knobLockPane.add(knob);
		LockComponentButton lockButt = new LockComponentButton(knob);
		knobLockPane.add(lockButt);
		knobLockPane.setComponentZOrder(knob, Integer.valueOf(1));
		knobLockPane.setComponentZOrder(lockButt, Integer.valueOf(0));
		lockButt.setBounds(33, 32, 8, 8);
		add(knobLockPane);
		knob.repaint();
	}


	public String getName() {
		return label.getText();
	}

	public int getInt() {
		return knob.updateAndGetValue();
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

	public void setBlockInput(boolean b) {
		knob.setEnabled(!b);
	}

	public void setRegenerating(boolean b) {
		knob.setRegenerating(b);
	}

}
