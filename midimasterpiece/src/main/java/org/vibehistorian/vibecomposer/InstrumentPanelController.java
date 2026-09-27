package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.Wrappers.InstPartsWrapper;

import javax.swing.*;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Owns the shared lifecycle of instrument panels inside their instrument tabs. */
public final class InstrumentPanelController {
	public interface Context {
		InstPanel createPanel(int instrument);
		void configurePanel(InstPanel panel);
		List<InstPanel> getAffectedPanels(int instrument);
		List<? extends InstPanel> getPanels(int instrument);
		int getRandomPanelCount(int instrument);
		JScrollPane getPanelScrollPane(int instrument);
		boolean isFullMode();
		boolean isCustomSection();
		boolean reverseDrumPanelOrder();
		void removeComboBoxArrows(InstPanel panel);
		void recalculateArrangementPartMaps();
		void recalculateTabPaneCounts();
		void recalculateAfterPanelGeneration();
		void recalculateAfterPanelAddition();
		void repaintInstrumentTabs();
		void repaintMainWindow();
		boolean canRegenerateOnChange();
		void regenerate();
		int getCurrentSeed();
		void randomizePanels(int instrument, int panelCount, boolean onlyAdd, Integer seed,
				InstPanel randomizedPanel);
	}

	private final Context context;

	public InstrumentPanelController(Context context) {
		this.context = context;
	}

	public InstPanel addPanel(int instrument) {
		return addPanel(instrument, null, true);
	}

	public InstPanel addPanel(int instrument, boolean recalculateArrangement) {
		return addPanel(instrument, null, recalculateArrangement);
	}

	public InstPanel addPanel(int instrument, InstPart initializingPart,
			boolean recalculateArrangement) {
		InstPanel panel = context.createPanel(instrument);
		context.configurePanel(panel);
		List<InstPanel> affectedPanels = context.getAffectedPanels(instrument);
		int panelOrder = getLowestAvailablePanelNumber(affectedPanels);

		panel.getToggleableComponents().forEach(component ->
				component.setVisible(context.isFullMode()));
		if (context.isCustomSection()) {
			panel.toggleGlobalElements(false);
			panel.toggleEnabledCopyRemove(false);
			if (instrument == 4) {
				panel.getInstrumentBox().setEnabled(true);
			}
		} else {
			panel.setBackground(OMNI.alphen(Constants.instColors[instrument], 60));
		}

		if (initializingPart != null) {
			panel.setFromInstPart(initializingPart);
		}
		panel.setOrderAndOffset(panelOrder,
				initializingPart == null ? panelOrder : initializingPart.getOrderOffset());

		affectedPanels.add(panelOrder - 1, panel);
		context.removeComboBoxArrows(panel);
		if (recalculateArrangement && ArrangementGUI.actualArrangement != null
				&& ArrangementGUI.actualArrangement.getSections() != null) {
			context.recalculateArrangementPartMaps();
		}

		JPanel panelContainer = (JPanel) context.getPanelScrollPane(instrument)
				.getViewport().getView();
		if (instrument < 4 || !context.reverseDrumPanelOrder()) {
			panelContainer.add(panel, panelOrder - 1);
		} else {
			panelContainer.add(panel, affectedPanels.size() - panelOrder);
		}
		return panel;
	}

	public void addRandomPanel(int instrument) {
		createRandomPanels(instrument, context.getAffectedPanels(instrument).size() + 1,
				true, null, null);
		context.recalculateAfterPanelAddition();
		context.repaintMainWindow();
	}

	public void generatePanels(int instrument) {
		generatePanels(instrument, false);
	}

	public void generatePanels(int instrument, boolean triggerRegenerate) {
		int panelCount = context.isCustomSection()
				? context.getPanels(instrument).size()
				: context.getRandomPanelCount(instrument);
		createRandomPanels(instrument, panelCount, false, null, null);
		context.recalculateAfterPanelGeneration();
		if (triggerRegenerate && context.canRegenerateOnChange()) {
			context.regenerate();
		}
	}

	public void createRandomPanels(int instrument, int panelCount, boolean onlyAdd) {
		createRandomPanels(instrument, panelCount, onlyAdd, null, null);
	}

	public void createRandomPanels(int instrument, int panelCount, boolean onlyAdd,
			Integer seed, InstPanel randomizedPanel) {
		context.randomizePanels(instrument, panelCount, onlyAdd, seed, randomizedPanel);
		context.repaintMainWindow();
	}

	public void randomizePanel(InstPanel panel) {
		int instrument = panel.getPartNum();
		if (instrument == 0) {
			createRandomPanels(instrument, context.getPanels(instrument).size() + 1, true,
					new java.util.Random().nextInt(), panel);
		} else if (instrument == 1) {
			// Bass panels do not currently expose single-panel randomization.
		} else if (instrument == 2 || instrument == 3 || instrument == 4) {
			createRandomPanels(instrument, context.getPanels(instrument).size() + 1, true,
					null, panel);
		}
	}

	public void removePanel(int instrument, int order) {
		List<? extends InstPanel> panels = context.getPanels(instrument);
		InstPanel panel = panels.stream().filter(candidate -> candidate.getPanelOrder() == order)
				.findFirst().get();
		((JPanel) context.getPanelScrollPane(instrument).getViewport().getView()).remove(panel);
		panels.remove(panel);
		context.recalculateArrangementPartMaps();
		context.repaintMainWindow();
	}

	public void recreatePanels(int instrument, List<? extends InstPart> parts) {
		recreatePanels(instrument, parts, true);
	}

	public void recreatePanels(int instrument, List<? extends InstPart> parts,
			boolean clearPreviousPanels) {
		if (clearPreviousPanels) {
			List<InstPanel> panels = context.getAffectedPanels(instrument);
			JPanel panelContainer = (JPanel) context.getPanelScrollPane(instrument)
					.getViewport().getView();
			for (InstPanel panel : panels) {
				panelContainer.remove(panel);
			}
			panels.clear();
		}

		InstPart.sortParts(parts);
		List<InstPanel> newPanels = new ArrayList<>();
		for (int i = 0; i < parts.size(); i++) {
			newPanels.add(addPanel(instrument, false));
		}
		for (int i = 0; i < newPanels.size(); i++) {
			InstPanel panel = newPanels.get(i);
			int newPanelOrder = panel.getPanelOrder();
			panel.setFromInstPart(parts.get(i));
			if (!clearPreviousPanels) {
				panel.setOrderAndOffset(newPanelOrder, parts.get(i).getOrderOffset());
			}
			if (instrument == 4 && panel.getComboPanel() != null) {
				panel.getComboPanel().reapplyHits();
			}
		}
		context.recalculateTabPaneCounts();
		context.repaintInstrumentTabs();
	}

	public int saveParts(String path, int instrument, boolean selectiveSave) throws JAXBException {
		Class<? extends InstPartsWrapper> wrapperClass = InstPartsWrapper.getWrapperClass(instrument);
		JAXBContext jaxbContext = JAXBContext.newInstance(wrapperClass, InstPartsWrapper.class);
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		InstPartsWrapper<?> wrapper = InstPartsWrapper.forClass(wrapperClass);

		List<? extends InstPanel> panels = context.getAffectedPanels(instrument);
		if (selectiveSave && panels.stream().anyMatch(InstPanel::getLockInst)) {
			panels = panels.stream().filter(InstPanel::getLockInst).collect(Collectors.toList());
		}
		List<InstPart> parts = panels.stream()
				.map(panel -> panel.toInstPart(context.getCurrentSeed()))
				.collect(Collectors.toList());
		InstPart.sortParts(parts);
		wrapper.setParts(parts);
		marshaller.marshal(wrapper, new File(path));
		LG.i("File saved: " + path);
		return parts.size();
	}

	public boolean recreateImportedParts(int instrument, List<InstPart> parts,
			boolean clearPreviousPanels) {
		boolean customSection = context.isCustomSection();
		if (!clearPreviousPanels && customSection) {
			return false;
		}

		List<InstPanel> currentPanels = context.getAffectedPanels(instrument);
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
