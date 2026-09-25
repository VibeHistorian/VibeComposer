package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.Components.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemListener;

/** Owns controls for settings that affect MIDI generation. */
public class GenerationGUI {
    public static JCheckBox customMidiForceScale;
    public static JCheckBox reuseMidiChannelAfterCopy;
    public static JCheckBox transposedNotesForceScale;
    public static JCheckBox orderedTransposeGeneration;
    public static JCheckBox configHistoryStoreRegeneratedTracks;
    public static JCheckBox patternApplyPausesWhenGenerating;
    public static JCheckBox allowValuesOutOfRange;
    public static ScrollComboBox<String> keyChangeTypeSelection;

    private final ItemListener keyChangeTypeSelectionListener;

    public GenerationGUI(ItemListener keyChangeTypeSelectionListener) {
        this.keyChangeTypeSelectionListener = keyChangeTypeSelectionListener;
    }

    public void initGenerationSettings(JPanel panelGenerationSettingsPanel) {
		// GENERATION

		//          scale
		customMidiForceScale = new CustomCheckBox("Force MIDI Melody Notes To Scale", false);
		reuseMidiChannelAfterCopy = new CustomCheckBox("Reuse MIDI Ch. After Copy (Cc)", true);
		transposedNotesForceScale = new CustomCheckBox("Force Transposed Notes To Scale", false);

		orderedTransposeGeneration = new CustomCheckBox("Ordered Transpose Generation", false);
		configHistoryStoreRegeneratedTracks = new CustomCheckBox(
				"Track History - Include Regenerated Tracks", true);
		MelodyGUI.melodyPatternFlip = new CustomCheckBox("Inverse Melody1 Pattern", false);
		patternApplyPausesWhenGenerating = new CustomCheckBox("Apply Pause% on Generate", true);
		allowValuesOutOfRange = new CustomCheckBox("(Experimental!) Allow Knob Values Out of Range", false);


		JPanel keyChangePanel = new JPanel();
		keyChangePanel.setLayout(new GridLayout(0, 2, 10, 30));
		keyChangeTypeSelection = new ScrollComboBox<String>(false);
		ScrollComboBox.addAll(new String[] { "PIVOT", "TWOFIVEONE", "DIRECT" },
				keyChangeTypeSelection);
		keyChangeTypeSelection.setVal("TWOFIVEONE");
		keyChangeTypeSelection.setPreferredSize(new Dimension(250, 30));
		keyChangeTypeSelection.addItemListener(keyChangeTypeSelectionListener);
		keyChangePanel.add(new JLabel("<html>Key Change<br>Type:</html>"));
		keyChangePanel.add(keyChangeTypeSelection);

		panelGenerationSettingsPanel.add(customMidiForceScale);
		panelGenerationSettingsPanel.add(transposedNotesForceScale);
		panelGenerationSettingsPanel.add(reuseMidiChannelAfterCopy);
		panelGenerationSettingsPanel.add(orderedTransposeGeneration);
		panelGenerationSettingsPanel.add(configHistoryStoreRegeneratedTracks);
		//panelGenerationSettingsPanel.add(MelodyGUI.melodyPatternFlip); -- pattern flip is now also available per-instrument..
		panelGenerationSettingsPanel.add(patternApplyPausesWhenGenerating);
		panelGenerationSettingsPanel.add(allowValuesOutOfRange);
		panelGenerationSettingsPanel.add(keyChangePanel);
	}
}
