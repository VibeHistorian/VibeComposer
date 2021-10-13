package org.vibehistorian.vibecomposer.Panels;

public class DetachedKnobPanel extends KnobPanel {

	private static final long serialVersionUID = 2815663844185760479L;

	public DetachedKnobPanel(String name, int value) {
		super(name, value);
		setRegenerating(false);
	}

	public DetachedKnobPanel(String name, int value, int minimum, int maximum, int tickSpacing) {
		super(name, value, minimum, maximum, tickSpacing);
		setRegenerating(false);
	}

	public DetachedKnobPanel(String name, int value, int minimum, int maximum) {
		super(name, value, minimum, maximum);
		setRegenerating(false);
	}

}
