package org.vibehistorian.vibecomposer.Components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Helpers.UsedPattern;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Popups.PatternManagerPopup;

public class MidiMVI extends JComponent {

	private static final long serialVersionUID = 2226722368743495710L;

	public static final String[] BUTTONS = { "M", "V", "I", "C" };
	public static final int BUTTON_WIDTH = 15;
	public static final Dimension DEFAULT_SIZE = new Dimension(BUTTON_WIDTH * BUTTONS.length, 25);
	Dimension defaultSize = DEFAULT_SIZE;
	InstPanel parent;

	public MidiMVI() {
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent evt) {
				if (!isEnabled() || VibeComposerGUI.guiConfig.getPatternMaps().isEmpty()) {
					return;
				}
				int butt = getButton(evt);
				if (butt < 0) {
					return;
				}

				LG.i("Button: " + BUTTONS[butt]);
				if (SwingUtilities.isLeftMouseButton(evt)) {
					processButton(butt);
				} else if (SwingUtilities.isRightMouseButton(evt)) {
					boolean activate = !isActive(butt);
					for (int i = 0; i < 2; i++) {
						PatternManagerPopup.toggle(parent.getPartNum(), parent.getPanelOrder(),
								UsedPattern.BASE_PATTERNS[i + 1], activate);
					}
					PatternManagerPopup.unapply2(3, parent.getPartNum(), parent.getPanelOrder(),
							activate);
				}
				repaint();
			}

			private void processButton(int butt) {
				if (butt <= 2) {
					PatternManagerPopup.toggle(parent.getPartNum(), parent.getPanelOrder(),
							UsedPattern.BASE_PATTERNS[butt + 1], null);
				} else {
					PatternManagerPopup.unapply2(3, parent.getPartNum(), parent.getPanelOrder(),
							false);
				}
			}
		});
		setOpaque(true);
		updateSizes(defaultSize);
	}

	public void setupParent(InstPanel parent) {
		this.parent = parent;
	}

	@Override
	protected void paintComponent(Graphics guh) {
		if (guh instanceof Graphics2D) {
			Graphics2D g = (Graphics2D) guh;
			g.setColor(VibeComposerGUI.isDarkMode ? new Color(100, 100, 100)
					: new Color(180, 180, 180));
			int width = getWidth();
			int height = getHeight();
			g.fillRect(0, 0, width, height);

			for (int i = 0; i < BUTTONS.length; i++) {
				boolean active = isActive(i);
				g.setColor(OMNI.alphen(CollectionCellRenderer.CUSTOM_PATTERN_COLORS[i],
						active ? 190 : 50));
				int startX = i * BUTTON_WIDTH + 1;

				g.fillRect(startX, 0, BUTTON_WIDTH, height);
				g.setColor(Color.white);
				g.drawString(BUTTONS[i],
						startX + BUTTON_WIDTH / 2 - SwingUtils.getDrawStringWidth(BUTTONS[i]) / 2,
						height * 2 / 3);
			}

			g.setColor(Color.black);
			g.drawRect(0, 0, width, height);
		}
	}

	private boolean isActive(int i) {
		if (VibeComposerGUI.guiConfig.getPatternMaps().isEmpty()) {
			return false;
		}

		if (i <= 2) {
			PhraseNotes pn = VibeComposerGUI.guiConfig.getPatternRaw(parent.getPartNum(),
					parent.getPanelOrder(), UsedPattern.BASE_PATTERNS[i + 1]);
			return (pn != null) && pn.isApplied();
		} else {
			Set<String> patternNames = VibeComposerGUI.guiConfig.getPatternMaps()
					.get(parent.getPartNum()).getPatternNames(parent.getPanelOrder());
			if (patternNames == null) {
				return false;
			}
			for (String bp : UsedPattern.BASE_PATTERNS) {
				patternNames.remove(bp);
			}
			for (String name : patternNames) {
				PhraseNotes pn = VibeComposerGUI.guiConfig.getPatternRaw(parent.getPartNum(),
						parent.getPanelOrder(), name);
				if (pn != null && pn.isApplied()) {
					return true;
				}

			}
			return false;
		}
	}

	private int getButton(MouseEvent evt) {
		if (!OMNI.mouseInComp(this)) {
			return -1;
		}

		return OMNI.clamp(evt.getPoint().x / BUTTON_WIDTH, 0, BUTTONS.length - 1);
	}

	public void updateSizes(Dimension size) {
		setPreferredSize(size);
		setMinimumSize(size);
		setMaximumSize(size);
		setSize(size);
	}

	public void setDefaultSize(Dimension size) {
		defaultSize = size;
	}
}
