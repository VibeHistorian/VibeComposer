package org.vibehistorian.vibecomposer.Components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class SectionInfoCellRenderer extends JComponent implements TableCellRenderer {

	private static final long serialVersionUID = 7080615849668597111L;

	private int height = 10;
	private int width = 10;
	private int section = 0;

	private static final int fontSize = 8;
	private static final float fontSizeMin = 7f;
	private static final Font font = new Font("Tahoma", Font.PLAIN, fontSize);

	public SectionInfoCellRenderer(int w, int h, int col) {
		height = h;
		width = w;
		section = col;
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {

		return this;
	}

	@Override
	public void paintComponent(Graphics guh) {

		if (guh instanceof Graphics2D) {
			Graphics2D g = (Graphics2D) guh;
			if (section < VibeComposerGUI.actualArrangement.getSections().size()) {
				Section sec = VibeComposerGUI.actualArrangement.getSections().get(section);
				g.setColor(new Color(100 + 15 * sec.getTypeMelodyOffset(), 150, 150));
				g.fillRect(0, 0, width, height);

				g.setColor(new Color(230, 230, 230));
				g.drawString("" + sec.getMeasures(), 3, height / 2);

				String customDurations = sec.isCustomDurationsEnabled()
						? sec.getCustomDurations().replaceAll(" ", "")
						: "";
				String customChords = ((sec.isCustomChordsEnabled()
						|| sec.isDisplayAlternateChords())
								? sec.getCustomChords().replaceAll(" ", "")
								: "");
				String guiUserChords = (VibeComposerGUI.userChordsEnabled.isSelected()
						? VibeComposerGUI.userChords.getChordListString()
						: StringUtils.join(MidiGenerator.chordInts, ",")).replaceAll(" ", "");

				String guiUserDurations = (VibeComposerGUI.userDurationsEnabled.isSelected()
						? VibeComposerGUI.userChordsDurations.getText()
						: "4,4,4,4").replaceAll(" ", "");

				if (customChords.trim().isEmpty() || guiUserChords.equalsIgnoreCase(customChords)) {
					customChords = "";
				} else {
					float newSize = Float.valueOf(fontSize * Math.min(width, 100)
							/ Math.max(60, SwingUtils.getDrawStringWidth(customChords)));
					g.setFont(font.deriveFont(newSize < fontSizeMin ? fontSizeMin : newSize));
					g.drawString("[" + customChords + "]", 12, height / 4);
				}

				if (customDurations.trim().isEmpty() || customDurations.equals(guiUserDurations)) {
					customDurations = "";
				} else {
					float newSize = Float.valueOf(fontSize * Math.min(width, 100)
							/ Math.max(60, SwingUtils.getDrawStringWidth(customDurations)));
					g.setFont(font.deriveFont(newSize < fontSizeMin ? fontSizeMin : newSize));
					g.drawString("(" + customDurations + ")", 12, height * 3 / 5);
				}
			}

			g.setColor(Color.black);
			g.drawRect(0, 0, width, height);
			g.dispose();
		}
	}

}
