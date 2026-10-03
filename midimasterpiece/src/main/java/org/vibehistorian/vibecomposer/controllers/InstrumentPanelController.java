package org.vibehistorian.vibecomposer.controllers;

import org.vibehistorian.vibecomposer.Constants;
import org.vibehistorian.vibecomposer.INST;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.PartPresetStore;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.UITheme;
import org.vibehistorian.vibecomposer.gui.ArrangementGUI;
import org.vibehistorian.vibecomposer.gui.InstGUI;

import javax.swing.*;
import javax.xml.bind.JAXBException;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Owns the shared lifecycle of instrument panels inside their instrument tabs. */
public final class InstrumentPanelController {
	public interface Context {
		void recalculateArrangementPartMaps();
		void recalculateTabPaneCounts();
		void recalculateAfterPanelGeneration();
		void recalculateAfterPanelAddition();
		void recalculateAfterPanelRemoval();
		void repaintInstrumentTabs();
		void repaintMainWindow();
		void regenerate();
		boolean canRegenerateOnChange();
	}

	private final Context context;
	private final Function<INST, InstPanel> setupInstPanel;
	private final Function<INST, InstGUI<?>> instrumentGui;
	private final IntSupplier currentSeed;
	private final Supplier<ArrangementGUI> arrangementGUI;
	private final PartPresetStore partPresetStore = new PartPresetStore();

	public InstrumentPanelController(Context context,
			Function<INST, InstPanel> setupInstPanel,
			Function<INST, InstGUI<?>> instrumentGui, IntSupplier currentSeed,
			Supplier<ArrangementGUI> arrangementGUI) {
		this.context = context;
		this.setupInstPanel = setupInstPanel;
		this.instrumentGui = instrumentGui;
		this.currentSeed = currentSeed;
		this.arrangementGUI = arrangementGUI;
	}

	private InstGUI<?> instrumentGui(INST instrument) {
		return instrumentGui.apply(instrument);
	}

	public List<PartPresetStore.PresetFile> listPartPresets(INST instrument) throws IOException {
		return partPresetStore.listPresets(instrument);
	}

	public InstPanel setupInstrumentPanel(INST instrument) {
		return setupInstPanel.apply(instrument);
	}

	public List<? extends InstPanel> getInstList(INST instrument) {
		return instrumentGui(instrument).getPanels();
	}

	public InstPanel getPanelByOrder(INST instrument, int panelOrder) {
		return getInstList(instrument).stream()
				.filter(panel -> panel.getPanelOrder() == panelOrder).findFirst().get();
	}

	public List<InstPart> getPartsFromPanels(List<? extends InstPanel> panels,
			boolean removeMuted, int seed) {
		List<InstPart> parts = new ArrayList<>();
		for (InstPanel panel : panels) {
			if (!removeMuted || !panel.getMuteInst()) {
				parts.add(panel.toInstPart(seed));
			}
		}
		return parts;
	}

	public int getAbsoluteOrder(INST instrument, int panelOrder) {
		List<Integer> allPanelOrders = getInstList(instrument).stream()
				.map(InstPanel::getPanelOrder).sorted().collect(Collectors.toList());
		int absoluteOrder = allPanelOrders.indexOf(panelOrder);
		if (absoluteOrder >= 0) {
			return absoluteOrder;
		}
		throw new IllegalArgumentException("Absolute order not found!");
	}

	public JScrollPane getInstPane(INST instrument) {
		return instrumentGui(instrument).getPanelScrollPane();
	}

	@SuppressWarnings("unchecked")
	public List<InstPanel> getAffectedPanels(INST instrument) {
		if (arrangementGUI.get() != null && arrangementGUI.get().isCustomSection()) {
			return getSectionPanelList(instrument);
		}
		return (List<InstPanel>) getInstList(instrument);
	}

	public List<InstPanel> getSectionPanelList(INST instrument) {
		JPanel panelContainer = (JPanel) getInstPane(instrument).getViewport().getView();
		List<InstPanel> sectionPanels = new ArrayList<>();
		for (Component component : panelContainer.getComponents()) {
			if (component instanceof InstPanel) {
				sectionPanels.add((InstPanel) component);
			}
		}
		return sectionPanels;
	}

	public InstPanel addPanel(INST instrument) {
		return addPanel(instrument, null, true);
	}

	public InstPanel addPanel(INST instrument, InstPart initializingPart,
							  boolean recalculateArrangement) {
		InstPanel panel = setupInstPanel.apply(instrument);
		List<InstPanel> affectedPanels = getAffectedPanels(instrument);
		int panelOrder = getLowestAvailablePanelNumber(affectedPanels);

		panel.getToggleableComponents().forEach(component ->
				component.setVisible(UITheme.isFullMode));
		if (arrangementGUI.get() != null && arrangementGUI.get().isCustomSection()) {
			panel.toggleGlobalElements(false);
			panel.toggleEnabledCopyRemove(false);
			if (instrument == INST.DRUM) {
				panel.getInstrumentBox().setEnabled(true);
			}
		} else {
			panel.setBackground(OMNI.alphen(Constants.instColors[instrument.getIndex()], 60));
		}

		if (initializingPart != null) {
			panel.setFromInstPart(initializingPart);
		}
		panel.setOrderAndOffset(panelOrder,
				initializingPart == null ? panelOrder : initializingPart.getOrderOffset());

		affectedPanels.add(panelOrder - 1, panel);
		if (recalculateArrangement && arrangementGUI.get() != null
				&& arrangementGUI.get().getActualArrangement() != null
				&& arrangementGUI.get().getActualArrangement().getSections() != null) {
			context.recalculateArrangementPartMaps();
		}

		JPanel panelContainer = (JPanel) getInstPane(instrument)
				.getViewport().getView();
		if (instrument != INST.DRUM || !instrumentGui(instrument).reversePanelOrder()) {
			panelContainer.add(panel, panelOrder - 1);
		} else {
			panelContainer.add(panel, affectedPanels.size() - panelOrder);
		}
		return panel;
	}

	public void addRandomPanel(INST instrument) {
		createRandomPanels(instrument, getAffectedPanels(instrument).size() + 1,
				true, null, null);
		context.recalculateAfterPanelAddition();
		context.repaintMainWindow();
	}

	public void generatePanels(INST instrument) {
		generatePanels(instrument, false);
	}

	public void generatePanels(INST instrument, boolean triggerRegenerate) {
		int panelCount = arrangementGUI.get() != null && arrangementGUI.get().isCustomSection()
				? getInstList(instrument).size()
				: instrumentGui(instrument).getRandomPanelCount();
		createRandomPanels(instrument, panelCount, false, null, null);
		context.recalculateAfterPanelGeneration();
		if (triggerRegenerate && context.canRegenerateOnChange()) {
			context.regenerate();
		}
	}

	public void createRandomPanels(INST instrument, int panelCount, boolean onlyAdd) {
		createRandomPanels(instrument, panelCount, onlyAdd, null, null);
	}

	public void createRandomPanels(INST instrument, int panelCount, boolean onlyAdd,
								   Integer seed, InstPanel randomizedPanel) {
		instrumentGui(instrument).createRandomPanels(panelCount, onlyAdd, seed,
				randomizedPanel);
		context.repaintMainWindow();
	}

	public void randomizePanel(InstPanel panel) {
		INST instrument = INST.fromIndex(panel.getPartNum());
		switch (instrument) {
		case MELODY:
			createRandomPanels(instrument, getInstList(instrument).size() + 1, true,
					new java.util.Random().nextInt(), panel);
			break;
		case BASS:
			// Bass panels do not currently expose single-panel randomization.
			break;
		case CHORD:
		case ARP:
		case DRUM:
			createRandomPanels(instrument, getInstList(instrument).size() + 1, true,
					null, panel);
			break;
		default:
			throw new IllegalStateException("Unsupported instrument: " + instrument);
		}
	}

	public void removePanel(INST instrument, int order) {
		List<? extends InstPanel> panels = getInstList(instrument);
		InstPanel panel = panels.stream().filter(candidate -> candidate.getPanelOrder() == order)
				.findFirst().get();
		((JPanel) getInstPane(instrument).getViewport().getView()).remove(panel);
		panels.remove(panel);
		context.recalculateArrangementPartMaps();
		context.repaintMainWindow();
		context.recalculateAfterPanelRemoval();
	}

	public void recreatePanels(INST instrument, List<? extends InstPart> parts) {
		recreatePanels(instrument, parts, true);
	}

	public void recreatePanels(INST instrument, List<? extends InstPart> parts,
							   boolean clearPreviousPanels) {
		if (clearPreviousPanels) {
			List<InstPanel> panels = getAffectedPanels(instrument);
			JPanel panelContainer = (JPanel) getInstPane(instrument)
					.getViewport().getView();
			for (InstPanel panel : panels) {
				panelContainer.remove(panel);
			}
			panels.clear();
		}

		InstPart.sortParts(parts);
		List<InstPanel> newPanels = new ArrayList<>();
		for (int i = 0; i < parts.size(); i++) {
			newPanels.add(addPanel(instrument, null, false));
		}
		for (int i = 0; i < newPanels.size(); i++) {
			InstPanel panel = newPanels.get(i);
			int newPanelOrder = panel.getPanelOrder();
			panel.setFromInstPart(parts.get(i));
			if (!clearPreviousPanels) {
				panel.setOrderAndOffset(newPanelOrder, parts.get(i).getOrderOffset());
			}
			if (instrument == INST.DRUM && panel.getComboPanel() != null) {
				panel.getComboPanel().reapplyHits();
			}
		}
		context.recalculateTabPaneCounts();
		context.repaintInstrumentTabs();
	}

	public int saveParts(String name, INST instrument, boolean selectiveSave)
			throws JAXBException {
		List<? extends InstPanel> panels = getAffectedPanels(instrument);
		if (selectiveSave && panels.stream().anyMatch(InstPanel::getLockInst)) {
			panels = panels.stream().filter(InstPanel::getLockInst).collect(Collectors.toList());
		}
		List<InstPart> parts = panels.stream()
				.map(panel -> panel.toInstPart(currentSeed.getAsInt()))
				.collect(Collectors.toList());
		InstPart.sortParts(parts);
		partPresetStore.save(instrument, name, parts);
		return parts.size();
	}

	public boolean loadParts(String name, INST instrument, boolean clearPreviousPanels)
			throws JAXBException, IOException {
		List<InstPart> parts = partPresetStore.load(instrument, name);
		return recreateImportedParts(instrument, parts, clearPreviousPanels);
	}

	public boolean recreateImportedParts(INST instrument, List<InstPart> parts,
										 boolean clearPreviousPanels) {
		boolean customSection = arrangementGUI.get() != null && arrangementGUI.get().isCustomSection();
		if (!clearPreviousPanels && customSection) {
			return false;
		}

		List<InstPanel> currentPanels = getAffectedPanels(instrument);
		List<InstPart> lockedParts = currentPanels.stream().filter(InstPanel::getLockInst)
				.map(panel -> panel.toInstPart(panel.getPatternSeed())).collect(Collectors.toList());
		Map<Integer, List<InstPart>> lockedPartsByOriginalOrder = lockedParts.stream()
				.collect(Collectors.groupingBy(part -> part.getOrder() - 1));
		List<InstPart> nonLockedParts = currentPanels.stream().filter(panel -> !panel.getLockInst())
				.map(panel -> panel.toInstPart(panel.getPatternSeed())).collect(Collectors.toList());

		int importedPartCount = parts.size();
		int replaceablePartCount = nonLockedParts.size();
		if (clearPreviousPanels && customSection && replaceablePartCount != importedPartCount) {
			if (importedPartCount > replaceablePartCount) {
				parts = parts.subList(0, replaceablePartCount);
			} else {
				parts.addAll(nonLockedParts.subList(importedPartCount, replaceablePartCount));
			}
		}

		if (clearPreviousPanels) {
			List<InstPart> finalParts = parts;
			lockedPartsByOriginalOrder.entrySet().stream()
					.sorted(Map.Entry.comparingByKey())
					.forEach(entry -> {
						int newIndex = Math.min(finalParts.size(), entry.getKey());
						finalParts.add(newIndex, entry.getValue().get(0));
					});
		}

		int startingOrder = clearPreviousPanels ? 0 : replaceablePartCount;
		int endingSize = parts.size() + startingOrder;
		for (int i = startingOrder; i < endingSize; i++) {
			parts.get(i - startingOrder).setOrder(i + 1);
		}

		recreatePanels(instrument, parts, clearPreviousPanels);
		return true;
	}

	private static int getLowestAvailablePanelNumber(List<? extends InstPanel> panels) {
		panels.sort(Comparator.comparing(InstPanel::getPanelOrder));
		int lowest = 0;
		for (InstPanel panel : panels) {
			if (panel.getPanelOrder() - lowest > 1) {
				return lowest + 1;
			}
			lowest++;
		}
		return lowest + 1;
	}
}
