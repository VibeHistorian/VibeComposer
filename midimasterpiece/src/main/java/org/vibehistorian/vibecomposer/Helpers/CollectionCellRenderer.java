package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class CollectionCellRenderer extends JComponent implements TableCellRenderer {

	private static final long serialVersionUID = 7080615849668597111L;

	private int height = 10;
	private int width = 10;
	private Collection<? extends Object> stringables = new ArrayList<>();
	private int part = 0;
	private int section = 0;

	public CollectionCellRenderer(Collection<? extends Object> itrs, int w, int h, int partNum,
			int col) {
		stringables = itrs;
		height = h;
		width = w;
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				System.out.println("YEP");

			}
		});
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
		//System.out.println("ListCellRenderer smth.");

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
			Color c = panelC;
			c = OMNI.mixColor(c, VibeComposerGUI.instColors[part], part > 0 ? 0.5 : 0.7);

			int guiPanelsCount = VibeComposerGUI.getInstList(part).size();
			int alphaValue = 0;
			if (guiPanelsCount > 0) {
				alphaValue = 240;
			}
			c = OMNI.alphen(c, alphaValue);
			g.setColor(c);

			//g.fillRect(0, 0, width, height);

			//g.setColor(OMNI.alphen(Color.black, 127));
			int widthDivider = Math.max(8, guiPanelsCount);

			double x = 0;
			Color mixC = Color.white;
			if (section < VibeComposerGUI.actualArrangement.getSections().size()) {
				Section sec = VibeComposerGUI.actualArrangement.getSections().get(section);
				double startX = x;
				double endX = (x + (width / (double) widthDivider));
				int counter = 0;
				for (Object o : stringables) {
					String num = o.toString();
					int partOrder = 0;
					try {
						partOrder = VibeComposerGUI.getAbsoluteOrder(part, Integer.valueOf(num));
					} catch (Exception e) {
						continue;
					}
					if (counter < partOrder) {
						x += (endX - startX) * (partOrder - counter);
						counter = partOrder;
					}

					startX = x;
					endX = (x + (width / (double) widthDivider));

					g.setColor(OMNI.mixColor(c, panelC,
							(1 - sec.countVariationsForPartAndOrder(part, partOrder)) / 1.5));
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
					endX = (x + (width / (double) widthDivider));
					g.drawLine((int) startX, 0, (int) startX, height);
					g.drawLine((int) endX, 0, (int) endX, height);
					x = endX;
				}

				if (sec.getInstPartList(part) != null) {
					g.setColor(OMNI.alphen(Color.black, 100));
					g.fillRect(0, 2 + height / 2, (int) endX, height / 5 - 2);
				}
			}


			//String value = StringUtils.join(stringables, ";");
			//g.drawString(value, 2, height / 2);
			//System.out.println("ListCellRenderer RENDERED");
			g.setColor(Color.black);
			g.drawRect(0, 0, width, height);
			g.dispose();
		}
	}

}
