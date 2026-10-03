/* --------------------
* @author Vibe Historian
* ---------------------

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not,
see <https://www.gnu.org/licenses/>.
*/

package org.vibehistorian.vibecomposer.gui;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.vibehistorian.vibecomposer.*;
import org.vibehistorian.vibecomposer.Components.ArrangementTableRenderState;
import org.vibehistorian.vibecomposer.Components.CheckButton;
import org.vibehistorian.vibecomposer.Components.CollectionCellRenderer;
import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.RandomValueButton;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Components.SectionDropDownCheckButton;
import org.vibehistorian.vibecomposer.Components.SectionInfoCellRenderer;
import org.vibehistorian.vibecomposer.Helpers.PatternMap;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Helpers.UsedPattern;
import org.vibehistorian.vibecomposer.Panels.ArrangementSectionSelectorPanel;
import org.vibehistorian.vibecomposer.Panels.DetachedKnobPanel;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Popups.ArrangementGlobalVariationPopup;
import org.vibehistorian.vibecomposer.Popups.ArrangementPartInclusionPopup;
import org.vibehistorian.vibecomposer.Popups.PatternManagerPopup;
import org.vibehistorian.vibecomposer.Popups.TemporaryInfoPopup;
import org.vibehistorian.vibecomposer.Popups.VariationPopup;
import org.vibehistorian.vibecomposer.controllers.InstrumentPanelController;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;


/** Owns arrangement controls, data and operations. */
public class ArrangementGUI {
	private Arrangement arrangement;
	private Arrangement actualArrangement;
	private JPanel arrangementSettings;
	private KnobPanel arrangementVariationChance;
	private KnobPanel arrangementPartVariationChance;
	private CheckButton manualArrangement;
	private JTextField pieceLength;
	private RandomValueButton arrangementSeed;
	private CheckButton useArrangement;
	private JCheckBox randomizeArrangementOnCompose;
	public static final String GLOBAL = "Global";
	private ArrangementSectionSelectorPanel arrSection;
	private JScrollPane arrSectionPane;
	private boolean switchTabPaneAfterApply;
	private boolean switchTabPaneToScoreAfterApply;
	private JPanel sectionSelectionSign;
	private ScrollComboBox<String> newSectionBox;
	private static final int arrangementRowHeaderWidth = 120;
	private JScrollPane arrangementScrollPane;
	private JScrollPane arrangementActualScrollPane;
	private JTable scrollableArrangementTable;
	private JTable scrollableArrangementActualTable;
	private JPanel actualArrangementCombinedPanel;
	private JPanel variationButtonsPanel;
	private boolean copyDragging;
	private Triple<Integer, Integer, Integer> highlightedTableCell;
	private Triple<Integer, Integer, Integer> copyDraggingOrigin;
	private Point arrangementActualTableMousePoint;
	private UsedPattern copyDraggedPattern;
	private static final Color arrangementLightModeText = new Color(220, 220, 220);
	private static final int arrangementDarkModeLowestColor = 100;
	private static final Color arrangementDarkModeText = new Color(50, 50, 50);
	private static final int arrangementLightModeHighestColor = 180;
	private JCheckBox arrangementScaleMidiVelocity;
	private JCheckBox arrangementResetCustomPanelsOnCompose;
	private VariationPopup varPopup;

	private ArrangementTableRenderState getTableRenderState() {
		return new ArrangementTableRenderState(actualArrangement, copyDragging, copyDraggingOrigin,
				highlightedTableCell, arrangementActualTableMousePoint != null);
	}

	public JCheckBox getArrangementScaleMidiVelocity() {
		if (arrangementScaleMidiVelocity == null) {
			arrangementScaleMidiVelocity = new CustomCheckBox(
					"Scale Midi Velocity in Arrangement", true);
		}
		return arrangementScaleMidiVelocity;
	}

	public JCheckBox getArrangementResetCustomPanelsOnCompose() {
		if (arrangementResetCustomPanelsOnCompose == null) {
			arrangementResetCustomPanelsOnCompose = SwingUtils.makeCheckBox(
					"Reset Customized Panels on Compose", true, true);
		}
		return arrangementResetCustomPanelsOnCompose;
	}

	public void saveToConfig(GUIConfig gc, boolean isNew, int seed, List<PatternMap> activePatternMaps) {
		preparePartMaps(arrangement);
		preparePartMaps(actualArrangement);
		arrangement.setPreviewChorus(!useArrangement.isSelected());
		arrangement.setFromTable(scrollableArrangementTable);
		boolean overrideSuccessful = manualArrangement.isSelected()
				&& actualArrangement.setFromActualTable(scrollableArrangementActualTable, false);
		arrangement.setOverridden(overrideSuccessful);
		PatternMap.checkMapBounds(activePatternMaps, !overrideSuccessful,
				this::getInstrumentParts);
		if (isNew) gc.setPatternMaps(PatternMap.multiMapCopy(activePatternMaps));
		int arrangementSeedValue = arrangementSeed.getValue() != 0 ? arrangementSeed.getValue() : seed;
		arrangement.setSeed(arrangementSeedValue);
		actualArrangement.setSeed(arrangementSeedValue);
		gc.setArrangement(arrangement);
		gc.setActualArrangement(actualArrangement);
		gc.setArrangementVariationChance(arrangementVariationChance.getInt());
		gc.setArrangementPartVariationChance(arrangementPartVariationChance.getInt());
		gc.setScaleMidiVelocityInArrangement(getArrangementScaleMidiVelocity().isSelected());
		gc.setArrangementEnabled(useArrangement.isSelected());
		gc.setPieceLength(Integer.parseInt(pieceLength.getText()));
	}

	public void loadFromConfig(GUIConfig gc) {
		arrangement = gc.getArrangement();
		actualArrangement = gc.getActualArrangement();
		scrollableArrangementTable.setModel(arrangement.convertToTableModel());
		setActualModel(actualArrangement.convertToActualTableModel());
		refreshVariationPopupButtons();
		arrangementVariationChance.setInt(gc.getArrangementVariationChance());
		arrangementPartVariationChance.setInt(gc.getArrangementPartVariationChance());
		getArrangementScaleMidiVelocity().setSelected(gc.isScaleMidiVelocityInArrangement());
		arrangementSeed.setValue(arrangement.getSeed());
		useArrangement.setSelected(gc.isArrangementEnabled());
		manualArrangement.setSelected(true);
		pieceLength.setText(String.valueOf(gc.getPieceLength()));
	}

	public void recalculatePartMapsAfterPartsLoaded() {
		preparePartMaps(arrangement);
		preparePartMaps(actualArrangement);
		scrollableArrangementTable.setModel(arrangement.convertToTableModel());
		setActualModel(actualArrangement.convertToActualTableModel());
	}

	private final Context context;
	private final PlaybackController playbackController;
	private final InstrumentPanelController panelController;
	private final ChordGUI chordGUI;
	private final JTabbedPane instrumentTabPane;
	private final MidiEditorSession midiEditorSession;

	public ArrangementGUI(Context context, PlaybackController playbackController,
			InstrumentPanelController panelController,
			ChordGUI chordGUI, JTabbedPane instrumentTabPane,
			MidiEditorSession midiEditorSession) {
		this.context = context;
		this.playbackController = playbackController;
		this.panelController = panelController;
		this.chordGUI = chordGUI;
		this.instrumentTabPane = instrumentTabPane;
		this.midiEditorSession = midiEditorSession;
	}

	public boolean isCustomSection() {
		return arrSection != null && arrSection.getSelectedIndex() != 0
				&& !GLOBAL.equals(arrSection.getVal());
	}

	public boolean isDefaultSectionSelected() {
		return arrSection != null && arrSection.getSelectedIndex() == 0;
	}

	public boolean isGlobalSectionSelected() {
		return arrSection != null && GLOBAL.equals(arrSection.getVal());
	}

	public void selectSectionAndRepaint(int sectionIndex) {
		arrSection.setSelectedIndex(sectionIndex);
		arrSection.getButtons().forEach(Component::repaint);
		arrSection.repaint();
	}

	public void resetSectionSelection() {
		arrSection.setSelectedIndex(0);
	}

	public void setSectionSelectorVisible(boolean visible) {
		arrSection.setVisible(visible);
	}

	public void repaintSectionSelectorPane() {
		arrSectionPane.repaint();
	}

	public JScrollPane getArrangementScrollPane() {
		return arrangementScrollPane;
	}

	public JScrollPane getArrangementActualScrollPane() {
		return arrangementActualScrollPane;
	}

	public void repaintActualArrangementTable() {
		if (scrollableArrangementActualTable != null) {
			scrollableArrangementActualTable.repaint();
		}
	}

	private int calculateSectionMeasureStart(int sectionIndex) {
		if (actualArrangement == null || actualArrangement.getSections() == null
				|| PlaybackState.sliderMeasureStartTimes == null
				|| PlaybackState.sliderMeasureStartTimes.isEmpty()
				|| sectionIndex < 0 || sectionIndex > actualArrangement.getSections().size()) {
			return 0;
		}
		List<Section> sections = actualArrangement.getSections();
		int measureCounter = 0;
		for (int i = 1; i < sections.size() && i < sectionIndex; i++) {
			measureCounter += sections.get(i).getMeasures();
		}
		return OMNI.clamp(measureCounter, 0, PlaybackState.sliderMeasureStartTimes.size() - 1);
	}

	public Pair<MidiUtils.ScaleMode, Integer> keyChangeAt(int sectionIndex,
														  MidiUtils.ScaleMode initialMode) {
		if (actualArrangement == null || actualArrangement.getSections() == null || sectionIndex < 0
				|| sectionIndex >= actualArrangement.getSections().size()) {
			return null;
		}

		MidiUtils.ScaleMode lastMode = initialMode;
		int lastKeyChange = 0;
		for (int i = 0; i < sectionIndex; i++) {
			Section section = actualArrangement.getSections().get(i);
			if (section.isSectionVar(4)) {
				SectionConfig sectionConfig = section.getSecConfig();
				lastMode = sectionConfig.getCustomScale() != null
						? sectionConfig.getCustomScale() : lastMode;
				lastKeyChange = sectionConfig.getCustomKeyChange() != null
						? sectionConfig.getCustomKeyChange() : lastKeyChange;
			}
		}
		return Pair.of(lastMode, lastKeyChange);
	}

	public Arrangement getArrangement() {
		return arrangement;
	}

	public Arrangement getActualArrangement() {
		return actualArrangement;
	}

	public void requestSwitchTabPaneToScoreAfterApply() {
		switchTabPaneToScoreAfterApply = true;
	}

	public JCheckBox getRandomizeArrangementOnCompose() {
		return randomizeArrangementOnCompose;
	}

	public JPanel getSectionSelectionSign() {
		return sectionSelectionSign;
	}

	public boolean isManualArrangementSelected() {
		return manualArrangement.isSelected();
	}

	public void setManualArrangementSelected(boolean selected) {
		manualArrangement.setSelected(selected);
		manualArrangement.repaint();
	}

	public boolean isArrangementEnabled() {
		return useArrangement.isSelected();
	}

	public int getConfiguredPieceLength() {
		return Integer.parseInt(pieceLength.getText());
	}

	public void clearArrangementSeed() {
		arrangementSeed.setValue(0);
	}

	public void trySliderStartChange(int sectionIndex) {
		if (ExtraSettingsGUI.moveStartToCustomizedSection == null
				|| !ExtraSettingsGUI.moveStartToCustomizedSection.isSelected()
				|| PlaybackState.sliderMeasureStartTimes == null) {
			return;
		}
		int measure = calculateSectionMeasureStart(sectionIndex);
		playbackController.setSliderStart(PlaybackState.sliderMeasureStartTimes.get(measure));
	}

	private List<? extends InstPanel> getInstList(int instrument) {
		return panelController.getInstList(INST.fromIndex(instrument));
	}

	private List<? extends InstPart> getInstrumentParts(int instrument) {
		List<InstPart> parts = panelController.getPartsFromPanels(getInstList(instrument), false,
				context.getLastRandomSeed());
		InstPart.sortParts(parts);
		return parts;
	}

	public void refreshPartMapsFromOldData() {
		if (actualArrangement != null && actualArrangement.getSections() != null) {
			actualArrangement.getSections().forEach(section ->
					section.initPartMapFromOldData(this::getInstrumentParts));
		}
	}

	private JScrollPane getInstPane(int instrument) {
		return panelController.getInstPane(INST.fromIndex(instrument));
	}

	private List<InstPart> getInstPartsFromCustomSectionInstPanels(INST instrument) {
		JPanel panePanel = (JPanel) panelController.getInstPane(instrument)
				.getViewport().getView();
		List<InstPanel> panels = new ArrayList<>();
		int lastSeed = context.getLastRandomSeed();
		int seed = lastSeed == 0 ? context.getCurrentSeed() : lastSeed;
		for (Component component : panePanel.getComponents()) {
			if (component instanceof InstPanel) {
				panels.add((InstPanel) component);
			}
		}
		return panelController.getPartsFromPanels(panels, false, seed);
	}

	/** Supplies the cross-tab work that belongs to the main window. */
	public interface Context {
		JButton makeButton(String name, String actionCommand, int width, int height);
		void recalculateTabPaneCounts();
		void regenerate();
		void openApplyCustomSectionPopup();
		void toggleButtonEnabledForPanels();
		void addArrangementComponents(JComponent sectionPane, JComponent settings,
				int startY, int anchorSide);
		Point getVariationPopupLocation();
		Dimension getVariationPopupWindowSize();
		GUIConfig getSelectedConfigHistory();
		void recalculateAfterSectionRecompose();
		void regenerateAfterSectionRecomposeIfEnabled();
		int getCurrentSeed();
		int getLastRandomSeed();
		boolean canRegenerateOnChange();
		GUIConfig getGUIConfig();
	}

	private void preparePartMaps(Arrangement target) {
		if (target != null && target.getSections() != null) {
			target.getSections().forEach(this::preparePartMap);
		}
	}

	private void preparePartMap(Section section) {
		section.initPartMapIfNull(this::getInstrumentParts);
		section.recalculatePartVariationMapBoundsIfNeeded(this::getInstrumentParts);
	}

	public void recomposeSection() {
		if (arrSection == null || arrSection.getSelectedIndex() == 0
				|| GLOBAL.equals(arrSection.getVal())) {
			return;
		}
		manualArrangement.setSelected(true);
		for (INST instrument : INST.values()) {
			int instrumentIndex = instrument.getIndex();
			panelController.createRandomPanels(instrument,
					getInstList(instrumentIndex).size(), false);
			applyCustomPanelsToSection("", instrumentIndex, arrSection.getSelectedIndex());
		}
		arrSection.getCurrentButton().repaint();
		context.recalculateAfterSectionRecompose();
		context.regenerateAfterSectionRecomposeIfEnabled();
	}

	public void replaceSection() {
		GUIConfig sectionGuiConfig = context.getSelectedConfigHistory();
		if (sectionGuiConfig == null || arrSection.getSelectedIndex() <= 0) {
			return;
		}
		Section currentSection = actualArrangement.getSections().get(arrSection.getSelectedIndex() - 1);
		for (INST instrument : INST.values()) {
			int instrumentIndex = instrument.getIndex();
			currentSection.setInstPartList(sectionGuiConfig.getInstPartList(instrumentIndex), instrumentIndex);
		}
		currentSection.setCustomChords(sectionGuiConfig.getCustomChords());
		currentSection.setCustomDurations(sectionGuiConfig.getCustomChordDurations());
		currentSection.setCustomChordsEnabled(true);
		if (!"4,4,4,4".equals(currentSection.getCustomDurations())) {
			currentSection.setCustomDurationsEnabled(true);
		}
		arrSection.getCurrentButton().repaint();
		switchPanelsForSectionSelection(arrSection.getVal());
	}

	public void resetSectionSelectionAfterGeneration() {
		SwingUtilities.invokeLater(() -> {
			int sectionIndex = arrSection.getSelectedIndex();
			setActualModel(actualArrangement.convertToActualTableModel());
			if (sectionIndex != 0 && sectionIndex < arrSection.getItemCount()) {
				arrSection.setSelectedIndex(sectionIndex);
			} else {
				arrSection.setSelectedIndex(0);
			}
			refreshVariationPopupButtons();
			arrSection.getButtons().forEach(Component::repaint);
			arrSection.repaint();
		});
	}

	public void applyGeneratedArrangement(Arrangement generatedArrangement, GUIConfig config) {
		actualArrangement = new Arrangement();
		actualArrangement.setPreviewChorus(false);
		actualArrangement.getSections().clear();
		for (Section section : generatedArrangement.getSections()) {
			actualArrangement.getSections().add(section.deepCopy());
		}
		config.setActualArrangement(actualArrangement);
	}

	public void prepareForCompose(boolean regenerate, boolean hasCurrentMidi, int seed) {
		if (!regenerate && getArrangementResetCustomPanelsOnCompose().isSelected()) {
			actualArrangement.getSections().forEach(Section::resetCustomizedParts);
		} else {
			for (Section section : actualArrangement.getSections()) {
				for (INST instrument : INST.values()) {
					int instrumentIndex = instrument.getIndex();
					List<?> parts = section.getInstPartList(instrumentIndex);
					if (parts != null && parts.size() > getInstList(instrumentIndex).size()) {
						section.resetCustomizedParts(instrumentIndex);
					}
				}
			}
		}

		if (!regenerate && randomizeArrangementOnCompose.isSelected()) {
			handleArrangementAction("ArrangementRandomize", seed,
					Integer.parseInt(pieceLength.getText()));
		}

		boolean preserveArrangement = (regenerate || !randomizeArrangementOnCompose.isSelected()) && hasCurrentMidi
				&& manualArrangement.isSelected();
		arrangement.setOverridden(preserveArrangement);
	}

	public void applyCustomPanelsToSection(String action, int replacedPartNum,
			Integer sectionOrder) {
		int lastSectionOrder = sectionOrder + 1;
		if (action.endsWith("+")) {
			lastSectionOrder = actualArrangement.getSections().size() + 1;
		} else if (action.contains(",")) {
			lastSectionOrder = Integer.parseInt(action.split(",")[1]) + 1;
		}

		for (int i = sectionOrder; i < lastSectionOrder; i++) {
			Section section = actualArrangement.getSections().get(i - 1);
			if (replacedPartNum >= 0 && replacedPartNum < 5) {
				section.setInstPartList(getInstPartsFromCustomSectionInstPanels(
						INST.fromIndex(replacedPartNum)),
						replacedPartNum);
			}
			if (replacedPartNum < 5) {
				String suffix = section.hasCustomizedParts() ? "*" : "";
				arrSection.getButtons().get(i)
						.setText(i + ": " + section.getType() + suffix);
			}
		}
	}

	protected Triple<Integer, Integer, Integer> calculateCurrentTableSubcell(MouseEvent event) {
		int row = scrollableArrangementActualTable.rowAtPoint(event.getPoint());
		int sectionOrder = scrollableArrangementActualTable.columnAtPoint(event.getPoint());
		if (row >= 2 && sectionOrder >= 0) {
			int instrument = row - 2;
			double orderPercentage = calculateMousePointPercentageInTable(row, sectionOrder);
			int actualSize = getInstList(instrument).size();
			int visualSize = Math.max(CollectionCellRenderer.MIN_CELLS + 1, actualSize + 1);
			int panelOrder = (int) Math.floor(orderPercentage * visualSize);
			if ((actualSize > CollectionCellRenderer.MIN_CELLS && panelOrder == actualSize)
					|| (actualSize <= CollectionCellRenderer.MIN_CELLS
							&& panelOrder == CollectionCellRenderer.MIN_CELLS)
					|| panelOrder >= actualSize) {
				return null;
			}
			return Triple.of(instrument, panelOrder, sectionOrder);
		}
		return null;
	}

	public void switchPanelsForSectionSelection(String selectedItem) {
		List<InstPanel> addedPanels = new ArrayList<>();
		if (GLOBAL.equals(selectedItem)) {
			LG.i("Resetting to normal panels!");
			sectionSelectionSign.setBackground(UITheme.panelColorHigh.brighter());
			for (INST instrument : INST.values()) {
				int instrumentIndex = instrument.getIndex();
				JScrollPane pane = getInstPane(instrumentIndex);
				List<? extends InstPanel> panels = getInstList(instrumentIndex);
				JPanel panelView = (JPanel) pane.getViewport().getView();
				for (Component component : panelView.getComponents()) {
					if (component instanceof InstPanel) panelView.remove(component);
				}
				for (InstPanel panel : panels) {
					panelView.add(panel);
					panel.setVisible(false);
				}
				addedPanels.addAll(panels);
			}
		} else {
			LG.i("Switching panels!");
			sectionSelectionSign.setBackground(UITheme.uiColor().darker().darker());
			int sectionOrder = Integer.parseInt(selectedItem.split(":")[0]) - 1;
			Section section = actualArrangement.getSections().get(sectionOrder);
			for (INST instrument : INST.values()) {
				int instrumentIndex = instrument.getIndex();
				JScrollPane pane = getInstPane(instrumentIndex);
				JPanel panelView = (JPanel) pane.getViewport().getView();
				List<InstPanel> sectionPanels = new ArrayList<>();
				List<Integer> missingPanels = new ArrayList<>();
				getInstList(instrumentIndex)
						.forEach(panel -> missingPanels.add(panel.getPanelOrder()));
				List<? extends InstPart> sectionParts = section.getInstPartList(instrumentIndex);
				if (sectionParts != null) {
					for (Component component : panelView.getComponents()) {
						if (component instanceof InstPanel) {
							int order = ((InstPanel) component).getAbsoluteOrder();
							if (order < sectionParts.size()) {
								panelView.remove(component);
								InstPanel copy = panelController.setupInstrumentPanel(instrument);
								copy.setFromInstPart(sectionParts.get(order));
								sectionPanels.add(copy);
								missingPanels.remove(Integer.valueOf(order));
							}
						}
					}
				}
				if (!missingPanels.isEmpty()) {
					List<? extends InstPanel> panels = new ArrayList<>(getInstList(instrumentIndex))
							.stream().filter(panel -> missingPanels.contains(panel.getPanelOrder()))
							.collect(java.util.stream.Collectors.toList());
					for (Component component : panelView.getComponents()) {
						if (component instanceof InstPanel) {
							InstPanel panel = (InstPanel) component;
							int order = panel.getPanelOrder();
							if (missingPanels.contains(order)) {
								panelView.remove(panel);
								InstPanel source = panels.stream()
										.filter(candidate -> candidate.getPanelOrder() == order)
										.findFirst().get();
								InstPanel copy = panelController.setupInstrumentPanel(instrument);
								copy.setRelatedSection(section);
								copy.setFromInstPart(source.toInstPart(0));
								sectionPanels.add(copy);
							}
						}
					}
				}
				sectionPanels.sort(java.util.Comparator.comparing(InstPanel::getPanelOrder));
				for (InstPanel panel : sectionPanels) {
					panel.toggleEnabledCopyRemove(false);
					panel.toggleGlobalElements(false);
					if (panel.getPartClass() == DrumPart.class) {
						panel.getInstrumentBox().setEnabled(true);
					}
					panel.getToggleableComponents()
							.forEach(component -> component.setVisible(UITheme.isFullMode));
					panel.setVisible(false);
					panelView.add(panel);
				}
				addedPanels.addAll(sectionPanels);
			}
		}
		sectionSelectionSign.repaint();
		addedPanels.forEach(panel -> panel.setVisible(true));
		context.toggleButtonEnabledForPanels();
		for (INST instrument : INST.values()) {
			getInstPane(instrument.getIndex()).repaint();
		}
		if (instrumentTabPane.getSelectedIndex() == 6) {
			actualArrangement.getSections().forEach(section ->
					section.initPartMapFromOldData(this::getInstrumentParts));
			scrollableArrangementActualTable.repaint();
		}
	}

	private double calculateMousePointPercentageInTable(int row, int sectionOrder) {
		Point mousePoint = SwingUtils.getMouseLocation();
		Point tablePoint = scrollableArrangementActualTable.getLocation();
		SwingUtilities.convertPointToScreen(tablePoint, scrollableArrangementActualTable);
		Rectangle cell = scrollableArrangementActualTable.getCellRect(row, sectionOrder, false);
		mousePoint.x -= tablePoint.x + cell.x;
		mousePoint.y -= tablePoint.y + cell.y;
		return OMNI.clamp(mousePoint.x / (double) cell.width, 0.01, 0.99);
	}

	public void processActualArrangementMouseEvent(MouseEvent event) {
		int row = scrollableArrangementActualTable.rowAtPoint(event.getPoint());
		int sectionOrder = scrollableArrangementActualTable.columnAtPoint(event.getPoint());
		LG.d("Clicked! " + row + ", " + sectionOrder);
		boolean rightClick = SwingUtilities.isRightMouseButton(event);
		boolean middleClick = !rightClick && SwingUtilities.isMiddleMouseButton(event);
		if (row == 0 && sectionOrder >= 0) {
			if (rightClick) handleArrangementAction("ArrangementRemove," + sectionOrder, 0, 0);
			else if (middleClick) handleArrangementAction("ArrangementAdd," + sectionOrder, 0, 0);
			return;
		}
		if (row < 2 || sectionOrder < 0) return;

		double orderPercentage = calculateMousePointPercentageInTable(row, sectionOrder);
		int instrument = row - 2;
		List<? extends InstPanel> panels = getInstList(instrument);
		int actualSize = panels.size();
		int visualSize = Math.max(CollectionCellRenderer.MIN_CELLS + 1, actualSize + 1);
		int partOrder = (int) Math.floor(orderPercentage * visualSize);
		LG.d("Percentage: " + orderPercentage);
		LG.d("Selected subcell: " + (partOrder + 1));
		boolean randomizerButtonPressed = (actualSize > CollectionCellRenderer.MIN_CELLS
				&& partOrder == actualSize) || (actualSize <= CollectionCellRenderer.MIN_CELLS
						&& partOrder == CollectionCellRenderer.MIN_CELLS);
		if (!randomizerButtonPressed && partOrder >= actualSize) {
			LG.d("Can't interact: subcell not present in part - " + (partOrder + 1));
			return;
		}
		if (!rightClick && !middleClick) {
			if (randomizerButtonPressed) return;
			int panelOrder = panels.get(partOrder).getPanelOrder();
			if ((event.getModifiersEx() & MouseEvent.ALT_DOWN_MASK) != 0) {
				if (sectionOrder + 1 < arrSection.getItemCount()) {
					arrSection.setSelectedIndexWithProperty(sectionOrder + 1, true);
					arrSection.repaint();
					instrumentTabPane.setSelectedIndex(instrument);
					switchTabPaneAfterApply = true;
				}
			} else if (event.isControlDown()) {
				Section section = actualArrangement.getSections().get(sectionOrder);
				preparePartMap(section);
				if (section.getPresence(instrument).contains(panelOrder)
						&& section.containsPattern(instrument, panelOrder)) {
					copyDraggingOrigin = Triple.of(instrument, partOrder, sectionOrder);
					prepareCustomMidiSubcellCopy(instrument, panelOrder, section);
				}
			} else if ((PlaybackState.currentMidi != null)) {
				Section section = actualArrangement.getSections().get(sectionOrder);
				if (section.getPresence(instrument).contains(panelOrder)
						&& section.containsPattern(instrument, panelOrder)) {
					midiEditorSession.open(section, instrument, panelOrder, sectionOrder);
				} else {
					LG.i("Presence: " + section.getPresence(instrument).contains(panelOrder)
							+ ", contains pattern: " + section.containsPattern(instrument, panelOrder));
				}
			}
			return;
		}

		Section section = actualArrangement.getSections().get(sectionOrder);
		preparePartMaps(actualArrangement);
		boolean hasPresence = !section.getPresence(instrument).isEmpty();
		boolean hasVariation = hasPresence && section.hasVariation(instrument);
		if (event.isControlDown()) {
			if (hasPresence) {
				int targetOrder = sectionOrder;
				if (middleClick) {
					targetOrder++;
					if (targetOrder >= actualArrangement.getSections().size()) return;
				} else if (rightClick) {
					targetOrder--;
					if (targetOrder < 0) return;
				}
				Section target = actualArrangement.getSections().get(targetOrder);
				target.resetAllPresence(instrument);
				for (int i = 2; i < Section.variationDescriptions[instrument].length; i++) {
					target.removeVariationForAllParts(instrument, i);
				}
				for (Integer panel : section.getPresence(instrument)) {
					int absoluteOrder = panelController.getAbsoluteOrder(INST.fromIndex(instrument), panel);
					target.setPresence(instrument, absoluteOrder);
					target.setVariation(instrument, absoluteOrder,
							section.getVariation(instrument, absoluteOrder));
					if (event.isShiftDown()) {
						UsedPattern pattern = section.getPattern(instrument, panel);
						if (pattern != null) {
							PhraseNotes notes = context.getGUIConfig().getPatternRaw(pattern);
							if (notes != null && notes.isApplied()) target.putPattern(instrument, panel, pattern);
						}
					}
				}
				target.setInstPartList(section.getInstPartList(instrument), instrument);
			}
		} else if (randomizerButtonPressed) {
			if (middleClick) {
				if (hasVariation) {
					for (int i = 2; i < Section.variationDescriptions[instrument].length; i++) {
						section.removeVariationForAllParts(instrument, i);
					}
				} else if (hasPresence) section.generateVariations(new Random(), instrument,
						arrangementPartVariationChance.getInt(), this::getInstrumentParts);
			} else if (hasPresence) {
				for (int i = 0; i < panels.size(); i++) section.resetPresence(instrument, i);
			} else {
				arrangement.initPartInclusionMapIfNull(this::getInstrumentParts);
				section.generatePresences(new Random(), instrument, arrangement.getInclMap(), true,
						this::getInstrumentParts);
			}
		} else if (event.isShiftDown()) {
			int panelOrder = panels.get(partOrder).getPanelOrder();
			boolean hasAnyPresence = actualArrangement.getSections().stream()
					.anyMatch(item -> item.getPresence(instrument).contains(panelOrder));
			if (middleClick) {
				boolean hasAnyVariation = hasAnyPresence && actualArrangement.getSections().stream()
						.anyMatch(item -> !item.getVariation(instrument, partOrder).isEmpty());
				for (Section item : actualArrangement.getSections()) {
					if (hasAnyVariation) {
						for (int i = 2; i < Section.variationDescriptions[instrument].length; i++)
							item.removeVariationForPart(instrument, partOrder, i);
					} else if (hasAnyPresence && item.getPresence(instrument).contains(panelOrder)) {
						item.generateVariationForPartAndOrder(new Random(), instrument, partOrder, false,
								arrangementPartVariationChance.getInt(), this::getInstrumentParts);
					}
				}
			} else if (hasAnyPresence) {
				for (Section item : actualArrangement.getSections()) {
					item.initPartMapFromOldData(this::getInstrumentParts);
					for (int i = 0; i < panels.size(); i++) item.resetPresence(instrument, partOrder);
				}
			} else {
				arrangement.initPartInclusionMapIfNull(this::getInstrumentParts);
				for (Section item : actualArrangement.getSections()) {
					item.initPartMapFromOldData(this::getInstrumentParts);
					if (new Random().nextInt(100) < item.getChanceForInst(instrument))
						item.setPresence(instrument, partOrder);
				}
			}
		} else {
			int panelOrder = panels.get(partOrder).getPanelOrder();
			boolean hasSinglePresence = section.getPresence(instrument).contains(panelOrder);
			boolean hasSingleVariation = hasSinglePresence
					&& !section.getVariation(instrument, partOrder).isEmpty();
			if (middleClick) {
				if (hasSingleVariation) {
					for (int i = 2; i < Section.variationDescriptions[instrument].length; i++)
						section.removeVariationForPart(instrument, partOrder, i);
				} else if (hasSinglePresence) {
					section.generateVariationForPartAndOrder(new Random(), instrument, partOrder, true,
							arrangementPartVariationChance.getInt(), this::getInstrumentParts);
				}
			} else {
				preparePartMap(section);
				section.initPartMapFromOldData(this::getInstrumentParts);
				if (hasSinglePresence) section.resetPresence(instrument, partOrder);
				else section.setPresence(instrument, partOrder);
			}
		}
		setActualModel(actualArrangement.convertToActualTableModel(), false);
		refreshVariationPopupButtons();
		manualArrangement.setSelected(true);
		manualArrangement.repaint();
		scrollableArrangementActualTable.repaint();
	}

	private void prepareCustomMidiSubcellCopy(int instrument, int panelOrder, Section section) {
		copyDragging = true;
		copyDraggedPattern = section.getPattern(instrument, panelOrder);
		scrollableArrangementActualTable.repaint();
	}

	public void processActualArrangementCopyDragging(MouseEvent event) {
		Triple<Integer, Integer, Integer> target = calculateCurrentTableSubcell(event);
		if (target == null) {
			LG.i("Can't copy custom midi - invalid part!");
			return;
		}
		PhraseNotes notes = context.getGUIConfig().getPatternRaw(copyDraggedPattern);
		if (notes == null) {
			new TemporaryInfoPopup("Invalid pattern for copying!", 1500);
			return;
		}
		Section section = actualArrangement.getSections().get(target.getRight());
		int instrument = target.getLeft();
		int panelOrder = getInstList(instrument).get(target.getMiddle()).getPanelOrder();
		section.putPattern(instrument, panelOrder, copyDraggedPattern);
		if (!section.getPresence(instrument).contains(panelOrder))
			section.setPresence(instrument, target.getMiddle());
		notes.setApplied(true);
		setActualModel(actualArrangement.convertToActualTableModel(), false);
		refreshVariationPopupButtons();
		manualArrangement.setSelected(true);
		manualArrangement.repaint();
		scrollableArrangementActualTable.repaint();
	}

	public void resetCopyDrag() {
		copyDragging = false;
		copyDraggedPattern = null;
		copyDraggingOrigin = null;
		scrollableArrangementActualTable.repaint();
	}

	protected void arrangementTableProcessSectionType(Component component, String value) {
		int typeOffset = Section.getTypeMelodyOffset(value);
		component.setBackground(new Color(100 + 15 * typeOffset, 150, 150));
	}

	private void arrangementTableProcessComponent(Component component, int row, int column,
			String value, int[] maxCounts, boolean actual) {
		if (row < 2) {
			component.setBackground(new Color(100, 150, 150));
			return;
		}
		if (value.isEmpty() || value.equalsIgnoreCase("*")) {
			component.setBackground(UITheme.panelColorLow.darker());
			return;
		}
		int count = actual ? StringUtils.countMatches(value, ",") + 1 : Integer.parseInt(value);
		int color;
		if (UITheme.isDarkMode) {
			color = arrangementDarkModeLowestColor + (70 * count) / maxCounts[row];
			color = Math.min(color, 170);
		} else {
			color = arrangementLightModeHighestColor - (70 * count) / maxCounts[row];
			color = Math.max(color, 130);
		}
		int extraRed = 0;
		if (actual && actualArrangement.getSections().size() > column) {
			double remaining = 255 - color - 1;
            extraRed = (int) (extraRed + actualArrangement.getSections().get(column)
                    .countVariationsForPartType(row - 2) * remaining);
			extraRed = Math.min(255 - color - 1, extraRed);
		}
		component.setBackground(new Color(color + extraRed, color, color));
	}

	public void refreshVariationPopupButtons() {
		int count = actualArrangement.getSections().size();
		variationButtonsPanel.removeAll();
		for (int i = 0; i < count; i++) {
			int sectionIndex = i;
			JButton button = new JButton("Edit " + (i + 1)) {
				private static final long serialVersionUID = -374920351085418730L;

				@Override public void paintComponent(Graphics graphics) {
					super.paintComponent(graphics);
					if (actualArrangement == null || actualArrangement.getSections() == null
							|| sectionIndex >= actualArrangement.getSections().size()
							|| !(graphics instanceof Graphics2D)) return;
					Graphics2D g = (Graphics2D) graphics;
					Section section = actualArrangement.getSections().get(sectionIndex);
					List<Integer> sectionVars = section.getSectionVariations();
					if (sectionVars == null) sectionVars = Section.EMPTY_SECTION_VARS;
					int xsizeForIcon = Math.max(16,
							(this.getWidth() / Section.sectionVariationNames.length) - 2);
					int currentX = 8;
					for (int j = 0; j < (Section.sectionVariationNames.length + 1) / 2; j++) {
						if (sectionVars.get(j) > 0 || (j == 1 && section.isCustomChordsEnabled())) {
							g.drawImage(GUIAssets.SECTION_VARIATIONS_ICONS.get(j), currentX, 6, this);
						}
						currentX += xsizeForIcon + 2;
					}
					if (section.getTransitionType() > 0) {
						g.drawImage(GUIAssets.SECTION_TRANSITION_ICONS
								.get(section.getTransitionType() - 1), this.getWidth() - 18, 6, this);
					}
					currentX = 8;
					for (int j = (Section.sectionVariationNames.length + 1) / 2;
							j < Section.sectionVariationNames.length; j++) {
						if (sectionVars.get(j) > 0) {
							g.drawImage(GUIAssets.SECTION_VARIATIONS_ICONS.get(j), currentX,
									this.getHeight() * 3 / 4 - 6, this);
						}
						currentX += xsizeForIcon + 2;
					}
				}
			};
			button.addActionListener(e -> openVariationPopup(sectionIndex + 1));
			int width = Math.max(GUIConstants.TABLE_COLUMN_MIN_WIDTH,
					(UITheme.scrollPaneDimension.width - arrangementRowHeaderWidth) / count);
			button.setPreferredSize(new Dimension(width, 50));
			button.addMouseListener(new MouseAdapter() {
				@Override public void mousePressed(MouseEvent event) {
					if (SwingUtilities.isMiddleMouseButton(event)) {
						actualArrangement.getSections().get(sectionIndex)
								.setSectionVariations(new ArrayList<>());
						recolorVariationPopupButton(button,
								actualArrangement.getSections().get(sectionIndex));
					}
				}
			});
			recolorVariationPopupButton(button, actualArrangement.getSections().get(i));
			variationButtonsPanel.add(button);
		}
	}

	public void recolorAllVariationButtons() {
		for (Component component : variationButtonsPanel.getComponents()) {
			if (component instanceof JButton) {
				JButton button = (JButton) component;
				int sectionOrder = Integer.parseInt(button.getText().split(" ")[1]);
				Section section = actualArrangement.getSections().get(sectionOrder - 1);
				recolorVariationPopupButton(button, section);
			}
		}
	}

	public void recolorVariationPopupButton(int sectionOrder) {
		if (actualArrangement == null
				|| sectionOrder - 1 >= actualArrangement.getSections().size()) return;
		for (Component component : variationButtonsPanel.getComponents()) {
			if (component instanceof JButton) {
				JButton button = (JButton) component;
				if (button.getText().equals("Edit " + sectionOrder)) {
					recolorVariationPopupButton(button,
							actualArrangement.getSections().get(sectionOrder - 1));
					break;
				}
			}
		}
	}

	private void recolorVariationPopupButton(JButton button, Section section) {
		int count = section.getSectionVariations() != null
				? (int) section.getSectionVariations().stream().filter(value -> value > 0).count()
				: 0;
		count += section.isTransition() ? 1 : 0;
		count += section.isCustomChordsEnabled() ? 1 : 0;
		int color;
		int total = Section.sectionVariationNames.length + 1;
		if (UITheme.isDarkMode) {
			color = arrangementDarkModeLowestColor + (35 * count) / total;
			color = Math.min(color, 135);
		} else {
			color = arrangementLightModeHighestColor - (70 * count) / total;
			color = Math.max(color, 130);
		}
		double remaining = 255 - color - 1;
		double extraRed = Math.min(remaining, (count * remaining) / (double) total);
		button.setBackground(new Color(color + (int) (extraRed / 2), color, color));
		button.repaint();
	}

	public void initArrangementSettings(int startY, int anchorSide) {
		arrangementSettings = new JPanel();
		arrangementSettings.setOpaque(false);
		arrangementSettings.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));

		JPanel arrangementSettingsLeft = new JPanel();
		arrangementSettingsLeft.setOpaque(false);
		arrangementSettingsLeft.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		JPanel arrangementSettingsRight = new JPanel();
		arrangementSettingsRight.setOpaque(false);
		arrangementSettingsRight.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		useArrangement = new CheckButton("ARRANGE", false);
		arrangementSettingsLeft.add(useArrangement);
		pieceLength = new JTextField("12", 2);
		JButton resetArrangementBtn = context.makeButton("Reset", "ArrangementReset", 60, 30);
		JButton randomizeArrangementBtn = SwingUtils.makeButton("Randomize", e -> {
			Random arrGen = new Random();
			handleArrangementAction("ArrangementRandomize", arrGen.nextInt(),
					getConfiguredPieceLength());
			context.recalculateTabPaneCounts();
			if (context.canRegenerateOnChange()) {
				context.regenerate();
			}
		}, 90);
		JButton arrangementPartInclusionBtn = SwingUtils.makeButton("Parts",
				e -> openPartInclusionPopup(), 60);
		JButton arrangementGlobalVariationBtn = SwingUtils.makeButton("Vars",
				e -> openGlobalVariationPopup(), 50);
		JButton patternManagerBtn = SwingUtils.makeButton("Patterns", e -> openPatternManagerPopup(), 70);

		randomizeArrangementOnCompose = SwingUtils.makeCheckBox("on Compose", true, true);
		List<CheckButton> defaultButtons = new ArrayList<>();
		defaultButtons.add(new SectionDropDownCheckButton(GLOBAL, true, OMNI.alphen(Color.pink, 70),
				action -> handleArrangementAction(action, 0, 0)));
		arrSection = new ArrangementSectionSelectorPanel(new ArrayList<>(), defaultButtons,
				this::switchPanelsForSectionSelection, this::openVariationPopup,
				this::trySliderStartChange, () -> actualArrangement.getSections().size(),
				action -> handleArrangementAction(action, 0, 0));

		JButton commitPanelBtn = context.makeButton("Apply", "ArrangementApply", 50, 30);
		JButton commitAllPanelBtn = SwingUtils.makeButton("Apply..", e -> context.openApplyCustomSectionPopup(), 60);
		JButton undoPanelBtn = SwingUtils.makeButton("<-*",
				e -> arrSection.setSelectedIndexWithProperty(
						arrSection.getSelectedIndex(), true), 30);
		JButton clearPanelBtn = SwingUtils.makeButton("X*", e -> {
			if (!GLOBAL.equals(arrSection.getVal())) {
				Section sec = actualArrangement.getSections()
						.get(arrSection.getSelectedIndex() - 1);
				if (sec.hasCustomizedParts()) {
					sec.resetCustomizedParts(instrumentTabPane.getSelectedIndex());
					setActualModel(actualArrangement.convertToActualTableModel(), false);
					if (!sec.hasCustomizedParts()) {
						CheckButton cb = arrSection.getCurrentButton();
						cb.setText(cb.getText().substring(0, cb.getText().length() - 1));
						cb.repaint();
					}
					arrSection.setSelectedIndexWithProperty(
							arrSection.getSelectedIndex(), true);
				}
			}
		}, 30);
		JButton clearAllPanelsBtn = SwingUtils.makeButton("CLR*", e -> {
			actualArrangement.getSections().forEach(Section::resetCustomizedParts);
			setActualModel(actualArrangement.convertToActualTableModel(), false);
			arrSection.getButtons().forEach(cb -> {
				if (!GLOBAL.equals(cb.getText()) && cb.getText().contains("*")) {
					cb.setText(cb.getText().substring(0, cb.getText().length() - 1));
					cb.repaint();
				}
			});
			scrollableArrangementActualTable.repaint();
		}, 40);

		JButton copySelectedBtn = context.makeButton("Cc", "ArrangementAddLast", 30, 30);
		JButton removeSelectedBtn = context.makeButton("X", "ArrangementRemoveLast", 30, 30);
		newSectionBox = new ScrollComboBox<>(false);
		newSectionBox.addItem(OMNI.EMPTYCOMBO);
		for (Section.SectionType type : Section.SectionType.values()) {
			newSectionBox.addItem(type.toString());
		}
		JButton addNewSectionBtn = context.makeButton("Add", "ArrangementAddNewSection", 35, 30);

		arrangementSettingsLeft.add(randomizeArrangementBtn);
		arrangementSettingsLeft.add(randomizeArrangementOnCompose);
		arrangementSettingsLeft.add(resetArrangementBtn);
		arrangementVariationChance = new DetachedKnobPanel("Section<br>Variations", 30);
		arrangementSettingsLeft.add(arrangementVariationChance);
		arrangementPartVariationChance = new DetachedKnobPanel("Part<br>Variations", 25);
		arrangementSettingsLeft.add(arrangementPartVariationChance);
		arrangementSettingsLeft.add(arrangementPartInclusionBtn);
		arrangementSettingsLeft.add(arrangementGlobalVariationBtn);
		arrangementSettingsLeft.add(patternManagerBtn);

		sectionSelectionSign = new JPanel();
		sectionSelectionSign.add(new JLabel("                                      "));
		arrangementSettings.add(arrangementSettingsLeft);
		arrangementSettings.add(sectionSelectionSign);

		manualArrangement = new CheckButton("MANUAL", false);
		arrangementSettingsRight.add(manualArrangement);
		arrangementSettingsRight.add(commitPanelBtn);
		arrangementSettingsRight.add(commitAllPanelBtn);
		arrangementSettingsRight.add(undoPanelBtn);
		arrangementSettingsRight.add(clearPanelBtn);
		arrangementSettingsRight.add(clearAllPanelsBtn);
		arrangementSettingsRight.add(newSectionBox);
		arrangementSettingsRight.add(addNewSectionBtn);
		arrangementSettingsRight.add(copySelectedBtn);
		arrangementSettingsRight.add(removeSelectedBtn);
		arrangementSettingsRight.add(new JLabel("Seed"));
		arrangementSeed = new RandomValueButton(0, context::getCurrentSeed);
		arrangementSettingsRight.add(arrangementSeed);
		arrangementSettings.add(arrangementSettingsRight);

		arrSectionPane = new JScrollPane() {
			@Override public Dimension getPreferredSize() {
				return new Dimension(UITheme.scrollPaneDimension.width, 45);
			}
		};
		arrSectionPane.setViewportView(arrSection);
		arrSectionPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		arrSectionPane.getHorizontalScrollBar().setUnitIncrement(32);
		arrSectionPane.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		arrSectionPane.setOpaque(true);
		arrSection.setOpaque(true);
		context.addArrangementComponents(arrSectionPane,
				arrangementSettings, startY, anchorSide);

		scrollableArrangementTable = new JTable(5, 5) {
			private static final long serialVersionUID = 3846279087936376003L;
			@Override public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
				Component comp = super.prepareRenderer(renderer, row, col);
				comp.setForeground(UITheme.isDarkMode ? ArrangementGUI.arrangementDarkModeText
						: ArrangementGUI.arrangementLightModeText);
				if (getModel().getColumnCount() <= col) return comp;
				if (row == 0) {
					arrangementTableProcessSectionType(comp,
							(String) getModel().getValueAt(row, col));
					return comp;
				}
				if (row == 1) {
					comp.setBackground(new Color(100, 150, 150));
					return comp;
				}
				Object objValue = getModel().getValueAt(row,
						scrollableArrangementTable.convertColumnIndexToModel(col));
				Integer value = (objValue instanceof String) ? Integer.valueOf((String) objValue)
						: (Integer) objValue;
				if (value > 100) {
					value = 100;
					getModel().setValueAt(value, row, col);
				} else if (value < 0) {
					value = 0;
					getModel().setValueAt(value, row, col);
				}
				arrangementTableProcessComponent(comp, row, col, String.valueOf(value),
						new int[] { 0, 0, 100, 100, 100, 100, 100 }, false);
				return comp;
			}
		};

		arrangement = new Arrangement();
		actualArrangement = new Arrangement();
		arrangement.generateDefaultArrangement();
		scrollableArrangementTable.setModel(arrangement.convertToTableModel());
		arrangementScrollPane = new JScrollPane() {
			@Override public Dimension getPreferredSize() { return UITheme.scrollPaneDimension; }
		};
		scrollableArrangementTable.setRowHeight(35);
		scrollableArrangementTable.setFont(new Font("Calibri", Font.PLAIN, 15));
		arrangementScrollPane.setViewportView(scrollableArrangementTable);
		JList<String> list = new JList<>();
		list.setListData(new String[] { "Section", "Bars", "Melody%", "Bass%", "Chord%", "Arp%", "Drum%" });
		list.setFixedCellHeight(scrollableArrangementTable.getRowHeight()
				+ scrollableArrangementTable.getRowMargin());
		arrangementScrollPane.setRowHeaderView(list);
		arrangementScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		arrangementScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		if (useArrangement.isSelected()) {
			arrangement.setPreviewChorus(false);
			actualArrangement.setPreviewChorus(false);
		} else {
			arrangement.setPreviewChorus(true);
			actualArrangement.setPreviewChorus(true);
			actualArrangement.resetArrangement();
		}
		scrollableArrangementTable.setRowSelectionAllowed(false);
		scrollableArrangementTable.setColumnSelectionAllowed(true);
		scrollableArrangementTable.getTableHeader().setPreferredSize(new Dimension(
				UITheme.scrollPaneDimension.width - ArrangementGUI.arrangementRowHeaderWidth, 30));
		scrollableArrangementTable.getTableHeader().addMouseListener(new MouseAdapter() {
			@Override public void mouseReleased(MouseEvent e) {
				LG.d("MOVED HEADER");
				arrangement.resortByIndexes(scrollableArrangementTable, false,
						ArrangementGUI.this::recolorAllVariationButtons);
			}
		});
		scrollableArrangementTable.addMouseListener(new MouseAdapter() {
			@Override public void mousePressed(MouseEvent evt) {
				int row = scrollableArrangementTable.rowAtPoint(evt.getPoint());
				int secOrder = scrollableArrangementTable.columnAtPoint(evt.getPoint());
				if (row == 0 && secOrder >= 0) {
					if (SwingUtilities.isRightMouseButton(evt)) {
						handleArrangementAction("ArrangementRemove," + secOrder, 0, 0);
					} else if (SwingUtilities.isMiddleMouseButton(evt)) {
						handleArrangementAction("ArrangementAdd," + secOrder, 0, 0);
					}
				}
			}
		});

		scrollableArrangementActualTable = new JTable(5, 5) {
			private static final long serialVersionUID = 1L;
			@Override public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
				Component comp = super.prepareRenderer(renderer, row, col);
				Object value = getModel().getValueAt(row,
						scrollableArrangementActualTable.convertColumnIndexToModel(col));
				comp.setForeground(UITheme.isDarkMode ? ArrangementGUI.arrangementDarkModeText
						: ArrangementGUI.arrangementLightModeText);
				if (value == null || getModel().getColumnCount() <= col) return comp;
				if (row == 0) {
					arrangementTableProcessSectionType(comp,
							(String) getModel().getValueAt(row, col));
					return comp;
				}
				int height = 350 / getModel().getRowCount();
				int width = Math.max(GUIConstants.TABLE_COLUMN_MIN_WIDTH,
						(int) ((UITheme.scrollPaneDimension.getWidth() - 60)
								/ getModel().getColumnCount()) - 2);
				if (row == 1) return new SectionInfoCellRenderer(width, height, col, chordGUI,
						ArrangementGUI.this::getTableRenderState);
				Collection<?> stringables = value instanceof String
						? Collections.singleton((String) value) : (Collection<?>) value;
				return new CollectionCellRenderer(stringables, width, height, row - 2, col,
						ArrangementGUI.this::getInstList,
						(part, panelOrder) -> panelController.getAbsoluteOrder(INST.fromIndex(part), panelOrder),
						ArrangementGUI.this::getTableRenderState, context::getGUIConfig);
			}
		};
		scrollableArrangementActualTable.addMouseListener(new MouseAdapter() {
			@Override public void mousePressed(MouseEvent evt) {
				ArrangementGUI.this.processActualArrangementMouseEvent(evt);
			}
			@Override public void mouseReleased(MouseEvent evt) {
				if (ArrangementGUI.this.copyDragging) {
					ArrangementGUI.this.processActualArrangementCopyDragging(evt);
					ArrangementGUI.this.resetCopyDrag();
				}
			}
		});
		scrollableArrangementActualTable.addMouseMotionListener(new MouseMotionListener() {
			@Override public void mouseMoved(MouseEvent e) {
				updateArrangementSubcell(e);
			}
			@Override public void mouseDragged(MouseEvent e) {
				updateArrangementSubcell(e);
			}
			private void updateArrangementSubcell(MouseEvent e) {
				boolean repaintAnyway = ArrangementGUI.this.highlightedTableCell != null;
				ArrangementGUI.this.highlightedTableCell = ArrangementGUI.this.calculateCurrentTableSubcell(e);
				ArrangementGUI.this.arrangementActualTableMousePoint = new Point(e.getPoint());
				if (ArrangementGUI.this.highlightedTableCell != null || repaintAnyway) {
					scrollableArrangementActualTable.repaint();
				}
			}
		});

		scrollableArrangementActualTable.setRowHeight(35);
		scrollableArrangementActualTable.setFont(new Font("Calibri", Font.PLAIN, 15));
		scrollableArrangementActualTable.setModel(actualArrangement.convertToActualTableModel());
		arrangementActualScrollPane = new JScrollPane() {
			@Override public Dimension getPreferredSize() { return UITheme.scrollPaneDimension; }
		};
		JList<String> actualList = new JList<>();
		actualList.setListData(new String[] { "", "Section", "Info", "Melody", "Bass", "Chord", "Arp", "Drum" });
		actualList.setFixedCellHeight(scrollableArrangementActualTable.getRowHeight()
				+ scrollableArrangementActualTable.getRowMargin());
		arrangementActualScrollPane.setRowHeaderView(actualList);
		arrangementActualScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		arrangementActualScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollableArrangementActualTable.setColumnSelectionAllowed(true);
		scrollableArrangementActualTable.setRowSelectionAllowed(false);
		scrollableArrangementActualTable.getTableHeader().addMouseListener(new MouseAdapter() {
			@Override public void mouseReleased(MouseEvent e) {
				LG.i("MOVED");
				actualArrangement.resortByIndexes(scrollableArrangementActualTable, true,
						ArrangementGUI.this::recolorAllVariationButtons);
				setManualArrangementSelected(true);
			}
		});

		actualArrangementCombinedPanel = new JPanel();
		actualArrangementCombinedPanel.setLayout(
				new BoxLayout(actualArrangementCombinedPanel, BoxLayout.Y_AXIS));
		scrollableArrangementActualTable.getTableHeader().setPreferredSize(new Dimension(
				UITheme.scrollPaneDimension.width - ArrangementGUI.arrangementRowHeaderWidth, 30));
		actualArrangementCombinedPanel.add(scrollableArrangementActualTable.getTableHeader());
		actualArrangementCombinedPanel.add(scrollableArrangementActualTable);
		variationButtonsPanel = new JPanel();
		refreshVariationPopupButtons();
		actualArrangementCombinedPanel.add(variationButtonsPanel);
		arrangementActualScrollPane.setViewportView(actualArrangementCombinedPanel);
		instrumentTabPane.addTab("Arrangement", arrangementScrollPane);
		instrumentTabPane.addTab("Generated Arrangement", arrangementActualScrollPane);
		UITheme.toggleableComponents.add(commitAllPanelBtn);
		UITheme.toggleableComponents.add(undoPanelBtn);
		UITheme.toggleableComponents.add(clearPanelBtn);
		UITheme.toggleableComponents.add(clearAllPanelsBtn);
		resetArrSection();
	}

	public void setActualModel(TableModel model) {
		setActualModel(model, true);
	}

	public void setActualModel(TableModel model, boolean reset) {
		scrollableArrangementActualTable.setModel(model);
		scrollableArrangementActualTable.setRowSelectionAllowed(false);
		scrollableArrangementActualTable.setColumnSelectionAllowed(true);
		if (reset) {
			resetArrSection();
		}
	}

	public void resetArrSection() {
		List<Section> actualSections = actualArrangement.getSections();
		if (actualSections != null) {
			List<String> sectionNamesNumbers = new ArrayList<>();
			for (int i = 0; i < actualSections.size(); i++) {
				Section sec = actualSections.get(i);
				String suffix = sec.hasCustomizedParts() ? "*" : "";
				sectionNamesNumbers.add((i + 1) + ": " + actualSections.get(i).getType() + suffix);
			}
			arrSection.setButtons(new ArrayList<>());
			arrSection.addAll(sectionNamesNumbers.toArray(new String[] {}));
		}
	}

	public void handleArrangementAction(String action, int seed, int maxLength) {
		boolean refreshActual = false;
		boolean resetArrSectionSelection = true;
		boolean resetArrSectionPanel = true;
		boolean checkManual = false;
		if (action.equalsIgnoreCase("ArrangementReset")) {
			arrangement.generateDefaultArrangement();
			pieceLength.setText("12");
		} else if (action.equalsIgnoreCase("ArrangementAddLast")) {
			if (instrumentTabPane.getSelectedIndex() == 5) {
				arrangement.duplicateSection(scrollableArrangementTable);
			} else {
				actualArrangement.duplicateSection(scrollableArrangementActualTable);
				refreshActual = true;
				checkManual = true;
			}
			if (arrangement.getSections().size() > maxLength) {
				pieceLength.setText("" + ++maxLength);
			}
		} else if (action.equalsIgnoreCase("ArrangementRemoveLast")) {
			if (instrumentTabPane.getSelectedIndex() == 5) {
				arrangement.removeSection(scrollableArrangementTable);
			} else {
				actualArrangement.removeSection(scrollableArrangementActualTable);
				refreshActual = true;
				checkManual = true;
			}
		} else if (action.equalsIgnoreCase("ArrangementRandomize")) {
			arrangement.randomizeFully(maxLength, seed, 50, 30, 2, 4, 15);
		} else if (action.startsWith("ArrangementOpenVariation,")) {
			int sectionOrder = Integer.parseInt(action.split(",")[1]);
			openVariationPopup(sectionOrder);
			return;
		} else if (action.startsWith("ArrangementApply")) {
			String selectedItem = arrSection.getVal();
			if (GLOBAL.equals(selectedItem)) {
				return;
			}
			int replacedPartNum = instrumentTabPane.getSelectedIndex();
			Integer sectionOrder = Integer.valueOf(selectedItem.split(":")[0]);
		applyCustomPanelsToSection(action, replacedPartNum, sectionOrder);
			if (instrumentTabPane.getSelectedIndex() < 5) {
				resetArrSectionSelection = false;
				resetArrSectionPanel = false;
				refreshActual = true;
				checkManual = true;
			}
			if (instrumentTabPane.getSelectedIndex() < 5) {
				if (switchTabPaneAfterApply) {
					switchTabPaneAfterApply = false;
					instrumentTabPane.setSelectedIndex(6);
					arrSection.setSelectedIndexWithProperty(0, true);
				}
				if (switchTabPaneToScoreAfterApply) {
					switchTabPaneToScoreAfterApply = false;
					if (instrumentTabPane.getComponents().length > 7) {
						instrumentTabPane.setSelectedIndex(7);
					}
					arrSection.setSelectedIndexWithProperty(0, true);
				}
			}
		} else if (action.startsWith("ArrangementClearPanels")) {
			String selectedItem = arrSection.getVal();
			if (!GLOBAL.equals(selectedItem)) {
				int sectionOrder = Integer.parseInt(selectedItem.split(":")[0]);
				Section sec = actualArrangement.getSections().get(sectionOrder - 1);
				sec.setMelodyParts(null);
				sec.setBassParts(null);
				sec.setChordParts(null);
				sec.setArpParts(null);
				sec.setDrumParts(null);
			}
		} else if (action.startsWith("ArrangementAddNewSection")) {
			String selectedItem;
			Integer column = null;
			if (action.contains(",")) {
				selectedItem = action.split(",")[1];
				column = SectionDropDownCheckButton.popupIndex - 1;
			} else {
				selectedItem = newSectionBox.getVal();
			}
			if (OMNI.EMPTYCOMBO.equals(selectedItem)) {
				return;
			}
			if (instrumentTabPane.getSelectedIndex() != 5) {
				Section addedSection = actualArrangement
						.addDefaultSection(scrollableArrangementActualTable, selectedItem, column);
				preparePartMap(addedSection);
				arrangement.initPartInclusionMapIfNull(this::getInstrumentParts);
				addedSection.generatePresences(
						arrangementSeed.getValue() != 0 ? new Random(arrangementSeed.getValue())
								: new Random(), arrangement.getInclMap(), false,
						this::getInstrumentParts);
				resetArrSectionSelection = actualArrangement.getSections()
						.indexOf(addedSection) == arrSection.getSelectedIndex() - 2;
				refreshActual = true;
				checkManual = true;
			} else {
				arrangement.addDefaultSection(scrollableArrangementTable, selectedItem);
				if (arrangement.getSections().size() > maxLength) {
					pieceLength.setText("" + ++maxLength);
				}
			}
			newSectionBox.setSelectedIndex(0);
		} else if (action.startsWith("ArrangementRemove,")) {
			int sectionIndex = Integer.parseInt(action.split(",")[1]);
			if (instrumentTabPane.getSelectedIndex() == 5) {
				arrangement.removeSectionExact(scrollableArrangementTable, sectionIndex);
			} else {
				actualArrangement.removeSectionExact(scrollableArrangementActualTable, sectionIndex);
				resetArrSectionSelection = sectionIndex < arrSection.getSelectedIndex();
				refreshActual = true;
				checkManual = true;
			}
		} else if (action.startsWith("ArrangementAdd,")) {
			LG.i("add exact");
			int sectionIndex = Integer.parseInt(action.split(",")[1]);
			if (instrumentTabPane.getSelectedIndex() == 5) {
				arrangement.duplicateSectionExact(scrollableArrangementTable, sectionIndex);
			} else {
				actualArrangement.duplicateSectionExact(scrollableArrangementActualTable, sectionIndex);
				resetArrSectionSelection = sectionIndex < arrSection.getSelectedIndex() - 1;
				refreshActual = true;
				checkManual = true;
			}
			if (arrangement.getSections().size() > maxLength) {
				pieceLength.setText("" + ++maxLength);
			}
		}

		if (!refreshActual) {
			scrollableArrangementTable.setModel(arrangement.convertToTableModel());
		} else {
			int index = arrSection.getSelectedIndex();
			setActualModel(actualArrangement.convertToActualTableModel(), resetArrSectionPanel);
			if (resetArrSectionSelection) {
				arrSection.setSelectedIndexWithProperty(0, true);
			} else {
				arrSection.setSelectedIndexWithProperty(index, true);
			}
			arrSection.repaint();
			refreshVariationPopupButtons();
		}
		if (checkManual) {
			manualArrangement.setSelected(true);
			manualArrangement.repaint();
		}
		context.recalculateTabPaneCounts();
	}

	public void openVariationPopup(int sectionOrder) {
		if (varPopup != null) {
			varPopup.getFrame().dispose();
		}
		recalculateActualArrangementSection(sectionOrder - 1);
		varPopup = new VariationPopup(sectionOrder, actualArrangement.getSections().get(sectionOrder - 1),
				context.getVariationPopupLocation(), context.getVariationPopupWindowSize(),
				this::getInstList, this::getInstrumentParts, chordGUI,
				scrollableArrangementActualTable, new VariationPopup.ArrangementActions() {
					@Override public void recolorVariationPopupButton(int order) {
						ArrangementGUI.this.recolorVariationPopupButton(order);
					}
					@Override public void refreshActualModel(boolean reset) {
						ArrangementGUI.this.setActualModel(actualArrangement.convertToActualTableModel(), reset);
					}
					@Override public void clearVariationPopup() {
						ArrangementGUI.this.varPopup = null;
					}
					@Override public void markManualArrangement() {
						manualArrangement.setSelected(true);
						manualArrangement.repaint();
					}
					@Override public void removeVariationForAllSections(INST instrument, int row, int column) {
						actualArrangement.getSections().forEach(section -> section.removeVariationForPart(
								instrument.getIndex(), row, column));
					}
					@Override public void toggleGlobalVariation(INST instrument, int column) {
						Boolean[] variations = arrangement.getGlobalVariationMap().get(instrument.getIndex());
						if (variations[column - 1]) {
							variations[column - 1] = Boolean.FALSE;
						} else {
							variations[column - 1] = Boolean.TRUE;
							actualArrangement.getSections().forEach(section ->
									section.removeVariationForAllParts(instrument.getIndex(), column));
						}
					}
					@Override public Boolean[] getGlobalVariationMap(INST instrument) {
						return arrangement.getGlobalVariationMap().get(instrument.getIndex());
					}
				});
	}

	public int getSelectedSectionIndex() {
		return arrSection.getSelectedIndex();
	}

	public List<String> getSectionNamesFromSelectedIndex() {
		List<String> names = new ArrayList<>();
		for (int i = arrSection.getSelectedIndex(); i < arrSection.getItemCount(); i++) {
			names.add(arrSection.getVal(i));
		}
		return names;
	}

	public Section getSelectedActualSection() {
		return actualArrangement.getSections().get(arrSection.getSelectedIndex() - 1);
	}

	public void recalculateActualArrangementSection(int sectionOrder) {
		if (actualArrangement == null || actualArrangement.getSections() == null
				|| actualArrangement.getSections().size() <= sectionOrder) {
			return;
		}
		Section sec = actualArrangement.getSections().get(sectionOrder);
		if (sec != null) {
			preparePartMap(sec);
		}
	}

	public void openPartInclusionPopup() {
		arrangement.recalculatePartInclusionMapBoundsIfNeeded(this::getInstrumentParts);
		new ArrangementPartInclusionPopup(arrangement, this::getInstList,
				this::getInstrumentParts);
	}

	public void openGlobalVariationPopup() {
		new ArrangementGlobalVariationPopup(arrangement);
	}

	public void openPatternManagerPopup() {
		new PatternManagerPopup(chordGUI::getUserChordDurations, context::getGUIConfig);
	}
}
