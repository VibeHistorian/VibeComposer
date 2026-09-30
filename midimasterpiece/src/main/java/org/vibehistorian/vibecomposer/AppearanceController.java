package org.vibehistorian.vibecomposer;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.SoloMuter;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import java.awt.*;
import java.util.List;

import static org.vibehistorian.vibecomposer.SoloMuteState.globalSoloMuter;
import static org.vibehistorian.vibecomposer.SoloMuteState.groupSoloMuters;
import static org.vibehistorian.vibecomposer.UITheme.*;

/** Applies the shared look and feel and recolors controls owned by the feature GUIs. */
public final class AppearanceController {
	public interface Context {
		JFrame getWindow();
	}

	private final Context context;
	private final MainWindowControls mainWindowControls;
	private final InstrumentPanelController instrumentPanelController;
	private final ChordGUI chordGUI;
	private final DrumGUI drumGUI;
	private final ArpGUI arpGUI;
	private final MelodyGUI melodyGUI;
	private final GenerationGUI generationGUI;

	public AppearanceController(Context context, MainWindowControls mainWindowControls,
			InstrumentPanelController instrumentPanelController, ChordGUI chordGUI, DrumGUI drumGUI,
			ArpGUI arpGUI, MelodyGUI melodyGUI, GenerationGUI generationGUI) {
		this.context = context;
		this.mainWindowControls = mainWindowControls;
		this.instrumentPanelController = instrumentPanelController;
		this.chordGUI = chordGUI;
		this.drumGUI = drumGUI;
		this.arpGUI = arpGUI;
		this.melodyGUI = melodyGUI;
		this.generationGUI = generationGUI;
	}

	public void switchDarkMode() {
		ArrangementGUI.arrSection.setSelectedIndex(0);
		LG.i("Switching dark mode!");
		if (isDarkMode) {
			FlatIntelliJLaf.install();
		} else {
			FlatDarculaLaf.install();
		}
		isDarkMode = !isDarkMode;
		updateGlobalUI();

		toggledUIColor = uiColor();
		toggledComposeColor = uiComposeTextColor();
		toggledRegenerateColor = uiRegenerateTextColor();
		mainWindowControls.toggleFgColors(toggledUIColor, toggledRegenerateColor,
				toggledComposeColor);
		chordGUI.tipLabel.setForeground(toggledUIColor);
		PlaybackState.currentTime.setForeground(toggledUIColor);
		PlaybackState.totalTime.setForeground(toggledUIColor);
		arpGUI.randomArpHitsPerPattern.setForeground(toggledUIColor);
		melodyGUI.randomMelodyOnRegenerate.setForeground(toggledRegenerateColor);
		switchAllOnComposeCheckboxesForegrounds(toggledComposeColor);

		panelColorHigh = UIManager.getColor("Panel.background");
		panelColorLow = UIManager.getColor("Panel.background");
		if (isDarkMode) {
			panelColorHigh = panelColorHigh.darker();
			panelColorLow = panelColorLow.brighter();
		} else {
			panelColorHigh = panelColorHigh.darker();
		}
		if (ArrangementGUI.GLOBAL.equals(ArrangementGUI.arrSection.getVal())) {
			ArrangementGUI.arrangementMiddleColoredPanel.setBackground(panelColorHigh.brighter());
		} else {
			ArrangementGUI.arrangementMiddleColoredPanel.setBackground(toggledUIColor.darker().darker());
		}
		PlaybackState.sliderPanel.setBackground(panelColorLow);

		globalSoloMuter.reapplyTextColor();
		for (SoloMuter soloMuter : groupSoloMuters) {
			soloMuter.reapplyTextColor();
		}

		for (INST instrument : INST.values()) {
			int instrumentIndex = instrument.getIndex();
			instrumentPanelController.getInstList(instrument)
					.forEach(panel -> panel.getSoloMuter().reapplyTextColor());
			List<InstPanel> affectedPanels = instrumentPanelController.getAffectedPanels(instrument);
			affectedPanels.forEach(panel -> {
				if (panel.getComboPanel() != null) {
					panel.getComboPanel().reapplyHits();
				}
			});
			affectedPanels.forEach(panel ->
					panel.setBackground(OMNI.alphen(Constants.instColors[instrumentIndex], 60)));
		}
	}

	private void updateGlobalUI() {
		ColorUIResource background = new ColorUIResource(isDarkMode
				? new Color(68, 66, 67) : new Color(153, 160, 166));
		UIManager.put("Button.background", background);
		UIManager.put("Panel.background", background);
		UIManager.put("ComboBox.background", background);
		UIManager.put("ComboBox.buttonBackground",
				isDarkMode ? new Color(60, 58, 61) : new Color(165, 170, 176));
		UIManager.put("TextField.background", background);
		UIManager.put("Table.background", background);
		UIManager.put("TableHeader.background", background);
		UIManager.put("TabbedPane.background", background);
		UIManager.put("ScrollPane.background", background);
		UIManager.put("ScrollPane.border", background);
		UIManager.put("List.background", background);
		UIManager.put("ScrollBar.background", background);
		SwingUtilities.updateComponentTreeUI(context.getWindow());
		SwingUtilities.updateComponentTreeUI(ExtraSettingsGUI.extraSettingsPanel);
		SwingUtils.popupMenus.forEach(SwingUtilities::updateComponentTreeUI);
	}

	private void switchAllOnComposeCheckboxesForegrounds(Color foreground) {
		melodyGUI.generateMelodiesOnCompose.setForeground(foreground);
		chordGUI.randomChordsGenerateOnCompose.setForeground(foreground);
		arpGUI.randomArpsGenerateOnCompose.setForeground(foreground);
		drumGUI.randomDrumsGenerateOnCompose.setForeground(foreground);
		generationGUI.randomizeBpmOnCompose.setForeground(foreground);
		generationGUI.randomizeTransposeOnCompose.setForeground(foreground);
		generationGUI.randomizeInstOnComposeOrGen.setForeground(foreground);
		ArrangementGUI.randomizeArrangementOnCompose.setForeground(foreground);
		ArrangementGUI.arrangementResetCustomPanelsOnCompose.setForeground(foreground);
		mainWindowControls.getRandomizeScaleModeOnCompose().setForeground(foreground);
		melodyGUI.melodyTargetNotesRandomizeOnCompose.setForeground(foreground);
		melodyGUI.melodyPatternRandomizeOnCompose.setForeground(foreground);
		generationGUI.switchOnComposeRandom.setForeground(foreground);
		ExtraSettingsGUI.randomizeTimingsOnCompose.setForeground(foreground);
		ExtraSettingsGUI.sidechainPatternsOnCompose.setForeground(foreground);
		chordGUI.copyChordsAfterGenerate.setForeground(foreground);
	}
}
