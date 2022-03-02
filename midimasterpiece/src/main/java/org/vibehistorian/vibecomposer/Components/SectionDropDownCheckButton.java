package org.vibehistorian.vibecomposer.Components;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.Arrangement;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class SectionDropDownCheckButton extends CheckButton {

	private static final long serialVersionUID = -5179430651277878280L;
	private List<String> dropDownOptions = null;
	public static int popupIndex = 0;

	public SectionDropDownCheckButton(String name, boolean sel, Color alphen) {
		super(name, sel, alphen);

		dropDownOptions = new ArrayList<>();
		Arrangement.defaultSections.keySet().forEach(e -> dropDownOptions.add(e));

		SwingUtils.addPopupMenu(this, (evt, e) -> {
			VibeComposerGUI.vibeComposerGUI.handleArrangementAction("ArrangementAddNewSection," + e,
					0, 0);
			LG.d("popupindex: " + popupIndex);
		}, e -> {
			if (SwingUtilities.isRightMouseButton(e) && dropDownOptions != null) {
				if (Character.isDigit(SectionDropDownCheckButton.this.getText().charAt(0))) {
					// not global
					String digits = SectionDropDownCheckButton.this.getText().split(":")[0];
					Integer index = Integer.valueOf(digits);
					popupIndex = index;
				} else {
					// global/first
					popupIndex = 0;
				}
				return true;
			}
			return false;
		}, dropDownOptions, null);
	}

	public SectionDropDownCheckButton(String name, boolean sel) {
		this(name, sel, null);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		g.setColor(Color.black);
		//g.drawRect(0, 0, getWidth(), getHeight());
		//g.fillRect(getWidth() - 7, getHeight() - 4, 7, 4);
		int width = getWidth();
		int height = getHeight();
		g.fillPolygon(new int[] { width - 7, width - 1, width - 4 },
				new int[] { height - 4, height - 4, height - 1 }, 3);
	}
}
