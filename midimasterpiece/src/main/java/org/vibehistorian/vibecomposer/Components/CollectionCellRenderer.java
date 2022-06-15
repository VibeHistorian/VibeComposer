package org.vibehistorian.vibecomposer.Components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import org.apache.commons.lang3.tuple.Triple;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class CollectionCellRenderer extends JComponent implements TableCellRenderer {

	private static final long serialVersionUID = 7080615849668597111L;

	private int height = 10;
	private int width = 10;
	private Collection<? extends Object> stringables = new ArrayList<>();
	private int part = 0;
	private int section = 0;
	public static final int MIN_CELLS = 8;
	public static final Color[] CUSTOM_PATTERN_COLORS = { VibeComposerGUI.darkModeUIColor,
			VibeComposerGUI.lightModeUIColor, new Color(150, 200, 200), Color.red };

	public CollectionCellRenderer(Collection<? extends Object> itrs, int w, int h, int partNum,
			int col) {
		stringables = itrs;
		height = h;
		width = w;
		part = partNum;
		section = col;
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		/*TableModel mdl = table.getModel();
		height = (int) ((VibeComposerGUI.scrollPaneDimension.getHeight() - 50) / mdl.getRowCount());
		width = (int) ((VibeComposerGUI.scrollPaneDimension.getWidth() - 100)
				/ mdl.getColumnCount());
		stringables = value instanceof String ? Collections.singleton((String) value)
				: (Collection<? extends Object>) value;
		section = column;*/
		//LG.d("ListCellRenderer smth.");

		return this;
	}

	@Override
	public void paintComponent(Graphics guh) {

		if (guh instanceof Graphics2D) {
			Graphics2D g = (Graphics2D) guh;
			Color panelC = VibeComposerGUI.isDarkMode ? VibeComposerGUI.panelColorLow
					: VibeComposerGUI.panelColorHigh;
			g.setColor(panelC);
			g.fillRect(0, 0, width, height);
			Color icolor = OMNI.mixColor(panelC, VibeComposerGUI.instColors[part],
					part > 0 ? 0.5 : 0.7);

			int guiPanelsCount = VibeComposerGUI.getInstList(part).size();
			int alphaValue = guiPanelsCount > 0 ? 240 : 0;

			icolor = OMNI.alphen(icolor, alphaValue);
			g.setColor(icolor);

			//g.fillRect(0, 0, width, height);
			String cellDescription = null;

			//g.setColor(OMNI.alphen(Color.black, 127));
			int widthDivider = Math.max(CollectionCellRenderer.MIN_CELLS + 1, guiPanelsCount + 1);
			double widthDividerValue = (width / (double) widthDivider);

			double x = 0;
			if (section < VibeComposerGUI.actualArrangement.getSections().size()) {
				Section sec = VibeComposerGUI.actualArrangement.getSections().get(section);
				double startX = x;
				double endX = (x + widthDividerValue);
				int counter = 0;
				for (Object o : stringables) {
					String num = o.toString();
					int partOrder = 0;
					int panelOrder = 1;
					try {
						panelOrder = Integer.valueOf(num);
						partOrder = VibeComposerGUI.getAbsoluteOrder(part, panelOrder);
					} catch (Exception e) {
						continue;
					}
					if (counter < partOrder) {
						x += (endX - startX) * (partOrder - counter);
						counter = partOrder;
					}

					startX = x;
					endX = (x + widthDividerValue);

					/*Color noteColor = c;
					if (counter > 0) {
						Color nextColor = part < 4 ? VibeComposerGUI.instColors[part + 1]
								: Color.red;
						double percentageMix = (counter
								/ (double) VibeComposerGUI.getInstList(part).size()) / 3;
					
						noteColor = OMNI.mixColor(noteColor, nextColor, percentageMix);
					}*/
					boolean isCustomMidi = sec.containsPattern(part, panelOrder)
							&& sec.getPattern(part, panelOrder).isCustom(part, panelOrder);

					Color instCellColor = OMNI.mixColor(panelC, VibeComposerGUI.instColors[part],
							part > 0 ? 0.55 : 0.7);
					if (counter > 0) {
						Color nextColor = part < 4 ? VibeComposerGUI.instColors[part + 1]
								: Color.red;
						double percentageMix = counter / (double) Math.max(counter,
								VibeComposerGUI.getInstList(part).size());

						instCellColor = OMNI.mixColor(instCellColor, nextColor, percentageMix / 3);
					}

					Color subcellColor = OMNI.mixColor(instCellColor, panelC, isCustomMidi ? 0.66
							: (1 - sec.countVariationsForPartAndOrder(part, partOrder)) * 0.66);

					// highlight copier/copiee
					if (VibeComposerGUI.copyDragging && (VibeComposerGUI.copyDraggingOrigin != null)
							&& VibeComposerGUI.copyDraggingOrigin
									.equals(Triple.of(part, partOrder, section))) {
						subcellColor = OMNI.mixColor(subcellColor, Color.green, 0.4);
					} else if (VibeComposerGUI.highlightedTableCell != null
							&& VibeComposerGUI.highlightedTableCell
									.equals(Triple.of(part, partOrder, section))) {
						subcellColor = OMNI.mixColor(subcellColor,
								VibeComposerGUI.copyDragging ? Color.red : Color.white, 0.3);
						cellDescription = VibeComposerGUI.getInstList(part).get(partOrder)
								.getInstrumentBox().getVal();
					}

					g.setColor(subcellColor);
					g.fillRect((int) startX + 1, 0, (int) (endX - startX), height);
					g.setColor(new Color(230, 230, 230));
					if (num.length() == 1) {
						g.drawString(num, (int) ((startX + endX) / 2 - 3), height / 2);
					} else {
						g.drawString(num.substring(0, 1), (int) ((startX + endX) / 2 - 3),
								10 * height / 27);
						g.drawString(num.substring(1), (int) ((startX + endX) / 2 - 3),
								10 * height / 16);
					}


					int numVars = Section.variationDescriptions[part].length - 2;
					double moveX = (endX - startX) / (numVars);

					if (isCustomMidi) {
						g.setColor(OMNI.alphen(
								CUSTOM_PATTERN_COLORS[sec.getPattern(part, panelOrder).getType()],
								150));
						g.fillRect((int) startX + 2, 0, (int) widthDividerValue - 4, 5);
					}

					g.setColor(new Color(230, 230, 230, 200));
					List<Integer> actualVars = sec.getVariation(part, partOrder);
					for (int i = 0; i < numVars; i++) {
						int varX = (int) (startX + ((moveX * i) + 1));
						if (actualVars.contains(i)) {
							if (isCustomMidi) {
								if (!Section.variationDescriptions[part][i + 2]
										.startsWith("Transpose")) {
									continue;
								}
							}
							g.drawLine(varX, 0, varX, 3);
						}
					}


					g.setColor(Color.black);
					g.drawLine((int) startX, 0, (int) startX, height);
					g.drawLine((int) endX, 0, (int) endX, height);
					x = endX;
					counter++;
				}
				x = 0;

				g.setColor(OMNI.alphen(Color.black, 45));
				for (int i = 0; i < guiPanelsCount; i++) {
					startX = x;
					endX = (x + widthDividerValue);
					g.drawLine((int) startX, 0, (int) startX, height);
					g.drawLine((int) endX, 0, (int) endX, height);
					x = endX;
				}
				g.setColor(OMNI.alphen(Color.black, 60));
				startX = width - widthDividerValue;
				g.drawLine((int) startX, 0, (int) startX, height);
				endX = width;
				g.setColor(OMNI.alphen(new Color(230, 230, 230), 15));
				g.fillRect((int) startX, 0, (int) widthDividerValue, height);
				g.setColor(OMNI.alphen(Color.black, 100));
				g.drawString("?", (int) ((startX + endX) / 2 - 3), height / 2);


				if (sec.getInstPartList(part) != null) {
					g.setColor(OMNI.alphen(Color.black, 100));
					g.fillRect(0, 2 + height / 2, (int) endX, height / 5 - 2);
				}
			}


			//String value = StringUtils.join(stringables, ";");
			//g.drawString(value, 2, height / 2);
			//LG.d("ListCellRenderer RENDERED");
			g.setColor(Color.black);
			g.drawRect(0, 0, width, height);

			g.setColor(new Color(210, 210, 210));
			if (cellDescription != null
					&& VibeComposerGUI.arrangementActualTableMousePoint != null) {
				g.drawString(cellDescription,
						width / 2 - SwingUtils.getDrawStringWidth(cellDescription) / 2, 10);
			}

			g.dispose();
		}
	}

}
