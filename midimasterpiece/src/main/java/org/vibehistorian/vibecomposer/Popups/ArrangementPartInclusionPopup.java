package org.vibehistorian.vibecomposer.Popups;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.table.JTableHeader;

import org.vibehistorian.vibecomposer.Arrangement;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.OMNI;
import org.vibehistorian.vibecomposer.Helpers.PartInclusionBooleanTableModel;
import org.vibehistorian.vibecomposer.Panels.TransparentablePanel;

public class ArrangementPartInclusionPopup extends CloseablePopup {

	public static final String[] ENERGY_LEVELS = new String[] { "#", "ALL", "MAIN", "MELODIC",
			"INSTRUMENTAL" };

	final JFrame frame = new JFrame();
	JPanel tablesPanel = new JPanel();
	JTable[] tables = new JTable[5];

	JScrollPane scroll;

	public ArrangementPartInclusionPopup(Arrangement arr, Point parentLoc, Dimension parentDim) {
		super("Arrangement - Part Inclusion", 10);
		tablesPanel.setLayout(new BoxLayout(tablesPanel, BoxLayout.Y_AXIS));
		tablesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		for (int i = 0; i < 5; i++) {
			int fI = i;

			JTable table = new JTable();
			table.setAlignmentX(Component.LEFT_ALIGNMENT);

			if (arr.getPartEnergyInclusionMap().get(i) == null) {
				arr.initPartEnergyInclusionMap();
			}

			List<String> partNames = VibeComposerGUI.getInstList(i).stream()
					.map(e -> (e.getInstrumentBox().getVal()).split(" = ")[0])
					.collect(Collectors.toList());

			table.setModel(new PartInclusionBooleanTableModel(fI,
					arr.getPartEnergyInclusionMap().get(i), ENERGY_LEVELS, partNames));
			table.setRowSelectionAllowed(false);
			table.setColumnSelectionAllowed(false);
			table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

			tables[i] = table;
			TransparentablePanel categoryPanel = new TransparentablePanel();
			categoryPanel.setLayout(new BoxLayout(categoryPanel, BoxLayout.X_AXIS));
			categoryPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			categoryPanel.setMaximumSize(new Dimension(2000, 40));
			categoryPanel.setBorder(new BevelBorder(BevelBorder.RAISED));
			JPanel categoryButtons = new JPanel();
			JLabel categoryName = new JLabel(VibeComposerGUI.instNames[i].toUpperCase());
			categoryName.setAlignmentX(Component.LEFT_ALIGNMENT);
			categoryName.setFont(new Font("Arial", Font.BOLD, 13));
			categoryName.setBorder(new BevelBorder(BevelBorder.RAISED));
			categoryPanel.add(categoryName);
			categoryPanel.addBackground(OMNI.alphen(VibeComposerGUI.instColors[i], 50));

			categoryButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
			categoryPanel.add(categoryButtons);
			tablesPanel.add(categoryPanel);
			JTableHeader header = tables[i].getTableHeader();
			header.setReorderingAllowed(false);
			header.setResizingAllowed(false);
			header.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					int col = table.columnAtPoint(e.getPoint());
					if (col == 0)
						return;

					if (SwingUtilities.isLeftMouseButton(e)) {
						for (int k = 0; k < VibeComposerGUI.getInstList(fI).size(); k++) {
							table.getModel().setValueAt(Boolean.TRUE, k, col);

							//sec.resetPresence(fI, j);
						}
					} else if (SwingUtilities.isRightMouseButton(e)) {
						for (int k = 0; k < VibeComposerGUI.getInstList(fI).size(); k++) {
							table.getModel().setValueAt(Boolean.FALSE, k, col);
							//sec.resetPresence(fI, j);
						}
					}
					table.repaint();
				}
			});
			header.setAlignmentX(Component.LEFT_ALIGNMENT);
			tablesPanel.add(header);
			tablesPanel.add(tables[i]);
		}

		scroll = new JScrollPane(tablesPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		//int heightLimit = 850;
		//frame.setPreferredSize(new Dimension(650, Math.min(parentDim.height, heightLimit)));
		frame.add(scroll);
		frame.pack();
		frame.setVisible(true);
		System.out.println("Opened arrangement part inclusion page!");
	}

	@Override
	protected void addFrameWindowOperation() {
		// TODO Auto-generated method stub

	}
}
