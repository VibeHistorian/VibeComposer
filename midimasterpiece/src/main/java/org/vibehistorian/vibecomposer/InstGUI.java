package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.VeloRect;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.SoloMuter;
import org.vibehistorian.vibecomposer.Parts.InstPart;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Shared controls and panel collection owned by one instrument tab. */
public abstract class InstGUI<P extends InstPanel> implements InstrumentGUIControls {
	protected final INST instrument;
	protected final InstrumentPanelController panelController;
	protected final List<P> panels = new ArrayList<>();

	protected JScrollPane panelScrollPane;
	protected JPanel parentPanel;
	protected JCheckBox enabledCheckBox;
	protected VeloRect groupFilterSlider;
	protected JButton addPanelButton;
	protected JButton generatePanelButton;
	protected JTextField randomPanelsToGenerate;

	protected InstGUI(INST instrument, InstrumentPanelController panelController) {
		this.instrument = instrument;
		this.panelController = panelController;
	}

	public final INST getInstrument() {
		return instrument;
	}

	/** Each instrument supplies the panel type and any instrument-specific setup. */
	public abstract P createPanel(SoloMuter.Context context);

	/** Each instrument supplies its own randomization algorithm. */
	public abstract void createRandomPanels(int panelCount, boolean onlyAdd,
			Integer seed, InstPanel randomizedPanel);

	protected final JScrollPane createPanelScrollPane(JPanel panelContainer) {
		JScrollPane scrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				Dimension size = UITheme.scrollPaneDimension;
				return new Dimension(size.width, size.height - 100);
			}
		};
		scrollPane.setViewportView(panelContainer);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		return scrollPane;
	}

	/** Adds the controls shared by every instrument settings row. */
	protected final void addPanelControls(JPanel settingsPanel, String enabledLabel,
			String addButtonLabel, String generateButtonLabel, String initialPanelCount) {
		addPanelControls(settingsPanel, enabledLabel, addButtonLabel, generateButtonLabel,
				initialPanelCount, null);
	}

	/** The optional callback inserts an instrument-specific control before the group filter. */
	protected final void addPanelControls(JPanel settingsPanel, String enabledLabel,
			String addButtonLabel, String generateButtonLabel, String initialPanelCount,
			Runnable beforeGroupFilter) {
		enabledCheckBox = new CustomCheckBox(enabledLabel, true);
		settingsPanel.add(enabledCheckBox);
		if (beforeGroupFilter != null) {
			beforeGroupFilter.run();
		}
		groupFilterSlider = VeloRect.midi(127);
		settingsPanel.add(new JLabel("LP"));
		settingsPanel.add(groupFilterSlider);

		addPanelButton = SwingUtils.makeButton(addButtonLabel,
				e -> panelController.addRandomPanel(instrument));
		generatePanelButton = SwingUtils.makeButton(generateButtonLabel,
				e -> panelController.generatePanels(instrument, true));
		randomPanelsToGenerate = new JTextField(initialPanelCount, 2);
		settingsPanel.add(addPanelButton);
		settingsPanel.add(generatePanelButton);
		settingsPanel.add(randomPanelsToGenerate);
	}

	protected final <T extends InstPart> List<T> createParts(int seed, Class<T> partType) {
		List<T> parts = new ArrayList<>();
		for (P panel : panels) {
			parts.add(partType.cast(panel.toInstPart(seed)));
		}
		InstPart.sortParts(parts);
		return parts;
	}

	@Override public final JCheckBox getEnabledCheckBox() { return enabledCheckBox; }
	@Override public final VeloRect getGroupFilterSlider() { return groupFilterSlider; }
	@Override public final JButton getAddPanelButton() { return addPanelButton; }
	@Override public final JButton getGeneratePanelButton() { return generatePanelButton; }
	@Override public final JTextField getRandomPanelsToGenerate() { return randomPanelsToGenerate; }
	@Override public final JScrollPane getPanelScrollPane() { return panelScrollPane; }
	@Override public final List<P> getPanels() { return panels; }
}
