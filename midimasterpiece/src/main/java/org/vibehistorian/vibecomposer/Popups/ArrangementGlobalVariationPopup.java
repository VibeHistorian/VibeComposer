package org.vibehistorian.vibecomposer.Popups;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.BevelBorder;
import javax.swing.table.JTableHeader;

import org.vibehistorian.vibecomposer.Arrangement;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.GlobalVariationBooleanTableModel;
import org.vibehistorian.vibecomposer.Panels.TransparentablePanel;

public class ArrangementGlobalVariationPopup extends CloseablePopup {
	JPanel tablesPanel = new JPanel();
	JTable[] tables = new JTable[6];

	JScrollPane scroll;

	public ArrangementGlobalVariationPopup(Arrangement arr) {
		super("Arrangement - Variation Selection", 10, new Point(-400, -500));
		tablesPanel.setLayout(new BoxLayout(tablesPanel, BoxLayout.Y_AXIS));
		tablesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		PopupUtils.addEmptySpaceCloser(tablesPanel, frame);

		//addPartInclusionButtons(arr);

		arr.verifyGlobalVariations();

		for (int i = 0; i < 6; i++) {
			JTable table = new JTable();
			table.setAlignmentX(Component.LEFT_ALIGNMENT);
			String[] colNames = null;
			if (i < 5) {

				colNames = new String[Section.variationDescriptions[i].length - 1];
				colNames[0] = "ALL";
				for (int j = 1; j < colNames.length; j++) {
					colNames[j] = Section.variationDescriptions[i][j + 1];
				}
			} else {

				colNames = new String[Section.sectionVariationNames.length + 1];
				colNames[0] = "ALL";
				for (int j = 1; j < colNames.length; j++) {
					colNames[j] = Section.sectionVariationNames[j - 1];
				}
			}

			table.setModel(new GlobalVariationBooleanTableModel(arr.getGlobalVariationMap().get(i),
					colNames));
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
			String name = (i < 5) ? VibeComposerGUI.instNames[i].toUpperCase()
					: "SECTION VARIATIONS";
			JLabel categoryName = new JLabel(name);
			categoryName.setAlignmentX(Component.LEFT_ALIGNMENT);
			categoryName.setFont(new Font("Arial", Font.BOLD, 13));
			categoryName.setBorder(new BevelBorder(BevelBorder.RAISED));
			categoryPanel.add(categoryName);
			Color categoryColor = (i < 5) ? VibeComposerGUI.instColors[i] : Color.cyan;
			categoryPanel.addBackground(OMNI.alphen(categoryColor, 50));

			categoryButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
			categoryPanel.add(categoryButtons);
			tablesPanel.add(categoryPanel);
			JTableHeader header = tables[i].getTableHeader();
			header.setReorderingAllowed(false);
			header.setResizingAllowed(false);
			/*header.addMouseListener(new MouseAdapter() {
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
			});*/
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
		LG.d("Opened arrangement global variation page!");
	}

	/*private void addPartInclusionButtons(Arrangement arr) {
		JButton piRandomizer = new JButton("Randomize");
	
		piRandomizer.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				//randomizePartInclusions(arr);
			}
	
		});
	
		JButton piFiller = new JButton("Fill All");
	
		piFiller.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				//emptyPartInclusions();
				//fillEmptyPartInclusions();
			}
	
		});
	
		tablesPanel.add(piRandomizer);
		tablesPanel.add(piFiller);
	}*/

	@Override
	protected void addFrameWindowOperation() {
		frame.addWindowListener(CloseablePopup.EMPTY_WINDOW_LISTENER);
	}
}
