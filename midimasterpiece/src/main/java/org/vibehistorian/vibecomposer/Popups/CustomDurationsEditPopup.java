package org.vibehistorian.vibecomposer.Popups;

import jm.music.data.Score;
import org.vibehistorian.vibecomposer.Components.MidiDropPane;
import org.vibehistorian.vibecomposer.Components.MidiEditArea;
import org.vibehistorian.vibecomposer.Components.MidiListCellRenderer;
import org.vibehistorian.vibecomposer.Components.VeloRect;
import org.vibehistorian.vibecomposer.Helpers.FileTransferHandler;
import org.vibehistorian.vibecomposer.Helpers.PartExt;
import org.vibehistorian.vibecomposer.Helpers.PhraseExt;
import org.vibehistorian.vibecomposer.Helpers.PhraseNote;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.JMusicUtilsCustom;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.MelodyPanel;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.io.File;
import java.util.List;

import static org.vibehistorian.vibecomposer.MidiGenerator.DBL_ERR;

public class CustomDurationsEditPopup extends CloseablePopup {

    public MidiEditArea cdMvea;
    public JList<File> generatedMidi;
    private int part = -1;
    private int partOrder = -1;

    final int maxPitch = MelodyPanel.CUSTOM_DURATIONS_LIMIT-1;

    public CustomDurationsEditPopup(PhraseNotes values, List<Integer> chances, InstPanel parentComponent) {
        super("Custom Durations Editor", 15, new Point(0,0), parentComponent);
        part = parentComponent.getPartNum();
        partOrder = parentComponent.getPanelOrder();

        JPanel mainVertPanel = new JPanel();
        mainVertPanel.setLayout(new BorderLayout());

        JPanel cdMveaPanel = new JPanel();
        cdMveaPanel.setLayout(new BoxLayout(cdMveaPanel, BoxLayout.X_AXIS));
        cdMvea = new MidiEditArea(0, maxPitch, values);
        cdMvea.splitNotesByGrid = true;
        cdMvea.drawNoteStrings = false;
        cdMvea.sectionLength = MidiGenerator.Durations.WHOLE_NOTE;
        cdMvea.timeGridChoice = 0;
        cdMvea.forceMarginTime = true;
        //cdMvea.setPop(null);
        cdMvea.setPreferredSize(new Dimension(920, 500));
        JPanel patternChancePanel = new JPanel();
        patternChancePanel.setPreferredSize(new Dimension(30,415));
        patternChancePanel.setLayout(new GridLayout(0,1,0,10));
        JPanel patternChancePanelWrapper = new JPanel();
        patternChancePanelWrapper.setPreferredSize(new Dimension(80,500));
        //patternChancePanelWrapper.add(new JLabel());
        for (int i = MelodyPanel.CUSTOM_DURATIONS_LIMIT - 1; i >= 0; i--) {
            VeloRect vr = VeloRect.percent(chances.get(i));
            vr.setMargin(new Insets(10,0,10,0));
            int finalI = i;
            vr.updatedValueListener = newVal -> {
                chances.set(finalI, newVal);
            };
            patternChancePanel.add(vr);
        }
        JLabel chanceLabel = new JLabel("CHANCE%");
        chanceLabel.setPreferredSize(new Dimension(60, 10));
        patternChancePanelWrapper.add(chanceLabel);
        patternChancePanelWrapper.add(patternChancePanel);
        cdMveaPanel.setPreferredSize(new Dimension(1000, 500));
        cdMveaPanel.add(patternChancePanelWrapper);
        cdMveaPanel.add(cdMvea);
        cdMvea.addKeyboardControls(cdMveaPanel);
        cdMvea.setAndRepaint();
        JPanel midiDragDropPanel = makeMidiDragDropPanel();
        midiDragDropPanel.setPreferredSize(new Dimension(1000, 100));
        mainVertPanel.add(midiDragDropPanel, BorderLayout.NORTH);
        mainVertPanel.add(cdMveaPanel, BorderLayout.CENTER);
        frame.add(mainVertPanel);
        frame.pack();
        frame.setVisible(true);
    }

    @Override
    protected void addFrameWindowOperation() {
        frame.addWindowListener(CloseablePopup.EMPTY_WINDOW_LISTENER);
    }

    private JPanel makeMidiDragDropPanel() {
        JPanel midiDragDropPanel = new JPanel();
        midiDragDropPanel.setLayout(new GridLayout(0, 1));

        generatedMidi = new JList<>();
        MidiListCellRenderer dndRenderer = new MidiListCellRenderer();
        dndRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        generatedMidi.setCellRenderer(dndRenderer);
        generatedMidi.setBorder(new BevelBorder(BevelBorder.RAISED));
        generatedMidi.setTransferHandler(new FileTransferHandler(e -> {
            return buildMidiFileFromNotes();
        }));
        generatedMidi.setDragEnabled(true);
        generatedMidi.setListData(new File[]{new File("tempMidi.mid")});

        midiDragDropPanel.add(generatedMidi);
        midiDragDropPanel.add(new MidiDropPane(e -> {
            PhraseNotes pn = new PhraseNotes(e);
            pn.remakeNoteStartTimes(true);

            double target = cdMvea.sectionLength - DBL_ERR;
            double negativeOffset = cdMvea.sectionLength;
            for (PhraseNote n : pn) {
                if (n.getPitch() < 0) {
                    continue;
                }
                if (n.getStartTime() < target) {
                    n.setPitch(0);
                } else {
                    // move to next line (pattern#) + move back in time based on how far off the start it is
                    double chordNum = Math.floor(n.getStartTime() / target);
                    n.setPitch((int)chordNum);
                    n.setOffset(n.getOffset() - chordNum * negativeOffset);
                }
            }

            double length = pn.stream().map(PhraseNote::getRv).mapToDouble(f -> f).sum();
            LG.i("Dropped MIDI Length: " + length);
            /*if (length > cdMvea.sectionLength + DBL_ERR) {
                return null;
            } else if (length < cdMvea.sectionLength - DBL_ERR) {
                PhraseNote lastNote = pn.get(pn.size() - 1);
                lastNote.setRv(lastNote.getRv() + cdMvea.sectionLength - length);
            }*/
            cdMvea.setCustomValues(pn.copy());

            return pn;
        }));
        return midiDragDropPanel;
    }

    private File buildMidiFileFromNotes() {
        PhraseExt phr = cdMvea.getValues().makePhrase();

        Score scr = new Score();
        PartExt prt = PartExt.makeFillerPart();
        prt.add(phr);
        scr.add(prt);

        JMusicUtilsCustom.midi(scr, "tempMidi.mid");
        return new File("tempMidi.mid");
    }
}
