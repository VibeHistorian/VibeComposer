package org.vibehistorian.vibecomposer.gui;

import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.Helpers.PhraseNote;
import org.vibehistorian.vibecomposer.Panels.ChordletPanel;
import org.vibehistorian.vibecomposer.generation.*;
import javax.swing.*;
import java.awt.BorderLayout;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Reviewable harmonic edits; generation, labels and locks commit together or remain unchanged. */
final class HarmonyEditorActions {
    private final ChordletPanel editor;
    private final ChordGUI.Context context;
    private final Supplier<String> durations;
    HarmonyEditorActions(ChordletPanel editor,ChordGUI.Context context,Supplier<String> durations) {
        this.editor = editor; this.context = context; this.durations = durations;
    }
    private HarmonyTimeline timeline() {
        List<Double> beats = new ArrayList<>(); String[] values = durations.get().split(",");
        for (int i = 0; i < editor.chordCount(); i++) { beats.add(i < values.length ? Double.parseDouble(values[i]) : 4.0); }
        return HarmonyTimeline.fromSymbols(editor.getChordList(),beats,new HarmonyTimeline.Context("C",0,context.getScaleMode(),
                context.getGUIConfig().getProgressionStrategy() != HarmonicProgressionPlanner.Strategy.MODAL)).withIdentities(editor.getEventIds());
    }
    void locks() {
        if (editor.chordCount() == 0) { return; }
        JComboBox<String> chord = new JComboBox<>();
        for (int i = 0; i < editor.chordCount(); i++) { chord.addItem((i + 1) + ": " + editor.getChordList().get(i)); }
        JCheckBox root = new JCheckBox("Root"), quality = new JCheckBox("Quality and color"), bass = new JCheckBox("Bass / inversion"), duration = new JCheckBox("Duration");
        Runnable load = () -> { int mask = editor.getChordletsRaw().get(chord.getSelectedIndex()).getGenerationLocks();
            root.setSelected((mask & ChordLock.ROOT) != 0); quality.setSelected((mask & ChordLock.QUALITY) != 0);
            bass.setSelected((mask & ChordLock.BASS) != 0); duration.setSelected((mask & ChordLock.DURATION) != 0); };
        chord.addActionListener(e -> load.run()); load.run(); JPanel controls = new JPanel(new java.awt.GridLayout(0,1));
        controls.add(chord); controls.add(root); controls.add(quality); controls.add(bass); controls.add(duration);
        if (JOptionPane.showConfirmDialog(editor,controls,"Preserve chord properties during generation",JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) { return; }
        int index = chord.getSelectedIndex(), mask = (root.isSelected() ? ChordLock.ROOT : 0) | (quality.isSelected() ? ChordLock.QUALITY : 0)
                | (bass.isSelected() ? ChordLock.BASS : 0) | (duration.isSelected() ? ChordLock.DURATION : 0);
        List<ChordLock> locks = editor.getPropertyLocks().stream().filter(lock -> lock.getIndex() != index).collect(Collectors.toList());
        if (mask != 0) { locks.add(new ChordLock(index,mask)); } editor.rememberGenerationEdit(); editor.setPropertyLocks(locks);
    }
    void alternatives() {
        context.copyGUItoConfig();
        try {
            String number = JOptionPane.showInputDialog(editor,"Chord index (first chord = 0)","0"); if (number == null) { return; }
            int index = Integer.parseInt(number);
            HarmonyOperations.Operation operation = (HarmonyOperations.Operation) JOptionPane.showInputDialog(editor,"Harmonic operation",
                    "Contextual alternatives",JOptionPane.PLAIN_MESSAGE,null,HarmonyOperations.Operation.values(),HarmonyOperations.Operation.CONTEXTUAL);
            if (operation == null) { return; }
            HarmonyTimeline source = timeline(); Set<String> locked = new HashSet<>();
            for (int i : editor.getLockedIndices()) { locked.add(source.events().get(i).id); }
            List<ChordLock> properties = editor.getPropertyLocks();
            List<HarmonyOperations.Alternative> choices = HarmonyOperations.alternatives(source,index,operation,
                    context.getGUIConfig().getMelodyStyle() == ContextualMelodyPlanner.Style.JAZZ,locked,importantMelody(source));
            choices = choices.stream().filter(candidate -> properties.stream().filter(lock -> lock.getIndex() == index)
                    .allMatch(lock -> lock.matches(ChordSymbol.parse(source.events().get(index).symbol),ChordSymbol.parse(candidate.symbol)))).collect(Collectors.toList());
            if (choices.isEmpty()) { JOptionPane.showMessageDialog(editor,"No alternative satisfies this operation, destination, and locks."); return; }
            HarmonyOperations.Alternative candidate = choices.size() == 1 ? choices.get(0) : (HarmonyOperations.Alternative)
                    JOptionPane.showInputDialog(editor,"Ranked alternatives satisfying the destination and locks","Choose an alternative",
                            JOptionPane.PLAIN_MESSAGE,null,choices.toArray(),choices.get(0));
            if (candidate == null) { return; }
            JPanel preview = new JPanel(new BorderLayout());
            preview.add(new JLabel(source.events().get(index).symbol + " → " + candidate.symbol + ": " + candidate.explanation),BorderLayout.CENTER);
            JButton audition = new JButton("Audition"); audition.setEnabled(context.canAuditionHarmony());
            audition.setToolTipText("Preview through the current chord instrument after a MIDI composition is available.");
            audition.addActionListener(e -> context.auditionHarmony(candidate.harmony.events().get(index).pitches())); preview.add(audition,BorderLayout.SOUTH);
            if (JOptionPane.showConfirmDialog(editor,preview,"Apply harmonic alternative",JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                editor.rememberGenerationEdit(); editor.setupChords(candidate.harmony.events().stream().map(event -> event.symbol).collect(Collectors.toList()));
                editor.setPropertyLocks(properties);
            }
        } catch (IllegalArgumentException invalid) { JOptionPane.showMessageDialog(editor,invalid.getMessage()); }
    }
    private Map<String,List<Integer>> importantMelody(HarmonyTimeline source) {
        Map<String,List<Integer>> result = new HashMap<>();
        if (context.getGUIConfig().getMelodyNotes() == null) { return result; }
        double cursor = 0;
        for (PhraseNote note : context.getGUIConfig().getMelodyNotes()) {
            long start = Math.max(0,HarmonyTimeline.ticks(cursor + note.getOffset()));
            long end = Math.min(source.length(),start + Math.max(0,HarmonyTimeline.ticks(note.getDuration())));
            if (note.getPitch() >= 0 && start < source.length() && end > start && (start % 960 == 0 || end - start >= 960)) {
                for (HarmonyTimeline.Event event : source.overlapping(start,end)) { result.computeIfAbsent(event.id,key -> new ArrayList<>()).add(note.getPitch()); }
            }
            cursor += note.getRv();
        }
        return result;
    }

    void harmonize() {
        context.copyGUItoConfig(); GUIConfig config = context.getGUIConfig();
        if (config.getMelodyNotes() == null || config.getMelodyNotes().isEmpty()) { JOptionPane.showMessageDialog(editor,"Attach a user melody before harmonizing."); return; }
        try {
            List<HarmonyHarmonizer.MelodyNote> melody = new ArrayList<>(); double cursor = 0;
            for (PhraseNote note : config.getMelodyNotes()) {
                if (note.getDuration() > 0) { melody.add(new HarmonyHarmonizer.MelodyNote(HarmonyTimeline.ticks(cursor + note.getOffset()),
                        HarmonyTimeline.ticks(note.getDuration()),note.getPitch() < 0 ? -1 : note.getPitch(),Math.abs(cursor - Math.rint(cursor)) < 0.00001 ? 1 : 0)); }
                cursor += note.getRv();
            }
            HarmonyTimeline source = timeline(); Map<Integer,String> locks = new HashMap<>();
            for (int i : editor.getLockedIndices()) { locks.put(i,source.events().get(i).symbol); }
            // Property-locked events constrain harmonization to their exact source rather than relaxing a lock.
            for (ChordLock lock : editor.getPropertyLocks()) { if ((lock.getMask() & (ChordLock.ROOT | ChordLock.QUALITY | ChordLock.BASS)) != 0) { locks.put(lock.getIndex(),source.events().get(lock.getIndex()).symbol); } }
            List<Double> beats = source.events().stream().map(event -> (event.end - event.start) / 960.0).collect(Collectors.toList());
            HarmonyTimeline result = HarmonyHarmonizer.harmonize(config,melody,beats,locks);
            List<ChordLock> properties = editor.getPropertyLocks(); editor.rememberGenerationEdit();
            editor.setupChords(result.events().stream().map(event -> event.symbol).collect(Collectors.toList())); editor.setPropertyLocks(properties);
        } catch (IllegalArgumentException invalid) { JOptionPane.showMessageDialog(editor,invalid.getMessage()); }
    }
}
