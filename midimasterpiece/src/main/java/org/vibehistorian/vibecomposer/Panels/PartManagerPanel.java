package org.vibehistorian.vibecomposer.Panels;

import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.INST;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.PartPresetStore;
import org.vibehistorian.vibecomposer.Popups.TemporaryInfoPopup;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.xml.bind.JAXBException;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;

public class PartManagerPanel extends TransparentablePanel {
	public interface Context {
		List<PartPresetStore.PresetFile> listPresets(INST part) throws IOException;
		int saveParts(String name, INST part, boolean selectiveSave) throws JAXBException;
		void loadParts(String name, INST part, boolean clearPreviousPanels)
				throws JAXBException, IOException;
		void recalculatePartCounts();
	}

    private final INST part;
    private final Context context;

    JLabel partName = new JLabel("");
    JTextField newPresetName = new JTextField("");
    ScrollComboBox<String> partPresetBox = new ScrollComboBox<>(false);
    JCheckBox overwriteExistingCheckbox = new CustomCheckBox("Overwrite", true);

    public PartManagerPanel(INST part, Context context) {
        this.part = part;
        this.context = context;

        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        this.setBorder(new BevelBorder(BevelBorder.LOWERED));

        partName.setText("Presets:");

        initPresetField();
        add(partName);
        add(newPresetName);
        add(partPresetBox);
        add(overwriteExistingCheckbox);

        Timer timer = new Timer(1000, e -> initPresetBox());
        timer.setRepeats(false);
        timer.start();
    }

    private void initPresetField() {
        ScrollComboBox.addAll(new String[] { OMNI.EMPTYCOMBO }, partPresetBox);
        newPresetName.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    try {
                        String fileName = newPresetName.getText().replaceAll(".xml", "");
                        int numParts = context.saveParts(fileName, part, true);
                        partPresetBox.addItem(fileName + " [" + numParts + "]");
                        newPresetName.setText("");
                    } catch (Exception ex) {
                        new TemporaryInfoPopup("Saving failed!", 1500);
                        LG.e(ex);
                    }
                }
            }
        });
        newPresetName.setToolTipText("<html>Type a name, press [Enter] to save preset!<br>Tip: Main instruments can be saved selectively by <b>Lock</b>ing only some of them.</html>");
    }

    private void initPresetBox() {
        try {
            for (PartPresetStore.PresetFile preset : context.listPresets(part)) {
                partPresetBox.addItem(preset.getName() + " [" + preset.getPartCount() + "]");
            }
        } catch (IOException e) {
            LG.e(e);
            new TemporaryInfoPopup("Could not initialize presets for part: " + part.getIndex(), 3000);
        }

        partPresetBox.addItemListener(event -> {
            if (event.getStateChange() == ItemEvent.SELECTED) {
                String item = (String) event.getItem();
                if (OMNI.EMPTYCOMBO.equals(item)) {
                    return;
                }
                String itemName = item.split(" \\[")[0];
                LG.i("Trying to load part preset: " + part + "/" + itemName);
                try {
                    context.loadParts(itemName, part, overwriteExistingCheckbox.isSelected());
                    partPresetBox.setVal(OMNI.EMPTYCOMBO);
                } catch (JAXBException | IOException e) {
                    LG.e(e);
                    return;
                }

                context.recalculatePartCounts();

                LG.i("Loaded preset: " + item);
            }
        });
    }

}
