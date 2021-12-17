package org.vibehistorian.vibecomposer.Components;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.Arrangement;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class SectionDropDownCheckButton extends CheckButton {

	private static final long serialVersionUID = -5179430651277878280L;
	private List<String> dropDownOptions = null;
	public static int popupIndex = 0;

	public SectionDropDownCheckButton(String name, boolean sel, Color alphen) {
		super(name, sel, alphen);

		dropDownOptions = new ArrayList<>();
		Arrangement.defaultSections.keySet().forEach(e -> dropDownOptions.add(e));
		final JPopupMenu popup = new JPopupMenu();
		for (int i = 0; i < dropDownOptions.size(); i++) {
			String e = dropDownOptions.get(i);
			JMenuItem newE = new JMenuItem(e);
			newE.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent evt) {
					VibeComposerGUI.vibeComposerGUI
							.handleArrangementAction("ArrangementAddNewSection," + e, 0, 0);
					LG.d("popupindex: " + popupIndex);
				}
			});
			popup.add(newE);
		}

		addMouseListener(new MouseAdapter() {

			@Override
			public void mousePressed(MouseEvent evt) {
				if (SwingUtilities.isRightMouseButton(evt) && dropDownOptions != null) {
					if (Character.isDigit(SectionDropDownCheckButton.this.getText().charAt(0))) {
						// not global
						String digits = SectionDropDownCheckButton.this.getText().split(":")[0];
						Integer index = Integer.valueOf(digits);
						popupIndex = index;
					} else {
						// global/first
						popupIndex = 0;
					}
					popup.show(evt.getComponent(), evt.getX(), evt.getY());
				}
			}

		});
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
