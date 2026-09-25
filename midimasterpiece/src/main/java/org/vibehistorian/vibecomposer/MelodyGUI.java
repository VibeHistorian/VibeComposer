/* --------------------
* @author Vibe Historian
* ---------------------

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or any
later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not,
see <https://www.gnu.org/licenses/>.
*/

package org.vibehistorian.vibecomposer;

import jm.music.data.Phrase;
import jm.music.tools.Mod;
import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.DynamicGridLayout;
import org.vibehistorian.vibecomposer.Components.MelodyMidiDropPane;
import org.vibehistorian.vibecomposer.Components.RandomIntegerListButton;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Components.VeloRect;
import org.vibehistorian.vibecomposer.Enums.BlockType;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.Panels.MelodyPanel;
import org.vibehistorian.vibecomposer.Panels.PartManagerPanel;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Builds and owns the melody controls and their UI state. */
public class MelodyGUI implements InstrumentGUIControls {

	private final List<MelodyPanel> melodyPanels = new ArrayList<>();
	private JScrollPane melodyScrollPane;
	public static JPanel melodyParentPanel;
	private JCheckBox enabledCheckBox;
	private VeloRect groupFilterSlider;
	private JButton addPanelButton;
	private JButton generatePanelButton;
	private JTextField randomPanelsToGenerate;

	public static JCheckBox generateMelodiesOnCompose;
	public static KnobPanel melodyUseOldAlgoChance;
	public static JCheckBox randomMelodyOnRegenerate;
	public static JCheckBox randomMelodySameSeed;
	public static JCheckBox melodyFirstNoteFromChord;
	public static JCheckBox randomChordNote;
	public static JCheckBox melodyBasicChordsOnly;
	public static KnobPanel melodyChordNoteTarget;
	public static KnobPanel melodyTonicNoteTarget;
	public static JCheckBox melodyEmphasizeKey;
	public static KnobPanel melodyModeNoteTarget;
	public static JCheckBox useUserMelody;
	public static MelodyMidiDropPane dropPane;
	public static ScrollComboBox<String> userMelodyScaleModeSelect;
	public static JCheckBox melody1ForcePatterns;
	public static JCheckBox melodyArpySurprises;
	public static JCheckBox melodySingleNoteExceptions;
	public static JCheckBox melodyFillPausesPerChord;
	public static KnobPanel melodyNewBlocksChance;
	public static JCheckBox melodyLegacyMode;
	public static JCheckBox melodyAvoidChordJumpsLegacy;
	public static JCheckBox melodyUseDirectionsFromProgression;
	public static JCheckBox melodyPatternFlip;
	public static ScrollComboBox<MelodyUtils.NoteTargetDirection> noteTargetDirectionChoice;
	public static ScrollComboBox<String> melodyBlockTargetMode;
	public static JCheckBox melodyTargetNotesRandomizeOnCompose;
	public static ScrollComboBox<String> melodyPatternEffect;
	public static ScrollComboBox<String> melodyRhythmAccents;
	public static ScrollComboBox<String> melodyRhythmAccentsMode;
	public static JCheckBox melodyRhythmAccentsPocket;
	public static JCheckBox melodyPatternRandomizeOnCompose;
	public static KnobPanel melodyReplaceAvoidNotes;
	public static KnobPanel melodyMaxDirChanges;
	public static KnobPanel melodyTargetNoteVariation;
	public static JPanel melodyBlockTypePreferences;
	public static VeloRect[] melodyBlockTypePreference;
	public static RandomIntegerListButton melodyBlockChoicePreference;
	public static JCheckBox melodyUseCustomDurations;
	public static JCheckBox melodyCustomDurationsRandomWeighting;
	public static JCheckBox melodyCustomDurationsStrictMode;
	public static JCheckBox combineMelodyTracks;

	private final Context context;

	public MelodyGUI(Context context) {
		this.context = context;
	}

	@Override public JCheckBox getEnabledCheckBox() { return enabledCheckBox; }
	@Override public VeloRect getGroupFilterSlider() { return groupFilterSlider; }
	@Override public JButton getAddPanelButton() { return addPanelButton; }
	@Override public JButton getGeneratePanelButton() { return generatePanelButton; }
	@Override public JTextField getRandomPanelsToGenerate() { return randomPanelsToGenerate; }
	@Override public JScrollPane getPanelScrollPane() { return melodyScrollPane; }
	@Override public List<MelodyPanel> getPanels() { return melodyPanels; }

	public void saveToConfig(GUIConfig gc, int seed) {
		gc.setMelodyEnable(enabledCheckBox.isSelected());
		List<MelodyPart> parts = new ArrayList<>();
		for (MelodyPanel panel : melodyPanels) parts.add((MelodyPart) panel.toInstPart(seed));
		InstPart.sortParts(parts);
		gc.setMelodyParts(parts);
		gc.setMelodyUseOldAlgoChance(melodyUseOldAlgoChance.getInt());
		gc.setFirstNoteFromChord(melodyFirstNoteFromChord.isSelected());
		gc.setFirstNoteRandomized(randomChordNote.isSelected());
		gc.setMelodyBasicChordsOnly(melodyBasicChordsOnly.isSelected());
		gc.setMelodyTonicNoteTarget(melodyTonicNoteTarget.getInt());
		gc.setMelodyChordNoteTarget(melodyChordNoteTarget.getInt());
		gc.setMelodyModeNoteTarget(melodyModeNoteTarget.getInt());
		gc.setMelodyEmphasizeKey(melodyEmphasizeKey.isSelected());
		gc.setMelody1ForcePatterns(melody1ForcePatterns.isSelected());
		gc.setMelodyArpySurprises(melodyArpySurprises.isSelected());
		gc.setMelodySingleNoteExceptions(melodySingleNoteExceptions.isSelected());
		gc.setMelodyFillPausesPerChord(melodyFillPausesPerChord.isSelected());
		gc.setMelodyLegacyMode(melodyLegacyMode.isSelected());
		gc.setMelodyNewBlocksChance(melodyNewBlocksChance.getInt());
		gc.setMelodyUseDirectionsFromProgression(melodyUseDirectionsFromProgression.isSelected());
		gc.setMelodyAvoidChordJumps(melodyAvoidChordJumpsLegacy.isSelected());
		gc.setMelodyBlockTargetMode(melodyBlockTargetMode.getSelectedIndex());
		gc.setNoteTargetDirectionChoice(noteTargetDirectionChoice.getSelectedItem());
		gc.setMelodyPatternEffect(melodyPatternEffect.getSelectedIndex());
		gc.setMelodyRhythmAccents(melodyRhythmAccents.getSelectedIndex());
		gc.setMelodyRhythmAccentsMode(melodyRhythmAccentsMode.getSelectedIndex());
		gc.setMelodyRhythmAccentsPocket(melodyRhythmAccentsPocket.isSelected());
		gc.setMelodyReplaceAvoidNotes(melodyReplaceAvoidNotes.getInt());
		gc.setMelodyMaxDirChanges(melodyMaxDirChanges.getInt());
		gc.setMelodyTargetNoteVariation(melodyTargetNoteVariation.getInt());
		gc.setMelodyBlockChoicePreference(melodyBlockChoicePreference.getValues());
		gc.setMelodyBlockTypePreference(Arrays.stream(melodyBlockTypePreference).map(e -> e.getValue()).collect(java.util.stream.Collectors.toList()));
		gc.setMelodyUseCustomDurations(melodyUseCustomDurations.isSelected());
		gc.setMelodyCustomDurationsRandomWeighting(melodyCustomDurationsRandomWeighting.isSelected());
		gc.setMelodyCustomDurationsStrictMode(melodyCustomDurationsStrictMode.isSelected());
		gc.setMelodyPatternFlip(melodyPatternFlip.isSelected());
		gc.setCombineMelodyTracks(combineMelodyTracks.isSelected());
		if (MelodyMidiDropPane.userMelody != null) gc.setMelodyNotes(new PhraseNotes(MelodyMidiDropPane.userMelody));
	}

	public void loadFromConfig(GUIConfig gc) {
		enabledCheckBox.setSelected(gc.isMelodyEnable());
		melodyFirstNoteFromChord.setSelected(gc.isFirstNoteFromChord());
		randomChordNote.setSelected(gc.isFirstNoteRandomized());
		melodyUseOldAlgoChance.setInt(gc.getMelodyUseOldAlgoChance());
		melodyBasicChordsOnly.setSelected(gc.isMelodyBasicChordsOnly());
		melodyTonicNoteTarget.setInt(gc.getMelodyTonicNoteTarget());
		melodyChordNoteTarget.setInt(gc.getMelodyChordNoteTarget());
		melodyModeNoteTarget.setInt(gc.getMelodyModeNoteTarget());
		melodyEmphasizeKey.setSelected(gc.isMelodyEmphasizeKey());
		melodyArpySurprises.setSelected(gc.isMelodyArpySurprises());
		melody1ForcePatterns.setSelected(gc.isMelody1ForcePatterns());
		melodySingleNoteExceptions.setSelected(gc.isMelodySingleNoteExceptions());
		melodyFillPausesPerChord.setSelected(gc.isMelodyFillPausesPerChord());
		melodyLegacyMode.setSelected(gc.isMelodyLegacyMode());
		melodyNewBlocksChance.setInt(gc.getMelodyNewBlocksChance());
		melodyAvoidChordJumpsLegacy.setSelected(gc.isMelodyAvoidChordJumps());
		melodyUseDirectionsFromProgression.setSelected(gc.isMelodyUseDirectionsFromProgression());
		melodyBlockTargetMode.setSelectedIndex(gc.getMelodyBlockTargetMode());
		noteTargetDirectionChoice.setVal(gc.getNoteTargetDirectionChoice());
		melodyPatternEffect.setSelectedIndex(gc.getMelodyPatternEffect());
		melodyRhythmAccents.setSelectedIndex(gc.getMelodyRhythmAccents());
		melodyRhythmAccentsMode.setSelectedIndex(gc.getMelodyRhythmAccentsMode());
		melodyRhythmAccentsPocket.setSelected(gc.isMelodyRhythmAccentsPocket());
		melodyReplaceAvoidNotes.setInt(gc.getMelodyReplaceAvoidNotes());
		melodyMaxDirChanges.setInt(gc.getMelodyMaxDirChanges());
		melodyTargetNoteVariation.setInt(gc.getMelodyTargetNoteVariation());
		melodyBlockChoicePreference.setValues(gc.getMelodyBlockChoicePreference());
		for (int i = 0; i < BlockType.values().length; i++) {
			int value = i < gc.getMelodyBlockTypePreference().size() ? gc.getMelodyBlockTypePreference().get(i) : BlockType.values()[i].defaultChance;
			melodyBlockTypePreference[i].setValue(value);
		}
		melodyUseCustomDurations.setSelected(gc.isMelodyUseCustomDurations());
		melodyCustomDurationsRandomWeighting.setSelected(gc.isMelodyCustomDurationsRandomWeighting());
		melodyCustomDurationsStrictMode.setSelected(gc.isMelodyCustomDurationsStrictMode());
		melodyPatternFlip.setSelected(gc.isMelodyPatternFlip());
		combineMelodyTracks.setSelected(gc.isCombineMelodyTracks());
		if (gc.getMelodyNotes() != null) {
			MelodyMidiDropPane.userMelody = gc.getMelodyNotes().makePhrase();
			dropPane.getMessage().setText("~MELODY LOADED FROM FILE~");
		} else {
			MelodyMidiDropPane.userMelody = null;
			dropPane.getMessage().setText(" * * Drag'n'Drop MIDI Here * * ");
		}
	}

	public void loadPartsFromConfig(GUIConfig gc, Consumer<List<MelodyPart>> restorePanels) {
		restorePanels.accept(gc.getMelodyParts());
	}

	/** Supplies shared GUI operations without making this module depend on the main window. */
	public interface Context {
		JButton makeButton(String name, Consumer<? super Object> action);
		void addPanel();
		void generatePanels(boolean triggerRegenerate);
		boolean canRegenerateOnChange();
		void regenerate();
		MelodyPanel addMelodyPanel();
		List<InstPanel> getAffectedPanels(int instrument);
	}

	public void initMelodyGenSettings(int startY, int anchorSide) {
		JPanel scrollableMelodyPanels = new JPanel();
		scrollableMelodyPanels.setLayout(new BoxLayout(scrollableMelodyPanels, BoxLayout.Y_AXIS));
		scrollableMelodyPanels.setAutoscrolls(true);

		melodyScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				Dimension size = UITheme.scrollPaneDimension;
				return new Dimension(size.width, size.height - 100);
			}
		};
		melodyScrollPane.setViewportView(scrollableMelodyPanels);
		melodyScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		melodyScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		melodyUseOldAlgoChance = new KnobPanel("Legacy<br>Algo", 0);

		randomChordNote = new CustomCheckBox();
		randomChordNote.setSelected(true);
		melodyFirstNoteFromChord = new CustomCheckBox();
		melodyFirstNoteFromChord.setSelected(true);

		JPanel melodySettingsExtraPanelOrg = initMelodySettings();
		JPanel melodySettingsExtraPanelShape = initMelodySettingsPlus();
		JPanel melodySettingsExtraPanelBlocksPatternsCompose = initMelodySettingsPlusPlus();

		melodyParentPanel = new JPanel() {
			@Override
			public Dimension getPreferredSize() {
				return UITheme.scrollPaneDimension;
			}
		};
		melodyParentPanel.setLayout(new BoxLayout(melodyParentPanel, BoxLayout.Y_AXIS));
		JPanel borderPanel = new JPanel() {
			@Override
			public Dimension getMaximumSize() {
				return new Dimension(UITheme.scrollPaneDimension.width, 150);
			}
		};
		borderPanel.setLayout(new DynamicGridLayout(0, 1));
		borderPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
		borderPanel.add(melodySettingsExtraPanelOrg);
		borderPanel.add(melodySettingsExtraPanelShape);
		borderPanel.add(melodySettingsExtraPanelBlocksPatternsCompose);
		melodyParentPanel.add(borderPanel);
		melodyParentPanel.add(melodyScrollPane);

		UITheme.toggleableComponents.add(melodySettingsExtraPanelShape);
		UITheme.toggleableComponents.add(melodySettingsExtraPanelBlocksPatternsCompose);
	}

	JPanel initMelodySettings() {
		JPanel settings = new JPanel();
		settings.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		settings.setAlignmentX(Component.LEFT_ALIGNMENT);
		settings.setMaximumSize(new Dimension(1800, 50));

		enabledCheckBox = new CustomCheckBox("MELODY", true);
		settings.add(enabledCheckBox);
		groupFilterSlider = VeloRect.midi(127);
		settings.add(new JLabel("LP"));
		settings.add(groupFilterSlider);

		addPanelButton = context.makeButton("+Melody", e -> context.addPanel());
		generatePanelButton = context.makeButton("Generate Melodies:",
				e -> context.generatePanels(true));
		randomPanelsToGenerate = new JTextField("3", 2);
		settings.add(addPanelButton);
		settings.add(generatePanelButton);
		settings.add(randomPanelsToGenerate);
		generateMelodiesOnCompose = SwingUtils.makeCheckBox("On Compose", false, true);
		settings.add(generateMelodiesOnCompose);

		JButton generateUserMelodySeed = context.makeButton("Randomize Seed", e -> {
			randomizeMelodySeeds();
			if (context.canRegenerateOnChange()) {
				context.regenerate();
			}
		});
		JButton clearUserMelodySeed = context.makeButton("Clear Seeds",
				e -> context.getAffectedPanels(0).forEach(m -> m.setPatternSeed(0)));
		randomMelodySameSeed = new CustomCheckBox("Same#", false);
		randomMelodyOnRegenerate = SwingUtils.makeCheckBox("on Manual Regen.", false, true);
		melody1ForcePatterns = new CustomCheckBox("<html>Force Melody#1<br> Outline</html>", true);

		dropPane = new MelodyMidiDropPane();
		useUserMelody = new CustomCheckBox("<html>Use MIDI<br>Melody File</html>", true);
		userMelodyScaleModeSelect = new ScrollComboBox<>(false);
		userMelodyScaleModeSelect.addItem(OMNI.EMPTYCOMBO);
		userMelodyScaleModeSelect.addItemListener(e -> {
			if (e.getStateChange() == ItemEvent.SELECTED
					&& userMelodyScaleModeSelect.getSelectedIndex() > 0
					&& MelodyMidiDropPane.userMelodyCandidate != null) {
				Phrase melody = MelodyMidiDropPane.userMelodyCandidate.copy();
				String item = userMelodyScaleModeSelect
						.getItemAt(userMelodyScaleModeSelect.getSelectedIndex());
				String[] itemSplit = item.split(",");
				int transposeUpBy = Integer.valueOf(itemSplit[1]);
				ScaleMode toMode = ScaleMode.valueOf(itemSplit[0]);
				Mod.transpose(melody, transposeUpBy);
				MidiUtils.transposePhrase(melody, toMode.noteAdjustScale,
				ScaleMode.IONIAN.noteAdjustScale, ExtraSettingsGUI.transposedNotesForceScale.isSelected());
				ScoreGUI.transposeScore.setInt(transposeUpBy * -1);
				GenerationGUI.scaleMode.setVal(toMode.toString());
				MelodyMidiDropPane.userMelody = melody;
				userMelodyScaleModeSelect.setSelectedIndex(0);
			}
		});

		combineMelodyTracks = new CustomCheckBox("<html>Combine<br>MIDI Tracks</html>", false);
		settings.add(melody1ForcePatterns);
		settings.add(combineMelodyTracks);
		settings.add(generateUserMelodySeed);
		settings.add(randomMelodySameSeed);
		settings.add(randomMelodyOnRegenerate);
		settings.add(clearUserMelodySeed);
		settings.add(useUserMelody);
		settings.add(dropPane);
		settings.add(new JLabel("in Mode:"));
		settings.add(userMelodyScaleModeSelect);
		return settings;
	}

	JPanel initMelodySettingsPlus() {
		JPanel settings = new JPanel();
		settings.setAlignmentX(Component.LEFT_ALIGNMENT);
		settings.setMaximumSize(new Dimension(1800, 50));
		JLabel label = new JLabel("MELODY SETTINGS+");
		label.setPreferredSize(new Dimension(120, 30));
		label.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		settings.add(label);

		melodyUseDirectionsFromProgression = new CustomCheckBox(
				"<html>Use Chord<br>Directions</html>", false);
		melodyBasicChordsOnly = new CustomCheckBox("<html>Base<br> Chords</html>", false);
		melodyChordNoteTarget = new KnobPanel("Chord Note<br> Target%", 40);
		melodyTonicNoteTarget = new KnobPanel("Tonic Note<br> Target%", 20);
		melodyEmphasizeKey = new CustomCheckBox("<html>Emphasize<br> Key</html>", true);
		melodyModeNoteTarget = new KnobPanel("Mode Note<br> Target%", 15);
		melodyArpySurprises = new CustomCheckBox("<html>Insert<br> Arps</html>", false);
		melodySingleNoteExceptions = new CustomCheckBox("<html>Single Note<br>Exceptions</html>", true);
		melodyFillPausesPerChord = new CustomCheckBox("<html>Fill Pauses<br>Per Chord</html>", true);
		melodyLegacyMode = new CustomCheckBox("<html>LEGACY<br>MODE</html>", false);
		melodyLegacyMode.addChangeListener(evt -> {
			boolean nonLegacyVisible = !melodyLegacyMode.isSelected();
			melodyChordNoteTarget.setVisible(nonLegacyVisible);
			melodyTonicNoteTarget.setVisible(nonLegacyVisible);
			melodyModeNoteTarget.setVisible(nonLegacyVisible);
			melodyEmphasizeKey.setVisible(nonLegacyVisible);
			melodyBlockTypePreferences.setVisible(nonLegacyVisible);
			melodyNewBlocksChance.setVisible(nonLegacyVisible);
			melodyBlockTargetMode.setVisible(nonLegacyVisible);
			melodyTargetNotesRandomizeOnCompose.setVisible(nonLegacyVisible);
			melodyPatternEffect.setVisible(nonLegacyVisible);
			melodyPatternRandomizeOnCompose.setVisible(nonLegacyVisible);
			noteTargetDirectionChoice.setVisible(nonLegacyVisible);
		});
		melodyAvoidChordJumpsLegacy = new CustomCheckBox("<html>Avoid<br>Chord Jumps</html>", true);
		melodyReplaceAvoidNotes = new KnobPanel("Replace Near<br>Chord Notes", 1, 0, 2);
		melodyMaxDirChanges = new KnobPanel("Max. Dir.<br>Changes", 2, 1, 6);
		melodyTargetNoteVariation = new KnobPanel("Target Note<br>Variation", 3, 1, 6);

		settings.add(melodyChordNoteTarget);
		settings.add(melodyTonicNoteTarget);
		settings.add(melodyModeNoteTarget);
		settings.add(melodyEmphasizeKey);
		settings.add(melodyBasicChordsOnly);
		settings.add(melodyReplaceAvoidNotes);
		settings.add(melodyMaxDirChanges);
		settings.add(melodyUseDirectionsFromProgression);
		settings.add(melodyTargetNoteVariation);
		settings.add(melodyArpySurprises);
		settings.add(melodySingleNoteExceptions);
		settings.add(melodyFillPausesPerChord);
		settings.add(melodyLegacyMode);
		settings.add(new PartManagerPanel(0));
		return settings;
	}

	JPanel initMelodySettingsPlusPlus() {
		melodyBlockTypePreferences = new JPanel();
		melodyBlockTypePreference = new VeloRect[BlockType.values().length];
		for (int i = 0; i < BlockType.values().length; i++) {
			melodyBlockTypePreference[i] = VeloRect.percent(BlockType.values()[i].defaultChance);
			melodyBlockTypePreferences.add(melodyBlockTypePreference[i]);
		}

		melodyNewBlocksChance = new KnobPanel("New<br>Blocks%", 25);
		melodyBlockTargetMode = new ScrollComboBox<>();
		ScrollComboBox.addAll(new String[] { "#. Chord Note", "Chord Root + #", "MIDI 60 (C4) + #" },
				melodyBlockTargetMode);
		melodyBlockTargetMode.setSelectedIndex(2);
		melodyTargetNotesRandomizeOnCompose = SwingUtils.makeCheckBox(
				"<html>Randomize Targets<br> on Compose</html>", true, true);
		melodyPatternEffect = new ScrollComboBox<>();
		ScrollComboBox.addAll(new String[] { "Rhythm", "Notes", "Rhythm+Notes" }, melodyPatternEffect);
		melodyPatternEffect.setSelectedIndex(2);
		melodyPatternRandomizeOnCompose = SwingUtils.makeCheckBox(
				"<html>Randomize Pattern<br> on Compose</html>", true, true);
		melodyRhythmAccents = new ScrollComboBox<>();
		ScrollComboBox.addAll(new String[] { "None", "Snares", "Kicks", "Rides,OpenHH",
				"Snares,Kicks", "Snares,Rides,OpenHH" }, melodyRhythmAccents);
		melodyRhythmAccentsMode = new ScrollComboBox<>();
		ScrollComboBox.addAll(new String[] { "Mute", "Pitch+", "Pitch-", "Pitch~", "Vol+", "Vol-", "---" },
				melodyRhythmAccentsMode);
		melodyRhythmAccentsPocket = new CustomCheckBox("Pocket", false);

		JPanel settings = new JPanel();
		settings.setAlignmentX(Component.LEFT_ALIGNMENT);
		settings.setMaximumSize(new Dimension(1800, 50));
		JLabel label = new JLabel("MELODY SETTINGS++");
		label.setPreferredSize(new Dimension(120, 30));
		label.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		settings.add(label);
		JLabel blockTypesLabel = new JLabel("<html>Block<br>Types</html>");
		blockTypesLabel.setToolTipText("Types (in order): " + Arrays.stream(BlockType.values())
				.map(Enum::name).collect(Collectors.joining(",")));
		settings.add(blockTypesLabel);
		settings.add(melodyBlockTypePreferences);
		settings.add(melodyNewBlocksChance);
		settings.add(new JLabel("<html>Note Target<br>Mode</html>"));
		noteTargetDirectionChoice = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(MelodyUtils.NoteTargetDirection.values(), noteTargetDirectionChoice);
		noteTargetDirectionChoice.setSelectedIndex(1);
		settings.add(noteTargetDirectionChoice);
		settings.add(melodyBlockTargetMode);
		settings.add(melodyTargetNotesRandomizeOnCompose);
		settings.add(new JLabel("Pattern Effect"));
		settings.add(melodyPatternEffect);
		settings.add(melodyPatternRandomizeOnCompose);
		JPanel postProcessPanel = new JPanel();
		postProcessPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		postProcessPanel.add(new JLabel("<html>Drum Rhythm Accents<br>(Post-process)</html>"));
		postProcessPanel.add(melodyRhythmAccents);
		postProcessPanel.add(new JLabel("Mode"));
		postProcessPanel.add(melodyRhythmAccentsMode);
		postProcessPanel.add(melodyRhythmAccentsPocket);
		settings.add(postProcessPanel);
		return settings;
	}

	public static void initExtraSettingsMelody(JPanel melodyGenerationSettingsPanel) {
		JPanel blockChoicePanel = new JPanel(new GridLayout(0, 2));
		melodyBlockChoicePreference = new RandomIntegerListButton("0", null);
		melodyBlockChoicePreference.setValues(MelodyUtils.BLOCK_CHANGE_JUMP_PREFERENCE);
		melodyBlockChoicePreference.min = 0;
		melodyBlockChoicePreference.max = 7;
		melodyBlockChoicePreference.editableCount = false;
		melodyBlockChoicePreference.setRandGenerator(e -> {
			List<Integer> scrambled = new ArrayList<>(MelodyUtils.BLOCK_CHANGE_JUMP_PREFERENCE);
			Collections.shuffle(scrambled, new Random());
			return scrambled;
		});
		melodyBlockChoicePreference.setTextGenerator(e -> org.apache.commons.lang3.StringUtils
				.join(melodyBlockChoicePreference.getRandGenerator().apply(new Object()), ","));
		melodyBlockChoicePreference.setPostFunc(e -> {
			List<Integer> values = melodyBlockChoicePreference.getValues();
			if (values.size() > 1 && (values.size() != MelodyUtils.BLOCK_CHANGE_JUMP_PREFERENCE.size()
					|| !values.containsAll(MelodyUtils.BLOCK_CHANGE_JUMP_PREFERENCE))) {
				melodyBlockChoicePreference.setValues(MelodyUtils.BLOCK_CHANGE_JUMP_PREFERENCE, false);
			}
		});

		melodyUseCustomDurations = new CustomCheckBox("Use custom durations", true);
		melodyCustomDurationsRandomWeighting = new CustomCheckBox(
				"Apply random weighting to custom durations", true);
		melodyCustomDurationsStrictMode = new CustomCheckBox(
				"Apply custom durations strictly (strict mode)", true);
		blockChoicePanel.add(new JLabel("<html>Melody Block Choice<br>Preferred Order</html>"));
		blockChoicePanel.add(melodyBlockChoicePreference);
		melodyGenerationSettingsPanel.add(blockChoicePanel);
		JPanel customDurationsPanel = new JPanel(new GridLayout(0, 2));
		customDurationsPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
		customDurationsPanel.add(melodyUseCustomDurations);
		customDurationsPanel.add(melodyCustomDurationsRandomWeighting);
		customDurationsPanel.add(melodyCustomDurationsStrictMode);
		melodyGenerationSettingsPanel.add(customDurationsPanel);
	}

	public JPanel initMelody() {
		return melodyParentPanel;
	}

	public void generateInitialMelodyPanels() {
		for (int i = 0; i < 3; i++) {
			MelodyPanel melodyPanel = context.addMelodyPanel();
			melodyPanel.setInstrument(73);
			melodyPanel.setOrderAndOffset(i + 1, i + 1);
			if (i > 0) {
				melodyPanel.setAccents(50);
				melodyPanel.setFillPauses(true);
				melodyPanel.setSpeed(0);
				melodyPanel.setPauseChance(70);
				if (i > 1) {
					melodyPanel.setMuteInst(true);
				}
				melodyPanel.setVelocityMax(70);
				melodyPanel.setVelocityMin(40);
				melodyPanel.setMidiChannel(i + 6);
				melodyPanel.setTranspose(i % 2 == 1 ? 0 : -12);
				melodyPanel.setPanByOrder(3);
				melodyPanel.getVolSlider().setValue(45);
			} else {
				melodyPanel.setAccents(75);
				melodyPanel.setFillPauses(false);
				melodyPanel.setSpeed(15);
				melodyPanel.setPauseChance(15);
				melodyPanel.setTranspose(12);
				melodyPanel.setVelocityMax(100);
				melodyPanel.setVelocityMin(50);
				melodyPanel.getVolSlider().setValue(60);
				melodyPanel.setNoteLengthMultiplier(108);
			}
		}
	}

	public void randomizeMelodySeeds() {
		List<? extends InstPanel> affectedPanels = context.getAffectedPanels(0);
		Random random = new Random();
		int melodySeed = random.nextInt();
		affectedPanels.forEach(panel -> panel.setVisible(false));
		if (!randomMelodySameSeed.isSelected()) {
			affectedPanels.forEach(panel -> {
				if (!panel.getLockInst()) {
					panel.setPatternSeed(random.nextInt());
				}
			});
		} else {
			affectedPanels.forEach(panel -> {
				if (!panel.getLockInst()) {
					panel.setPatternSeed(melodySeed);
				}
			});
		}
		affectedPanels.forEach(panel -> panel.setVisible(true));
	}

	public void createRandomMelodyPanels(int panelCount, boolean onlyAdd) {
		createRandomMelodyPanels(new Random().nextInt(), panelCount, onlyAdd, null);
	}

	public void createRandomMelodyPanels(int seed, int panelCount, boolean onlyAdd,
			MelodyPanel randomizedPanel) {
		ScrollComboBox.discardInteractions();
		List<MelodyPanel> affectedMelodies = (List<MelodyPanel>) (List<?>) context.getAffectedPanels(0);

		Random panelGenerator = new Random(seed);
		List<MelodyPanel> removedPanels = new ArrayList<>();
		List<MelodyPanel> remainingPanels = new ArrayList<>();
		for (java.util.Iterator<MelodyPanel> panelI = affectedMelodies.iterator(); panelI.hasNext();) {
			MelodyPanel panel = panelI.next();
			if (!onlyAdd && !panel.getLockInst()) {
				if (removedPanels.size() >= panelCount) {
					((JPanel) melodyScrollPane.getViewport().getView()).remove(panel);
					panelI.remove();
				} else {
					removedPanels.add(panel);
				}
			} else {
				remainingPanels.add(panel);
			}
		}
		Collections.sort(removedPanels, java.util.Comparator.comparing(MelodyPanel::getPanelOrder));
		panelCount -= remainingPanels.size();
		ChordSpanFill[] melodyFills = { ChordSpanFill.ALL, ChordSpanFill.ALL, ChordSpanFill.EVEN,
				ChordSpanFill.ODD, ChordSpanFill.HALF1, ChordSpanFill.HALF2 };
		for (int panelIndex = 0; panelIndex < panelCount; panelIndex++) {
			boolean needNewChannel = false;
			MelodyPanel panel;
			if (randomizedPanel != null) {
				panel = randomizedPanel;
			} else if (panelIndex < removedPanels.size()) {
				panel = removedPanels.get(panelIndex);
			} else {
				panel = context.addMelodyPanel();
				needNewChannel = true;
			}
			if (GenerationGUI.randomizeInstOnComposeOrGen.isSelected()) {
				panel.setInstrument(panel.getInstrumentBox().getRandomInstrument());
			}

			panel.setSpeed(panelGenerator.nextInt(25));
			panel.setMaxBlockChange(3 + panelGenerator.nextInt(5));
			panel.setNoteExceptionChance(10 + panelGenerator.nextInt(15));
			panel.setMaxNoteExceptions(panelGenerator.nextInt(2));
			panel.setLeadChordsChance(panelGenerator.nextInt(50));
			panel.setChordSpanFill(melodyFills[panelGenerator.nextInt(melodyFills.length)]);
			panel.setFillFlip(false);

			int panelOrder = panel.getPanelOrder();
			if (panelOrder > 1) {
				panel.setFillPauses(true);
				panel.setPauseChance(50 + panelGenerator.nextInt(40));
				panel.setVelocityMax(65 + panelGenerator.nextInt(20));
				panel.setVelocityMin(40 + panelGenerator.nextInt(20));
				panel.setTranspose(panelOrder % 2 == 0 ? 0 : -12);
				panel.setPanByOrder(3);
				panel.setNoteLengthMultiplier(70 + panelGenerator.nextInt(40));
			} else {
				panel.setFillPauses(panelGenerator.nextBoolean());
				panel.setPauseChance(panelGenerator.nextInt(35));
				panel.setTranspose(12);
				panel.setVelocityMax(80 + panelGenerator.nextInt(30));
				panel.setVelocityMin(50 + panelGenerator.nextInt(25));
				panel.setNoteLengthMultiplier(100 + panelGenerator.nextInt(25));
			}

			if (needNewChannel) {
				panel.setMidiChannel(Constants.TYPICAL_MIDI_CH.get(0).get((panelOrder - 1) % 3));
			}
		}
	}
}
