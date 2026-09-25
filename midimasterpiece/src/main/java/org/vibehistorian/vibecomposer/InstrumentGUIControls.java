package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Components.VeloRect;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.util.List;

/** Controls and panel collection owned by one instrument GUI. */
public interface InstrumentGUIControls {
	JCheckBox getEnabledCheckBox();
	VeloRect getGroupFilterSlider();
	JButton getAddPanelButton();
	JButton getGeneratePanelButton();
	JTextField getRandomPanelsToGenerate();
	JScrollPane getPanelScrollPane();
	List<? extends InstPanel> getPanels();
}
