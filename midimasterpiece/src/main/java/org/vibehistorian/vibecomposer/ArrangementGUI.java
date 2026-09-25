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

package org.vibehistorian.vibecomposer;

import org.apache.commons.lang3.tuple.Triple;
import org.vibehistorian.vibecomposer.Components.CheckButton;
import org.vibehistorian.vibecomposer.Components.RandomValueButton;
import org.vibehistorian.vibecomposer.Components.SectionDropDownCheckButton;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Components.CollectionCellRenderer;
import org.vibehistorian.vibecomposer.Components.SectionInfoCellRenderer;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Helpers.UsedPattern;
import org.vibehistorian.vibecomposer.Panels.ArrangementSectionSelectorPanel;
import org.vibehistorian.vibecomposer.Panels.DetachedKnobPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Popups.ArrangementGlobalVariationPopup;
import org.vibehistorian.vibecomposer.Popups.ArrangementPartInclusionPopup;
import org.vibehistorian.vibecomposer.Popups.PatternManagerPopup;
import org.vibehistorian.vibecomposer.Popups.VariationPopup;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;


/** Owns arrangement controls, data and operations. */
public class ArrangementGUI {
	public static ArrangementGUI arrangementGUI;
	public static Arrangement arrangement;
	public static Arrangement actualArrangement;
	public static JPanel arrangementSettings;
	public static KnobPanel arrangementVariationChance;
	public static KnobPanel arrangementPartVariationChance;
	public static CheckButton manualArrangement;
	public static JTextField pieceLength;
	public static RandomValueButton arrangementSeed;
	public static CheckButton useArrangement;
	public static JCheckBox randomizeArrangementOnCompose;
	public static final String GLOBAL = "Global";
	public static ArrangementSectionSelectorPanel arrSection;
	public static JScrollPane arrSectionPane;
	public static boolean switchTabPaneAfterApply;
	public static boolean switchTabPaneToScoreAfterApply;
	public static JPanel arrangementMiddleColoredPanel;
	public static ScrollComboBox<String> newSectionBox;
	public static int arrangementRowHeaderWidth = 120;
	public static JScrollPane arrangementScrollPane;
	public static JScrollPane arrangementActualScrollPane;
	public static JTable scrollableArrangementTable;
	public static JTable scrollableArrangementActualTable;
	public static boolean arrangementTableColumnDragging;
	public static boolean actualArrangementTableColumnDragging;
	public static JPanel actualArrangementCombinedPanel;
	public static JPanel arrangementCombinedPanel;
	public static JPanel variationButtonsPanel;
	public static boolean copyDragging;
	public static Triple<Integer, Integer, Integer> highlightedTableCell;
	public static Triple<Integer, Integer, Integer> copyDraggingOrigin;
	public static Point arrangementActualTableMousePoint;
	public static UsedPattern copyDraggedPattern;
	public static Color arrangementLightModeText = new Color(220, 220, 220);
	public static int arrangementDarkModeLowestColor = 100;
	public static Color arrangementDarkModeText = new Color(50, 50, 50);
	public static int arrangementLightModeHighestColor = 180;
	public static JCheckBox arrangementScaleMidiVelocity;
	public static JCheckBox arrangementResetCustomPanelsOnCompose;
	public static VariationPopup varPopup;

	private final Context context;

	public ArrangementGUI(Context context) {
		this.context = context;
		arrangementGUI = this;
	}

	/** Supplies the cross-tab work that belongs to the main window. */
	public interface Context {
		Dimension getScrollPaneDimension();
		boolean isDarkMode();
		int getTableColumnMinWidth();
		JPanel getEverythingPanel();
		GridBagConstraints getConstraints();
		Set<Component> getToggleableComponents();
		JTabbedPane getInstrumentTabPane();
		JButton makeButton(String name, String actionCommand, int width, int height);
		JButton makeButton(String name, Consumer<? super Object> action, int width);
		JCheckBox makeCheckBox(String label, boolean selected, boolean thick);
		void recalculateTabPaneCounts();
		boolean canRegenerateOnChange();
		void regenerate();
		void openApplyCustomSectionPopup();
		List<? extends InstPanel> getInstrumentPanels(int instrument);
		JScrollPane getInstrumentPanelScrollPane(int instrument);
		List<InstPart> getCustomSectionParts(int instrument);
		InstPanel makeInstrumentPanel(int instrument);
		int getAbsoluteOrder(int instrument, int relativeOrder);
		boolean isFullMode();
		Color getPanelColorHigh();
		Color getUiColor();
		void toggleButtonEnabledForPanels();
		PhraseNotes getPatternRaw(UsedPattern pattern);
		void showInvalidPatternCopyInfo();
		boolean hasCurrentMidi();
		void openMidiEditPopup(Section section, int instrument, int panelOrder,
				int sectionOrder);
		void arrangementTableProcessSectionType(Component component, String value);
		void arrangementTableProcessComponent(Component component, int row, int column,
				String value, int[] maxCounts, boolean actual);
		void refreshVariationPopupButtons(int count);
		JFrame getMainWindow();
	}

	public void applyCustomPanelsToSection(String action, int replacedPartNum,
			Integer sectionOrder) {
		int lastSectionOrder = sectionOrder + 1;
		if (action.endsWith("+")) {
			lastSectionOrder = actualArrangement.getSections().size() + 1;
		} else if (action.contains(",")) {
			lastSectionOrder = Integer.valueOf(action.split(",")[1]) + 1;
		}

		for (int i = sectionOrder; i < lastSectionOrder; i++) {
			Section section = actualArrangement.getSections().get(i - 1);
			if (replacedPartNum >= 0 && replacedPartNum < 5) {
				section.setInstPartList(context.getCustomSectionParts(replacedPartNum),
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
			int actualSize = context.getInstrumentPanels(instrument).size();
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
			arrangementMiddleColoredPanel.setBackground(context.getPanelColorHigh().brighter());
			for (int instrument = 0; instrument < 5; instrument++) {
				JScrollPane pane = context.getInstrumentPanelScrollPane(instrument);
				List<? extends InstPanel> panels = context.getInstrumentPanels(instrument);
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
			arrangementMiddleColoredPanel.setBackground(context.getUiColor().darker().darker());
			int sectionOrder = Integer.valueOf(selectedItem.split(":")[0]) - 1;
			Section section = actualArrangement.getSections().get(sectionOrder);
			for (int instrument = 0; instrument < 5; instrument++) {
				JScrollPane pane = context.getInstrumentPanelScrollPane(instrument);
				JPanel panelView = (JPanel) pane.getViewport().getView();
				List<InstPanel> sectionPanels = new ArrayList<>();
				List<Integer> missingPanels = new ArrayList<>();
				context.getInstrumentPanels(instrument)
						.forEach(panel -> missingPanels.add(panel.getPanelOrder()));
				List<? extends InstPart> sectionParts = section.getInstPartList(instrument);
				if (sectionParts != null) {
					for (Component component : panelView.getComponents()) {
						if (component instanceof InstPanel) {
							int order = ((InstPanel) component).getAbsoluteOrder();
							if (order < sectionParts.size()) {
								panelView.remove(component);
								InstPanel copy = context.makeInstrumentPanel(instrument);
								copy.setFromInstPart(sectionParts.get(order));
								sectionPanels.add(copy);
								missingPanels.remove(Integer.valueOf(order));
							}
						}
					}
				}
				if (!missingPanels.isEmpty()) {
					List<? extends InstPanel> panels = new ArrayList<>(context.getInstrumentPanels(instrument))
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
								InstPanel copy = context.makeInstrumentPanel(instrument);
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
							.forEach(component -> component.setVisible(context.isFullMode()));
					panel.setVisible(false);
					panelView.add(panel);
				}
				addedPanels.addAll(sectionPanels);
			}
		}
		arrangementMiddleColoredPanel.repaint();
		addedPanels.forEach(panel -> panel.setVisible(true));
		context.toggleButtonEnabledForPanels();
		for (int instrument = 0; instrument < 5; instrument++) {
			context.getInstrumentPanelScrollPane(instrument).repaint();
		}
		if (context.getInstrumentTabPane().getSelectedIndex() == 6) {
			actualArrangement.getSections().forEach(Section::initPartMapFromOldData);
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
		List<? extends InstPanel> panels = context.getInstrumentPanels(instrument);
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
			if (event.isAltDown()) {
				if (sectionOrder + 1 < arrSection.getItemCount()) {
					arrSection.setSelectedIndexWithProperty(sectionOrder + 1, true);
					arrSection.repaint();
					context.getInstrumentTabPane().setSelectedIndex(instrument);
					switchTabPaneAfterApply = true;
				}
			} else if (event.isControlDown()) {
				Section section = actualArrangement.getSections().get(sectionOrder);
				if (section.getPresence(instrument).contains(panelOrder)
						&& section.containsPattern(instrument, panelOrder)) {
					copyDraggingOrigin = Triple.of(instrument, partOrder, sectionOrder);
					prepareCustomMidiSubcellCopy(instrument, panelOrder, section);
				}
			} else if (context.hasCurrentMidi()) {
				Section section = actualArrangement.getSections().get(sectionOrder);
				if (section.getPresence(instrument).contains(panelOrder)
						&& section.containsPattern(instrument, panelOrder)) {
					context.openMidiEditPopup(section, instrument, panelOrder, sectionOrder);
				} else {
					LG.i("Presence: " + section.getPresence(instrument).contains(panelOrder)
							+ ", contains pattern: " + section.containsPattern(instrument, panelOrder));
				}
			}
			return;
		}

		Section section = actualArrangement.getSections().get(sectionOrder);
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
					int absoluteOrder = context.getAbsoluteOrder(instrument, panel);
					target.setPresence(instrument, absoluteOrder);
					target.setVariation(instrument, absoluteOrder,
							section.getVariation(instrument, absoluteOrder));
					if (event.isShiftDown()) {
						UsedPattern pattern = section.getPattern(instrument, panel);
						if (pattern != null) {
							PhraseNotes notes = context.getPatternRaw(pattern);
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
				} else if (hasPresence) section.generateVariations(new Random(), instrument);
			} else if (hasPresence) {
				for (int i = 0; i < panels.size(); i++) section.resetPresence(instrument, i);
			} else {
				arrangement.initPartInclusionMapIfNull();
				section.generatePresences(new Random(), instrument, arrangement.getInclMap(), true);
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
						item.generateVariationForPartAndOrder(new Random(), instrument, partOrder, false);
					}
				}
			} else if (hasAnyPresence) {
				for (Section item : actualArrangement.getSections()) {
					item.initPartMapFromOldData();
					for (int i = 0; i < panels.size(); i++) item.resetPresence(instrument, partOrder);
				}
			} else {
				arrangement.initPartInclusionMapIfNull();
				for (Section item : actualArrangement.getSections()) {
					item.initPartMapFromOldData();
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
					section.generateVariationForPartAndOrder(new Random(), instrument, partOrder, true);
				}
			} else {
				section.initPartMapFromOldData();
				if (hasSinglePresence) section.resetPresence(instrument, partOrder);
				else section.setPresence(instrument, partOrder);
			}
		}
		setActualModel(actualArrangement.convertToActualTableModel(), false);
		context.refreshVariationPopupButtons(actualArrangement.getSections().size());
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
		PhraseNotes notes = context.getPatternRaw(copyDraggedPattern);
		if (notes == null) {
			context.showInvalidPatternCopyInfo();
			return;
		}
		Section section = actualArrangement.getSections().get(target.getRight());
		int instrument = target.getLeft();
		int panelOrder = context.getInstrumentPanels(instrument).get(target.getMiddle()).getPanelOrder();
		section.putPattern(instrument, panelOrder, copyDraggedPattern);
		if (!section.getPresence(instrument).contains(panelOrder))
			section.setPresence(instrument, target.getMiddle());
		notes.setApplied(true);
		setActualModel(actualArrangement.convertToActualTableModel(), false);
		context.refreshVariationPopupButtons(actualArrangement.getSections().size());
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

	public void initArrangementSettings(int startY, int anchorSide) {
		ArrangementGUI.arrangementSettings = new JPanel();
		ArrangementGUI.arrangementSettings.setOpaque(false);
		ArrangementGUI.arrangementSettings.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));

		JPanel arrangementSettingsLeft = new JPanel();
		arrangementSettingsLeft.setOpaque(false);
		arrangementSettingsLeft.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		JPanel arrangementSettingsRight = new JPanel();
		arrangementSettingsRight.setOpaque(false);
		arrangementSettingsRight.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		ArrangementGUI.useArrangement = new CheckButton("ARRANGE", false);
		arrangementSettingsLeft.add(ArrangementGUI.useArrangement);
		ArrangementGUI.pieceLength = new JTextField("12", 2);
		JButton resetArrangementBtn = context.makeButton("Reset", "ArrangementReset", 60, 30);
		JButton randomizeArrangementBtn = context.makeButton("Randomize", e -> {
			Random arrGen = new Random();
			handleArrangementAction("ArrangementRandomize", arrGen.nextInt(),
					Integer.valueOf(ArrangementGUI.pieceLength.getText()));
			context.recalculateTabPaneCounts();
			if (context.canRegenerateOnChange()) {
				context.regenerate();
			}
		}, 90);
		JButton arrangementPartInclusionBtn = context.makeButton("Parts",
				e -> openPartInclusionPopup(), 60);
		JButton arrangementGlobalVariationBtn = context.makeButton("Vars",
				e -> openGlobalVariationPopup(), 50);
		JButton patternManagerBtn = context.makeButton("Patterns", e -> openPatternManagerPopup(), 70);

		ArrangementGUI.randomizeArrangementOnCompose = context.makeCheckBox("on Compose", true, true);
		List<CheckButton> defaultButtons = new ArrayList<>();
		defaultButtons.add(new SectionDropDownCheckButton(GLOBAL, true, OMNI.alphen(Color.pink, 70)));
		ArrangementGUI.arrSection = new ArrangementSectionSelectorPanel(new ArrayList<>(), defaultButtons);

		JButton commitPanelBtn = context.makeButton("Apply", "ArrangementApply", 50, 30);
		JButton commitAllPanelBtn = context.makeButton("Apply..", e -> context.openApplyCustomSectionPopup(), 60);
		JButton undoPanelBtn = context.makeButton("<-*",
				e -> ArrangementGUI.arrSection.setSelectedIndexWithProperty(
						ArrangementGUI.arrSection.getSelectedIndex(), true), 30);
		JButton clearPanelBtn = context.makeButton("X*", e -> {
			if (!GLOBAL.equals(ArrangementGUI.arrSection.getVal())) {
				Section sec = ArrangementGUI.actualArrangement.getSections()
						.get(ArrangementGUI.arrSection.getSelectedIndex() - 1);
				if (sec.hasCustomizedParts()) {
					sec.resetCustomizedParts(context.getInstrumentTabPane().getSelectedIndex());
					setActualModel(ArrangementGUI.actualArrangement.convertToActualTableModel(), false);
					if (!sec.hasCustomizedParts()) {
						CheckButton cb = ArrangementGUI.arrSection.getCurrentButton();
						cb.setText(cb.getText().substring(0, cb.getText().length() - 1));
						cb.repaint();
					}
					ArrangementGUI.arrSection.setSelectedIndexWithProperty(
							ArrangementGUI.arrSection.getSelectedIndex(), true);
				}
			}
		}, 30);
		JButton clearAllPanelsBtn = context.makeButton("CLR*", e -> {
			ArrangementGUI.actualArrangement.getSections().forEach(s -> s.resetCustomizedParts());
			setActualModel(ArrangementGUI.actualArrangement.convertToActualTableModel(), false);
			ArrangementGUI.arrSection.getButtons().forEach(cb -> {
				if (!GLOBAL.equals(cb.getText()) && cb.getText().contains("*")) {
					cb.setText(cb.getText().substring(0, cb.getText().length() - 1));
					cb.repaint();
				}
			});
			ArrangementGUI.scrollableArrangementActualTable.repaint();
		}, 40);

		JButton copySelectedBtn = context.makeButton("Cc", "ArrangementAddLast", 30, 30);
		JButton removeSelectedBtn = context.makeButton("X", "ArrangementRemoveLast", 30, 30);
		ArrangementGUI.newSectionBox = new ScrollComboBox<>(false);
		ArrangementGUI.newSectionBox.addItem(OMNI.EMPTYCOMBO);
		for (Section.SectionType type : Section.SectionType.values()) {
			ArrangementGUI.newSectionBox.addItem(type.toString());
		}
		JButton addNewSectionBtn = context.makeButton("Add", "ArrangementAddNewSection", 35, 30);

		arrangementSettingsLeft.add(randomizeArrangementBtn);
		arrangementSettingsLeft.add(ArrangementGUI.randomizeArrangementOnCompose);
		arrangementSettingsLeft.add(resetArrangementBtn);
		ArrangementGUI.arrangementVariationChance = new DetachedKnobPanel("Section<br>Variations", 30);
		arrangementSettingsLeft.add(ArrangementGUI.arrangementVariationChance);
		ArrangementGUI.arrangementPartVariationChance = new DetachedKnobPanel("Part<br>Variations", 25);
		arrangementSettingsLeft.add(ArrangementGUI.arrangementPartVariationChance);
		arrangementSettingsLeft.add(arrangementPartInclusionBtn);
		arrangementSettingsLeft.add(arrangementGlobalVariationBtn);
		arrangementSettingsLeft.add(patternManagerBtn);

		ArrangementGUI.arrangementMiddleColoredPanel = new JPanel();
		ArrangementGUI.arrangementMiddleColoredPanel.add(new JLabel("                                      "));
		ArrangementGUI.arrangementSettings.add(arrangementSettingsLeft);
		ArrangementGUI.arrangementSettings.add(ArrangementGUI.arrangementMiddleColoredPanel);

		ArrangementGUI.manualArrangement = new CheckButton("MANUAL", false);
		arrangementSettingsRight.add(ArrangementGUI.manualArrangement);
		arrangementSettingsRight.add(commitPanelBtn);
		arrangementSettingsRight.add(commitAllPanelBtn);
		arrangementSettingsRight.add(undoPanelBtn);
		arrangementSettingsRight.add(clearPanelBtn);
		arrangementSettingsRight.add(clearAllPanelsBtn);
		arrangementSettingsRight.add(ArrangementGUI.newSectionBox);
		arrangementSettingsRight.add(addNewSectionBtn);
		arrangementSettingsRight.add(copySelectedBtn);
		arrangementSettingsRight.add(removeSelectedBtn);
		arrangementSettingsRight.add(new JLabel("Seed"));
		ArrangementGUI.arrangementSeed = new RandomValueButton(0);
		arrangementSettingsRight.add(ArrangementGUI.arrangementSeed);
		ArrangementGUI.arrangementSettings.add(arrangementSettingsRight);

		GridBagConstraints constraints = context.getConstraints();
		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		ArrangementGUI.arrSectionPane = new JScrollPane() {
			@Override public Dimension getPreferredSize() {
				return new Dimension(context.getScrollPaneDimension().width, 45);
			}
		};
		ArrangementGUI.arrSectionPane.setViewportView(ArrangementGUI.arrSection);
		ArrangementGUI.arrSectionPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		ArrangementGUI.arrSectionPane.getHorizontalScrollBar().setUnitIncrement(32);
		ArrangementGUI.arrSectionPane.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		ArrangementGUI.arrSectionPane.setOpaque(true);
		ArrangementGUI.arrSection.setOpaque(true);
		context.getEverythingPanel().add(ArrangementGUI.arrSectionPane, constraints);
		constraints.gridy = startY + 1;
		context.getEverythingPanel().add(ArrangementGUI.arrangementSettings, constraints);

		ArrangementGUI.scrollableArrangementTable = new JTable(5, 5) {
			private static final long serialVersionUID = 3846279087936376003L;
			@Override public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
				Component comp = super.prepareRenderer(renderer, row, col);
				comp.setForeground(context.isDarkMode() ? ArrangementGUI.arrangementDarkModeText
						: ArrangementGUI.arrangementLightModeText);
				if (getModel().getColumnCount() <= col) return comp;
				if (row == 0) {
					context.arrangementTableProcessSectionType(comp,
							(String) getModel().getValueAt(row, col));
					return comp;
				}
				if (row == 1) {
					comp.setBackground(new Color(100, 150, 150));
					return comp;
				}
				Object objValue = getModel().getValueAt(row,
						ArrangementGUI.scrollableArrangementTable.convertColumnIndexToModel(col));
				Integer value = (objValue instanceof String) ? Integer.valueOf((String) objValue)
						: (Integer) objValue;
				if (value > 100) {
					value = 100;
					getModel().setValueAt(value, row, col);
				} else if (value < 0) {
					value = 0;
					getModel().setValueAt(value, row, col);
				}
				context.arrangementTableProcessComponent(comp, row, col, String.valueOf(value),
						new int[] { 0, 0, 100, 100, 100, 100, 100 }, false);
				return comp;
			}
		};

		ArrangementGUI.arrangement = new Arrangement();
		ArrangementGUI.actualArrangement = new Arrangement();
		ArrangementGUI.arrangement.generateDefaultArrangement();
		ArrangementGUI.scrollableArrangementTable.setModel(ArrangementGUI.arrangement.convertToTableModel());
		ArrangementGUI.arrangementScrollPane = new JScrollPane() {
			@Override public Dimension getPreferredSize() { return context.getScrollPaneDimension(); }
		};
		ArrangementGUI.scrollableArrangementTable.setRowHeight(35);
		ArrangementGUI.scrollableArrangementTable.setFont(new Font("Calibri", Font.PLAIN, 15));
		ArrangementGUI.arrangementScrollPane.setViewportView(ArrangementGUI.scrollableArrangementTable);
		JList<String> list = new JList<>();
		list.setListData(new String[] { "Section", "Bars", "Melody%", "Bass%", "Chord%", "Arp%", "Drum%" });
		list.setFixedCellHeight(ArrangementGUI.scrollableArrangementTable.getRowHeight()
				+ ArrangementGUI.scrollableArrangementTable.getRowMargin());
		ArrangementGUI.arrangementScrollPane.setRowHeaderView(list);
		ArrangementGUI.arrangementScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		ArrangementGUI.arrangementScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		if (ArrangementGUI.useArrangement.isSelected()) {
			ArrangementGUI.arrangement.setPreviewChorus(false);
			ArrangementGUI.actualArrangement.setPreviewChorus(false);
		} else {
			ArrangementGUI.arrangement.setPreviewChorus(true);
			ArrangementGUI.actualArrangement.setPreviewChorus(true);
			ArrangementGUI.actualArrangement.resetArrangement();
		}
		ArrangementGUI.scrollableArrangementTable.setRowSelectionAllowed(false);
		ArrangementGUI.scrollableArrangementTable.setColumnSelectionAllowed(true);
		ArrangementGUI.scrollableArrangementTable.getTableHeader().setPreferredSize(new Dimension(
				context.getScrollPaneDimension().width - ArrangementGUI.arrangementRowHeaderWidth, 30));
		ArrangementGUI.scrollableArrangementTable.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
			@Override public void columnMoved(TableColumnModelEvent e) {
				ArrangementGUI.arrangementTableColumnDragging = true;
			}
			@Override public void columnAdded(TableColumnModelEvent e) { }
			@Override public void columnRemoved(TableColumnModelEvent e) { }
			@Override public void columnMarginChanged(ChangeEvent e) { }
			@Override public void columnSelectionChanged(ListSelectionEvent e) { }
		});
		ArrangementGUI.scrollableArrangementTable.getTableHeader().addMouseListener(new MouseAdapter() {
			@Override public void mouseReleased(MouseEvent e) {
				LG.d("MOVED HEADER");
				ArrangementGUI.arrangement.resortByIndexes(ArrangementGUI.scrollableArrangementTable, false);
				ArrangementGUI.arrangementTableColumnDragging = false;
			}
		});
		ArrangementGUI.scrollableArrangementTable.addMouseListener(new MouseAdapter() {
			@Override public void mousePressed(MouseEvent evt) {
				int row = ArrangementGUI.scrollableArrangementTable.rowAtPoint(evt.getPoint());
				int secOrder = ArrangementGUI.scrollableArrangementTable.columnAtPoint(evt.getPoint());
				if (row == 0 && secOrder >= 0) {
					if (SwingUtilities.isRightMouseButton(evt)) {
						handleArrangementAction("ArrangementRemove," + secOrder, 0, 0);
					} else if (SwingUtilities.isMiddleMouseButton(evt)) {
						handleArrangementAction("ArrangementAdd," + secOrder, 0, 0);
					}
				}
			}
		});

		ArrangementGUI.scrollableArrangementActualTable = new JTable(5, 5) {
			private static final long serialVersionUID = 1L;
			@Override public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
				Component comp = super.prepareRenderer(renderer, row, col);
				Object value = getModel().getValueAt(row,
						ArrangementGUI.scrollableArrangementActualTable.convertColumnIndexToModel(col));
				comp.setForeground(context.isDarkMode() ? ArrangementGUI.arrangementDarkModeText
						: ArrangementGUI.arrangementLightModeText);
				if (value == null || getModel().getColumnCount() <= col) return comp;
				if (row == 0) {
					context.arrangementTableProcessSectionType(comp,
							(String) getModel().getValueAt(row, col));
					return comp;
				}
				int height = 350 / getModel().getRowCount();
				int width = Math.max(context.getTableColumnMinWidth(),
						(int) ((context.getScrollPaneDimension().getWidth() - 60)
								/ getModel().getColumnCount()) - 2);
				if (row == 1) return new SectionInfoCellRenderer(width, height, col);
				Collection<?> stringables = value instanceof String
						? Collections.singleton((String) value) : (Collection<?>) value;
				return new CollectionCellRenderer(stringables, width, height, row - 2, col);
			}
		};
		ArrangementGUI.scrollableArrangementActualTable.addMouseListener(new MouseAdapter() {
			@Override public void mousePressed(MouseEvent evt) {
				ArrangementGUI.this.processActualArrangementMouseEvent(evt);
			}
			@Override public void mouseReleased(MouseEvent evt) {
				if (ArrangementGUI.copyDragging) {
					ArrangementGUI.this.processActualArrangementCopyDragging(evt);
					ArrangementGUI.this.resetCopyDrag();
				}
			}
		});
		ArrangementGUI.scrollableArrangementActualTable.addMouseMotionListener(new MouseMotionListener() {
			@Override public void mouseMoved(MouseEvent e) {
				updateArrangementSubcell(e);
			}
			@Override public void mouseDragged(MouseEvent e) {
				updateArrangementSubcell(e);
			}
			private void updateArrangementSubcell(MouseEvent e) {
				boolean repaintAnyway = ArrangementGUI.highlightedTableCell != null;
				ArrangementGUI.highlightedTableCell = ArrangementGUI.this.calculateCurrentTableSubcell(e);
				ArrangementGUI.arrangementActualTableMousePoint = new Point(e.getPoint());
				if (ArrangementGUI.highlightedTableCell != null || repaintAnyway) {
					ArrangementGUI.scrollableArrangementActualTable.repaint();
				}
			}
		});

		ArrangementGUI.scrollableArrangementActualTable.setRowHeight(35);
		ArrangementGUI.scrollableArrangementActualTable.setFont(new Font("Calibri", Font.PLAIN, 15));
		ArrangementGUI.scrollableArrangementActualTable.setModel(ArrangementGUI.actualArrangement.convertToActualTableModel());
		ArrangementGUI.arrangementActualScrollPane = new JScrollPane() {
			@Override public Dimension getPreferredSize() { return context.getScrollPaneDimension(); }
		};
		JList<String> actualList = new JList<>();
		actualList.setListData(new String[] { "", "Section", "Info", "Melody", "Bass", "Chord", "Arp", "Drum" });
		actualList.setFixedCellHeight(ArrangementGUI.scrollableArrangementActualTable.getRowHeight()
				+ ArrangementGUI.scrollableArrangementActualTable.getRowMargin());
		ArrangementGUI.arrangementActualScrollPane.setRowHeaderView(actualList);
		ArrangementGUI.arrangementActualScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		ArrangementGUI.arrangementActualScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		ArrangementGUI.scrollableArrangementActualTable.setColumnSelectionAllowed(true);
		ArrangementGUI.scrollableArrangementActualTable.setRowSelectionAllowed(false);
		ArrangementGUI.scrollableArrangementActualTable.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
			@Override public void columnMoved(TableColumnModelEvent e) {
				ArrangementGUI.actualArrangementTableColumnDragging = true;
			}
			@Override public void columnAdded(TableColumnModelEvent e) { }
			@Override public void columnRemoved(TableColumnModelEvent e) { }
			@Override public void columnMarginChanged(ChangeEvent e) { }
			@Override public void columnSelectionChanged(ListSelectionEvent e) { }
		});
		ArrangementGUI.scrollableArrangementActualTable.getTableHeader().addMouseListener(new MouseAdapter() {
			@Override public void mouseReleased(MouseEvent e) {
				LG.i("MOVED");
				ArrangementGUI.actualArrangement.resortByIndexes(ArrangementGUI.scrollableArrangementActualTable, true);
				ArrangementGUI.actualArrangementTableColumnDragging = false;
				ArrangementGUI.manualArrangement.setSelected(true);
				ArrangementGUI.manualArrangement.repaint();
			}
		});

		ArrangementGUI.actualArrangementCombinedPanel = new JPanel();
		ArrangementGUI.actualArrangementCombinedPanel.setLayout(
				new BoxLayout(ArrangementGUI.actualArrangementCombinedPanel, BoxLayout.Y_AXIS));
		ArrangementGUI.scrollableArrangementActualTable.getTableHeader().setPreferredSize(new Dimension(
				context.getScrollPaneDimension().width - ArrangementGUI.arrangementRowHeaderWidth, 30));
		ArrangementGUI.actualArrangementCombinedPanel.add(ArrangementGUI.scrollableArrangementActualTable.getTableHeader());
		ArrangementGUI.actualArrangementCombinedPanel.add(ArrangementGUI.scrollableArrangementActualTable);
		ArrangementGUI.variationButtonsPanel = new JPanel();
		context.refreshVariationPopupButtons(1);
		ArrangementGUI.actualArrangementCombinedPanel.add(ArrangementGUI.variationButtonsPanel);
		ArrangementGUI.arrangementActualScrollPane.setViewportView(ArrangementGUI.actualArrangementCombinedPanel);
		context.getInstrumentTabPane().addTab("Arrangement", ArrangementGUI.arrangementScrollPane);
		context.getInstrumentTabPane().addTab("Generated Arrangement", ArrangementGUI.arrangementActualScrollPane);
		context.getToggleableComponents().add(commitAllPanelBtn);
		context.getToggleableComponents().add(undoPanelBtn);
		context.getToggleableComponents().add(clearPanelBtn);
		context.getToggleableComponents().add(clearAllPanelsBtn);
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
			if (context.getInstrumentTabPane().getSelectedIndex() == 5) {
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
			if (context.getInstrumentTabPane().getSelectedIndex() == 5) {
				arrangement.removeSection(scrollableArrangementTable);
			} else {
				actualArrangement.removeSection(scrollableArrangementActualTable);
				refreshActual = true;
				checkManual = true;
			}
		} else if (action.equalsIgnoreCase("ArrangementRandomize")) {
			arrangement.randomizeFully(maxLength, seed, 50, 30, 2, 4, 15);
		} else if (action.startsWith("ArrangementOpenVariation,")) {
			Integer sectionOrder = Integer.valueOf(action.split(",")[1]);
			openVariationPopup(sectionOrder);
			return;
		} else if (action.startsWith("ArrangementApply")) {
			String selectedItem = arrSection.getVal();
			if (GLOBAL.equals(selectedItem)) {
				return;
			}
			int replacedPartNum = context.getInstrumentTabPane().getSelectedIndex();
			Integer sectionOrder = Integer.valueOf(selectedItem.split(":")[0]);
		applyCustomPanelsToSection(action, replacedPartNum, sectionOrder);
			if (context.getInstrumentTabPane().getSelectedIndex() < 5) {
				resetArrSectionSelection = false;
				resetArrSectionPanel = false;
				refreshActual = true;
				checkManual = true;
			}
			if (context.getInstrumentTabPane().getSelectedIndex() < 5) {
				if (switchTabPaneAfterApply) {
					switchTabPaneAfterApply = false;
					context.getInstrumentTabPane().setSelectedIndex(6);
					arrSection.setSelectedIndexWithProperty(0, true);
				}
				if (switchTabPaneToScoreAfterApply) {
					switchTabPaneToScoreAfterApply = false;
					if (context.getInstrumentTabPane().getComponents().length > 7) {
						context.getInstrumentTabPane().setSelectedIndex(7);
					}
					arrSection.setSelectedIndexWithProperty(0, true);
				}
			}
		} else if (action.startsWith("ArrangementClearPanels")) {
			String selectedItem = arrSection.getVal();
			if (!GLOBAL.equals(selectedItem)) {
				Integer sectionOrder = Integer.valueOf(selectedItem.split(":")[0]);
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
			if (context.getInstrumentTabPane().getSelectedIndex() != 5) {
				Section addedSection = actualArrangement
						.addDefaultSection(scrollableArrangementActualTable, selectedItem, column);
				addedSection.recalculatePartVariationMapBoundsIfNeeded();
				arrangement.initPartInclusionMapIfNull();
				addedSection.generatePresences(
						arrangementSeed.getValue() != 0 ? new Random(arrangementSeed.getValue())
								: new Random(), false);
				resetArrSectionSelection = actualArrangement.getSections()
						.indexOf(addedSection) == arrSection.getSelectedIndex() - 2;
				resetArrSectionPanel = true;
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
			Integer sectionIndex = Integer.valueOf(action.split(",")[1]);
			if (context.getInstrumentTabPane().getSelectedIndex() == 5) {
				arrangement.removeSectionExact(scrollableArrangementTable, sectionIndex);
			} else {
				actualArrangement.removeSectionExact(scrollableArrangementActualTable, sectionIndex);
				resetArrSectionSelection = sectionIndex < arrSection.getSelectedIndex();
				resetArrSectionPanel = true;
				refreshActual = true;
				checkManual = true;
			}
		} else if (action.startsWith("ArrangementAdd,")) {
			LG.i("add exact");
			Integer sectionIndex = Integer.valueOf(action.split(",")[1]);
			if (context.getInstrumentTabPane().getSelectedIndex() == 5) {
				arrangement.duplicateSectionExact(scrollableArrangementTable, sectionIndex);
			} else {
				actualArrangement.duplicateSectionExact(scrollableArrangementActualTable, sectionIndex);
				resetArrSectionSelection = sectionIndex < arrSection.getSelectedIndex() - 1;
				resetArrSectionPanel = true;
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
			context.refreshVariationPopupButtons(actualArrangement.getSections().size());
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
		JFrame mainWindow = context.getMainWindow();
		varPopup = new VariationPopup(sectionOrder, actualArrangement.getSections().get(sectionOrder - 1),
				new Point(SwingUtils.getMouseLocation().x, mainWindow.getLocation().y), mainWindow.getSize());
	}

	public static void recalculateActualArrangementSection(int sectionOrder) {
		if (actualArrangement == null || actualArrangement.getSections() == null
				|| actualArrangement.getSections().size() <= sectionOrder) {
			return;
		}
		Section sec = actualArrangement.getSections().get(sectionOrder);
		if (sec != null) {
			sec.recalculatePartVariationMapBoundsIfNeeded();
		}
	}

	public void openPartInclusionPopup() {
		arrangement.recalculatePartInclusionMapBoundsIfNeeded();
		new ArrangementPartInclusionPopup(arrangement);
	}

	public void openGlobalVariationPopup() {
		new ArrangementGlobalVariationPopup(arrangement);
	}

	public void openPatternManagerPopup() {
		new PatternManagerPopup();
	}
}
