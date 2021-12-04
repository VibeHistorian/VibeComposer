package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.List;

import javax.swing.JComponent;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class MultiValueEditArea extends JComponent {

	private static final long serialVersionUID = -2972572935738976623L;
	int min = -10;
	int max = 10;
	int numValues = 4;
	List<Integer> values = null;
	int markWidth = 6;
	int numHeight = 6;
	int numWidth = 4;

	public MultiValueEditArea(int min, int max, int numValues, List<Integer> values) {
		super();
		this.min = min;
		this.max = max;
		this.numValues = numValues;
		this.values = values;
	}


	@Override
	public void paintComponent(Graphics guh) {
		if (guh instanceof Graphics2D) {
			Graphics2D g = (Graphics2D) guh;
			int w = getWidth();
			int h = getHeight();
			int colStart = 2;
			int colDivisors = numValues + colStart;
			double colWidth = w / (double) colDivisors;
			int rowStart = 4;
			int rowDivisors = max - min + rowStart;
			double rowHeight = h / (double) rowDivisors;
			// clear screen
			g.setColor(VibeComposerGUI.panelColorHigh);
			g.fillRect(0, 0, w, h);

			// draw graph lines - first to last value X, min to max value Y
			g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), 80));

			Point bottomLeft = new Point((int) colWidth * (colStart - 1),
					(int) rowHeight * (rowDivisors - 1));
			g.drawLine(bottomLeft.x, bottomLeft.y, bottomLeft.x, 0);
			g.drawLine(bottomLeft.x, bottomLeft.y, w, bottomLeft.y);

			// draw numbers left of Y line
			// draw line marks

			for (int i = 0; i < 1 + (max - min); i++) {

				String drawnValue = "" + (min + i);
				int valueLength = drawnValue.startsWith("-") ? drawnValue.length() + 1
						: drawnValue.length();
				int drawValueX = bottomLeft.x / 2 - (numWidth * valueLength) / 2;
				int drawMarkX = bottomLeft.x - markWidth / 2;
				int drawY = bottomLeft.y - (int) (rowHeight * (i + 1));

				g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), 40));
				g.drawLine(bottomLeft.x, drawY, w, drawY);

				g.setColor(VibeComposerGUI.uiColor());
				g.drawString(drawnValue, drawValueX, drawY + numHeight / 2);
				g.drawLine(drawMarkX, drawY, drawMarkX + markWidth, drawY);


			}

			// draw numbers below X line
			// draw line marks
			for (int i = 0; i < numValues; i++) {
				String drawnValue = "" + (i + 1);
				int valueLength = drawnValue.startsWith("-") ? drawnValue.length() + 1
						: drawnValue.length();
				int drawValueY = numHeight + (bottomLeft.y + h) / 2;
				int drawMarkY = (bottomLeft.y - markWidth / 2);
				int drawX = bottomLeft.x + (int) (colWidth * (i + 1));

				g.drawString(drawnValue, drawX - (numWidth * valueLength) / 2, drawValueY);
				g.drawLine(drawX, drawMarkY, drawX, drawMarkY + markWidth);


			}
			g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), 80));

			// draw line helpers/dots
			for (int i = 0; i < numValues; i++) {
				int drawX = bottomLeft.x + (int) (colWidth * (i + 1));
				for (int j = 0; j < 1 + max - min; j++) {
					int drawDotY = bottomLeft.y - (int) (rowHeight * (j + 1));
					g.drawLine(drawX, drawDotY - 2, drawX, drawDotY + 2);
				}
			}
		}
	}


	public List<Integer> getValues() {
		return values;
	}
}
