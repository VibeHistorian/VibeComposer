package org.vibehistorian.vibecomposer.Components;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.GeneratedChordState;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.gui.ChordGUI;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.function.Supplier;

public class SectionInfoCellRenderer extends JComponent implements TableCellRenderer {

	private static final long serialVersionUID = 7080615849668597111L;

	private int height = 10;
	private int width = 10;
	private int section = 0;
	private final ChordGUI chordGUI;
	private final Supplier<ArrangementTableRenderState> getArrangementRenderState;

	private static final int fontSize = 8;
	private static final float fontSizeMin = 7f;
	private static final Font font = new Font("Tahoma", Font.PLAIN, fontSize);

	public SectionInfoCellRenderer(int w, int h, int col, ChordGUI chordGUI,
			Supplier<ArrangementTableRenderState> getArrangementRenderState) {
		height = h;
		width = w;
		section = col;
		this.chordGUI = chordGUI;
		this.getArrangementRenderState = getArrangementRenderState;
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
			ArrangementTableRenderState arrangementState = getArrangementRenderState.get();
			if (section < arrangementState.getArrangement().getSections().size()) {
				Section sec = arrangementState.getArrangement().getSections().get(section);
				g.setColor(new Color(100 + 15 * sec.getTypeMelodyOffset(), 150, 150));
				g.fillRect(0, 0, width, height);

				g.setColor(new Color(230, 230, 230));
				g.drawString("" + sec.getMeasures(), 3, height / 2);

				String customDurations = sec.isCustomDurationsEnabled()
						? sec.getCustomDurations().replace(" ", "")
						: "";
				String customChords = ((sec.isCustomChordsEnabled()
						|| sec.isDisplayAlternateChords())
								? sec.getCustomChords().replace(" ", "")
								: "");
				String guiUserChords = (chordGUI.userChordsEnabled.isSelected()
						? chordGUI.userChords.getChordListString()
						: StringUtils.join(GeneratedChordState.getChordNames(), ",")).replaceAll(" ", "");

				String guiUserDurations = (chordGUI.userDurationsEnabled.isSelected()
						? chordGUI.userChordsDurations.getText()
						: "4,4,4,4").replace(" ", "");

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
