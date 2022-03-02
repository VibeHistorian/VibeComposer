package org.vibehistorian.vibecomposer.Popups;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Components.MultiValueEditArea;
import org.vibehistorian.vibecomposer.Components.RandomIntegerListButton;

public class VisualArrayPopup extends CloseablePopup {

	MultiValueEditArea mvea = null;
	RandomIntegerListButton butt = null;
	JTextField text = null;
	Consumer<Object> closeFunc = null;

	public VisualArrayPopup(int min, int max, List<Integer> values) {
		super("Edit Multiple Values (Graphical)", 13, new Point(-300, 0));
		mvea = new MultiValueEditArea(min, max, values);
		mvea.setPop(this);
		mvea.setPreferredSize(new Dimension(500, 500));

		JPanel allPanels = new JPanel();
		allPanels.setLayout(new BoxLayout(allPanels, BoxLayout.Y_AXIS));
		allPanels.setMaximumSize(new Dimension(500, 600));

		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(0, 4, 0, 0));
		buttonPanel.setPreferredSize(new Dimension(500, 50));
		buttonPanel.add(VibeComposerGUI.makeButton("Add", e -> {
			if (mvea.getValues().size() > 31) {
				return;
			}
			mvea.getValues().add(0);
			repaintMvea();
		}));
		buttonPanel.add(VibeComposerGUI.makeButton("Remove", e -> {
			if (mvea.getValues().size() <= 1) {
				return;
			}
			mvea.getValues().remove(mvea.getValues().size() - 1);
			repaintMvea();
		}));
		buttonPanel.add(VibeComposerGUI.makeButton("Clear", e -> {
			int size = mvea.getValues().size();
			mvea.getValues().clear();
			for (int i = 0; i < size; i++) {
				mvea.getValues().add(0);
			}
			repaintMvea();
		}));
		buttonPanel.add(VibeComposerGUI.makeButton("2x", e -> {
			if (mvea.getValues().size() > 16) {
				return;
			}
			mvea.getValues().addAll(mvea.getValues());
			repaintMvea();
		}));
		buttonPanel.add(VibeComposerGUI.makeButton("Dd", e -> {
			if (mvea.getValues().size() > 16) {
				return;
			}
			List<Integer> ddValues = new ArrayList<>();
			List<Integer> oldValues = mvea.getValues();
			oldValues.forEach(f -> {
				ddValues.add(f);
				ddValues.add(f);
			});
			oldValues.clear();
			oldValues.addAll(ddValues);
			repaintMvea();
		}));
		buttonPanel.add(VibeComposerGUI.makeButton("1/2", e -> {
			if (mvea.getValues().size() <= 1) {
				return;
			}
			int toRemain = (mvea.getValues().size() + 1) / 2;
			for (int i = mvea.getValues().size() - 1; i >= toRemain; i--) {
				mvea.getValues().remove(i);
			}

			repaintMvea();
		}));
		buttonPanel.add(VibeComposerGUI.makeButton("???", e -> {
			int size = mvea.getValues().size();
			boolean successRandGenerator = false;
			if (butt != null && butt.getRandGenerator() != null) {
				List<Integer> randValues = null;
				try {
					randValues = butt.getRandGenerator().apply(new Object());
				} catch (Exception exc) {
					LG.d("Random generator is not ready!");
				}
				if (randValues != null && !randValues.isEmpty()) {
					mvea.getValues().clear();
					mvea.getValues().addAll(randValues);
					successRandGenerator = true;
				}

			}
			if (!successRandGenerator) {
				Random rnd = new Random();
				mvea.getValues().clear();
				for (int i = 0; i < size; i++) {
					mvea.getValues().add(rnd.nextInt(max - min + 1) + min);
				}
			}

			repaintMvea();
		}));

		buttonPanel.add(VibeComposerGUI.makeButton("> OK <", e -> close()));

		JPanel mveaPanel = new JPanel();
		mveaPanel.setPreferredSize(new Dimension(500, 500));
		mveaPanel.setMinimumSize(new Dimension(500, 500));
		mveaPanel.add(mvea);

		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.X_AXIS));
		text = new JTextField(StringUtils.join(values, ","), 25);
		textPanel.add(text);
		textPanel.add(VibeComposerGUI.makeButton("Apply", e -> {
			if (StringUtils.isNotEmpty(text.getText())) {
				try {
					String[] textSplit = text.getText().split(",");
					List<Integer> nums = new ArrayList<>();
					for (String s : textSplit) {
						nums.add(Integer.valueOf(s));
					}
					mvea.getValues().clear();
					mvea.getValues().addAll(nums);
					repaintMvea();
				} catch (Exception exc) {
					LG.d("Incorrect text format, cannot convert to list of numbers.");
				}
			}
		}));

		allPanels.add(buttonPanel);
		allPanels.add(textPanel);
		allPanels.add(mveaPanel);
		frame.add(allPanels);
		frame.pack();
		frame.setVisible(true);
	}

	void repaintMvea() {
		mvea.repaint();
		text.setText(StringUtils.join(mvea.getValues(), ","));
	}

	public void linkButton(RandomIntegerListButton butt) {
		this.butt = butt;
		if (butt != null && butt.getHighlighterGenerator() != null) {
			mvea.setHighlightedGrid(butt.getHighlighterGenerator().apply(new Object()));
		}
	}

	@Override
	protected void addFrameWindowOperation() {
		frame.addWindowListener(new WindowListener() {

			@Override
			public void windowOpened(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowClosing(WindowEvent e) {
				if (butt != null) {
					butt.setValues(mvea.getValues());
				}

				if (closeFunc != null) {
					closeFunc.accept(new Object());
				}
				handleClose();
				/*if (RandomValueButton.singlePopup != null) {
					if (RandomValueButton.singlePopup.randomNum == randomNum) {
						RandomValueButton.singlePopup = null;
					}
				}*/

			}

			@Override
			public void windowClosed(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowIconified(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowDeiconified(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowActivated(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowDeactivated(WindowEvent e) {
				// Auto-generated method stub

			}

		});
	}

	public List<Integer> getValues() {
		return mvea.getValues();
	}

	public void setCloseFunc(Consumer<Object> func) {
		closeFunc = func;
	}

	public JTextField getText() {
		return text;
	}

	public void setText(JTextField text) {
		this.text = text;
	}

}
