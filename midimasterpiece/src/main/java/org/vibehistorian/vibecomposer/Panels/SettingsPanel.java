package org.vibehistorian.vibecomposer.Panels;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.BevelBorder;

import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;

public class SettingsPanel extends JPanel {

	private static final long serialVersionUID = -2303731820368636826L;

	private int alwaysVisible = 8;
	private boolean showAll = true;
	private JCheckBox enabled = new JCheckBox("Enable", true);

	private JButton soloAllButton = new JButton();
	private JButton expandCollapseButton = new JButton("-");
	private Class instClass = null;

	public SettingsPanel(String title, Class clazz) {
		super();
		setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		instClass = clazz;
		add(new JLabel(title));
		add(enabled);
		initSoloButton();
	}

	public int getAlwaysVisible() {
		return alwaysVisible;
	}

	public void setAlwaysVisible(int alwaysVisible) {
		this.alwaysVisible = alwaysVisible;
	}

	public void showMinimalComponents() {
		Component[] cmps = getComponents();
		for (int i = alwaysVisible; i < cmps.length; i++) {
			cmps[i].setVisible(false);
		}
	}

	public void showAllComponents() {
		Component[] cmps = getComponents();
		for (int i = alwaysVisible; i < cmps.length; i++) {
			cmps[i].setVisible(true);
		}
	}

	private void initSoloButton() {
		soloAllButton.setText("S");
		soloAllButton.setBackground(null);

		JButton soloAllChords = new JButton("S");
		soloAllChords.setBackground(null);

		List<? extends InstPanel> panels = null;
		if (instClass == ChordPart.class) {
			panels = VibeComposerGUI.chordPanels;
		} else if (instClass == ArpPart.class) {
			panels = VibeComposerGUI.arpPanels;
		} else if (instClass == DrumPart.class) {
			panels = VibeComposerGUI.drumPanels;
		}

		final List<? extends InstPanel> finalPanels = panels;

		soloAllChords.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (new Color(120, 180, 120).equals(soloAllChords.getBackground())) {
					System.out.println("Background not null");
				} else {
					soloAllTracks(finalPanels, ChordPart.class);
					soloAllChords.setBackground(new Color(120, 180, 120));
				}

			}

		});
		add(soloAllChords);
	}

	public void soloAllTracks(List<? extends InstPanel> panels, Class partClass) {
		if (VibeComposerGUI.sequencer != null && VibeComposerGUI.sequencer.isOpen()) {
			for (InstPanel ip : panels) {
				InstPart part = MidiGenerator.trackList.stream()
						.filter(e -> e.getClass().equals(partClass)
								&& ip.getPanelOrder() == ((InstPart) e).getOrder())
						.findFirst().get();
				Integer trackOrder = MidiGenerator.trackList.indexOf(part);
				if (!VibeComposerGUI.sequencer.getTrackSolo(trackOrder + 1)) {
					VibeComposerGUI.sequencer.setTrackSolo(trackOrder + 1, true);
				} else {
					VibeComposerGUI.sequencer.setTrackSolo(trackOrder + 1, false);
				}
			}
		} else {
			System.out.println("Sequencer not ready!");
		}
	}

	public void finishMinimalInit() {
		alwaysVisible = getComponents().length + 1;
		expandCollapseButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (showAll) {
					showAll = false;
					showMinimalComponents();
					expandCollapseButton.setText("+");
				} else {
					showAll = true;
					showAllComponents();
					expandCollapseButton.setText("-");
				}
			}
		});
		expandCollapseButton.setPreferredSize(new Dimension(30, 30));
		add(expandCollapseButton);
	}

	public boolean getEnabled() {
		return enabled.isSelected();
	}

	public void setEnabled(boolean enabled) {
		this.enabled.setSelected(enabled);
	}
}
