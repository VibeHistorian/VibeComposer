package org.vibehistorian.vibecomposer.Popups;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.table.JTableHeader;

import org.vibehistorian.vibecomposer.Arrangement;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.PartInclusionBooleanTableModel;
import org.vibehistorian.vibecomposer.Panels.TransparentablePanel;

public class ArrangementPartInclusionPopup extends CloseablePopup {

	public static final String[] ENERGY_LEVELS = new String[] { "#", "ALL", "MAIN", "VERSE",
			"INST" };
	public static final Integer[] ENERGY_WEIGHTS = new Integer[] { 50, 50, 50, 50 };

	JPanel tablesPanel = new JPanel();
	JTable[] tables = new JTable[5];

	JScrollPane scroll;

	public ArrangementPartInclusionPopup(Arrangement arr) {
		super("Arrangement - Part Inclusion", 10, new Point(-500, -600));
		tablesPanel.setLayout(new BoxLayout(tablesPanel, BoxLayout.Y_AXIS));
		tablesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		PopupUtils.addEmptySpaceCloser(tablesPanel, frame);

		addPartInclusionButtons(arr);


		for (int i = 0; i < 5; i++) {
			int fI = i;

			JTable table = new JTable();
			table.setAlignmentX(Component.LEFT_ALIGNMENT);
			if (arr.getPartInclusionMap() == null) {
				arr.initPartInclusionMap();
			}
			if (arr.getPartInclusionMap().get(i) == null) {
				arr.initPartInclusionMap();
			}

			List<String> partNames = VibeComposerGUI.getInstList(i).stream()
					.map(e -> (e.getInstrumentBox().getVal()).split(": ")[1])
					.collect(Collectors.toList());

			table.setModel(new PartInclusionBooleanTableModel(fI, arr.getPartInclusionMap().get(i),
					ENERGY_LEVELS, partNames));
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
		LG.d("Opened arrangement part inclusion page!");
	}

	private void addPartInclusionButtons(Arrangement arr) {
		JButton piRandomizer = new JButton("Randomize");

		piRandomizer.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				randomizePartInclusions(arr);
			}

		});

		JButton piFiller = new JButton("Fill All");

		piFiller.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				emptyPartInclusions();
				fillEmptyPartInclusions();
			}

		});

		tablesPanel.add(piRandomizer);
		tablesPanel.add(piFiller);

		//JTextField weights = new JTextField("50,35,")

	}

	public void randomizePartInclusions(Arrangement arr) {
		randomizePartInclusions(arr, null);
	}

	public void randomizePartInclusions(Arrangement arr, Integer seed) {
		Random piRand = new Random();
		if (seed != null) {
			piRand.setSeed(seed);
		}
		emptyPartInclusions();
		for (int i = 0; i < 5; i++) {
			JTable tbl = tables[i];
			for (int j = 0; j < tbl.getRowCount(); j++) {
				for (int k = 0; k < ENERGY_WEIGHTS.length; k++) {
					if (piRand.nextInt(100) < ENERGY_WEIGHTS[k]) {
						tbl.getModel().setValueAt(Boolean.TRUE, j, k + 1);
					}
				}
			}
		}
		fillEmptyPartInclusions();
	}

	public void emptyPartInclusions() {
		for (int i = 0; i < 5; i++) {
			JTable tbl = tables[i];
			for (int j = 0; j < tbl.getRowCount(); j++) {
				for (int k = 0; k < ENERGY_WEIGHTS.length; k++) {
					tbl.getModel().setValueAt(Boolean.FALSE, j, k + 1);
				}
			}
		}
	}

	public void fillEmptyPartInclusions() {
		for (int i = 0; i < 5; i++) {
			JTable tbl = tables[i];
			for (int j = 0; j < tbl.getRowCount(); j++) {
				boolean isEmpty = true;
				for (int k = 0; k < ENERGY_WEIGHTS.length; k++) {
					isEmpty &= !((Boolean) tbl.getModel().getValueAt(j, k + 1));
				}
				if (isEmpty) {
					tbl.getModel().setValueAt(Boolean.TRUE, j, 1);
				}
			}
		}
		JTable melodyTbl = tables[0];
		for (int j = 0; j < melodyTbl.getRowCount(); j++) {
			melodyTbl.getModel().setValueAt(Boolean.TRUE, j, 2);
		}

		JTable bassTbl = tables[1];
		for (int j = 0; j < bassTbl.getRowCount(); j++) {
			bassTbl.getModel().setValueAt(Boolean.TRUE, j, 2);
		}


		JTable drumTbl = tables[4];
		for (int j = 0; j < drumTbl.getRowCount(); j++) {
			String name = (String) drumTbl.getValueAt(j, 0);
			if (name.contains("KICK") || name.contains("SNARE")) {
				drumTbl.getModel().setValueAt(Boolean.TRUE, j, 2);
			}
		}
	}

	protected void addFrameWindowOperation() {
		frame.addWindowListener(CloseablePopup.EMPTY_WINDOW_LISTENER);
	}
}
