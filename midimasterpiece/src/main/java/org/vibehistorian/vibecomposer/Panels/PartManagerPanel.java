package org.vibehistorian.vibecomposer.Panels;

import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Constants;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Popups.TemporaryInfoPopup;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.xml.bind.JAXBException;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class PartManagerPanel extends TransparentablePanel {

    private int part = -1;

    JLabel partName = new JLabel("");
    JTextField newPresetName = new JTextField("");
    ScrollComboBox<String> partPresetBox = new ScrollComboBox<>(false);
    JCheckBox overwriteExistingCheckbox = new CustomCheckBox("Overwrite", true);

    public PartManagerPanel(int partNum) {
        part = partNum;

        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        this.setBorder(new BevelBorder(BevelBorder.LOWERED));

        String folderName = Constants.instNames[partNum];
        partName.setText("Presets:");

        initPresetField(folderName);
        add(partName);
        add(newPresetName);
        add(partPresetBox);
        add(overwriteExistingCheckbox);

        Timer timer = new Timer(1000, e -> initPresetBox(folderName));
        timer.setRepeats(false);
        timer.start();
    }

    private void initPresetField(String folderName) {
        ScrollComboBox.addAll(new String[] { OMNI.EMPTYCOMBO }, partPresetBox);
        newPresetName.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    String partsDirectory = "PartPresets/" + folderName + "/";
                    File makeSavedDir = new File(partsDirectory);
                    makeSavedDir.mkdir();

                    try {
                        String dirPath = makeSavedDir.getPath().toString();
                        String fileName = newPresetName.getText().replaceAll(".xml", "");
                        int numParts = VibeComposerGUI.marshalParts(dirPath + "/" + fileName + ".xml", part, true);
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

    private void initPresetBox(String folderName) {
        File folder = new File("PartPresets/" + folderName);
        if (folder.exists()) {
            File[] listOfFiles = folder.listFiles();
            for (File f : listOfFiles) {
                if (f.isFile()) {
                    String fileName = f.getName();
                    int pos = fileName.lastIndexOf(".");
                    if (pos > 0 && pos < (fileName.length() - 1)) {
                        fileName = fileName.substring(0, pos);
                    }
                    try {
                        int numOfParts = countStringOccurrences(f, "</" + Constants.instPartNames[part] + "Part>");
                        partPresetBox.addItem(fileName + " [" + numOfParts + "]");
                    } catch (IOException e) {
                        LG.e(e);
                        new TemporaryInfoPopup("Could not initialize presets for part: " + part, 3000);
                    }
                }
            }
        }


        partPresetBox.addItemListener(event -> {
            if (event.getStateChange() == ItemEvent.SELECTED) {
                String item = (String) event.getItem();
                if (OMNI.EMPTYCOMBO.equals(item)) {
                    return;
                }
                String itemName = item.split(" \\[")[0];
                LG.i("Trying to load part preset: " + folderName + "/" + itemName);

                // check if file exists
                File loadedFile = new File("PartPresets/" + folderName + "/" + itemName + ".xml");
                if (loadedFile.exists()) {
                    try {
                        VibeComposerGUI.vibeComposerGUI.unmarshallParts(loadedFile, part, overwriteExistingCheckbox.isSelected());
                        partPresetBox.setVal(OMNI.EMPTYCOMBO);
                    } catch (JAXBException | IOException e) {
                        LG.e(e);
                        return;
                    }
                }

                VibeComposerGUI.vibeComposerGUI.recalculateTabPaneCounts();
                VibeComposerGUI.vibeComposerGUI.recalculateGenerationCounts();
                VibeComposerGUI.vibeComposerGUI.recalculateSoloMuters();

                LG.i("Loaded preset: " + item);
            }
        });
    }

    private static int countStringOccurrences(File file, String searchString) throws IOException {
        if (file == null || !file.exists() || searchString == null || searchString.isEmpty()) {
            throw new IllegalArgumentException("Invalid file or search string.");
        }

        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                count += countOccurrencesInLine(line, searchString);
            }
        }
        return count;
    }

    private static int countOccurrencesInLine(String line, String searchString) {
        int count = 0;
        int index = 0;
        while ((index = line.indexOf(searchString, index)) != -1) {
            count++;
            index += searchString.length(); // Move past the current occurrence
        }
        return count;
    }

}
