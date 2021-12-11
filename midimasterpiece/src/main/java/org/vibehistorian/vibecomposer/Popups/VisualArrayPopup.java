package org.vibehistorian.vibecomposer.Popups;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import javax.swing.BoxLayout;
import javax.swing.JPanel;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Components.MultiValueEditArea;
import org.vibehistorian.vibecomposer.Components.RandomIntegerListButton;

public class VisualArrayPopup extends CloseablePopup {

	MultiValueEditArea mvea = null;
	RandomIntegerListButton butt = null;
	Function<? super Object, List<Integer>> randGenerator = null;


	public VisualArrayPopup(int min, int max, List<Integer> values) {
		super("Edit Multiple Values (Graphical)", 13);
		mvea = new MultiValueEditArea(min, max, values);
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
			mvea.repaint();
		}));
		buttonPanel.add(VibeComposerGUI.makeButton("Remove", e -> {
			if (mvea.getValues().size() <= 1) {
				return;
			}
			mvea.getValues().remove(mvea.getValues().size() - 1);
			mvea.repaint();
		}));
		buttonPanel.add(VibeComposerGUI.makeButton("Clear", e -> {
			int size = mvea.getValues().size();
			mvea.getValues().clear();
			for (int i = 0; i < size; i++) {
				mvea.getValues().add(0);
			}
			mvea.repaint();
		}));
		buttonPanel.add(VibeComposerGUI.makeButton("2x", e -> {
			if (mvea.getValues().size() > 16) {
				return;
			}
			mvea.getValues().addAll(mvea.getValues());
			mvea.repaint();
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
			mvea.repaint();
		}));
		buttonPanel.add(VibeComposerGUI.makeButton("1/2", e -> {
			if (mvea.getValues().size() <= 1) {
				return;
			}
			int toRemain = (mvea.getValues().size() + 1) / 2;
			for (int i = mvea.getValues().size() - 1; i >= toRemain; i--) {
				mvea.getValues().remove(i);
			}

			mvea.repaint();
		}));
		buttonPanel.add(VibeComposerGUI.makeButton("???", e -> {
			int size = mvea.getValues().size();
			boolean successRandGenerator = false;
			if (randGenerator != null) {
				List<Integer> randValues = null;
				try {
					randValues = randGenerator.apply(new Object());
				} catch (Exception exc) {
					System.out.println("Random generator is not ready!");
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

			mvea.repaint();
		}));

		buttonPanel.add(VibeComposerGUI.makeButton("> OK <", e -> close()));

		JPanel mveaPanel = new JPanel();
		mveaPanel.setPreferredSize(new Dimension(500, 500));
		mveaPanel.setMinimumSize(new Dimension(500, 500));
		mveaPanel.add(mvea);
		allPanels.add(buttonPanel);
		allPanels.add(mveaPanel);
		frame.add(allPanels);
		frame.pack();
		frame.setVisible(true);
	}

	public void linkButton(RandomIntegerListButton butt) {
		this.butt = butt;
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
					butt.setValue(StringUtils.join(mvea.getValues(), ","));
				}

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

	public void setRandGenerator(Function<? super Object, List<Integer>> rndGen) {
		randGenerator = rndGen;
	}

}
