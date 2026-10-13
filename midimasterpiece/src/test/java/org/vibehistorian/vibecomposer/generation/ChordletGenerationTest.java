package org.vibehistorian.vibecomposer.generation;

import org.junit.Test;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.Components.Chordlet;
import org.vibehistorian.vibecomposer.Panels.ChordletPanel;

import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ChordletGenerationTest {
    @Test public void localEditRespectsLocksAndUndoRestoresChordIdentityAndLocks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GUIConfig config = new GUIConfig(); config.setSpiceChance(0);
            ChordletPanel panel = new ChordletPanel(600,"C","Am","F","G7");
            panel.setGenerationContext(() -> config, () -> { });
            panel.setLockedIndices(Collections.singletonList(0));
            List<String> original = panel.getChordList();
            panel.rerollChord(panel.getChordletsRaw().get(0));
            assertEquals(original,panel.getChordList());
            panel.rerollChord(panel.getChordletsRaw().get(1));
            assertNotEquals("Am",panel.getChordList().get(1));
            assertEquals("C",panel.getChordList().get(0));
            assertEquals(Collections.singletonList(0),panel.getLockedIndices());
            panel.undoGenerationEdit();
            assertEquals(original,panel.getChordList());
            assertEquals(Collections.singletonList(0),panel.getLockedIndices());
            panel.redoGenerationEdit(); assertNotEquals(original,panel.getChordList());
            panel.undoGenerationEdit(); assertEquals(original,panel.getChordList());
            assertFalse(panel.setupChords(Arrays.asList("C","not-a-chord")));
            assertEquals(original,panel.getChordList());
        });
    }

    @Test public void persistentEventIdsSurviveEditsUndoAndReordering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChordletPanel panel = new ChordletPanel(600,"C","Am","G7"); List<String> ids = panel.getEventIds();
            panel.rememberGenerationEdit(); panel.setupChords(Arrays.asList("C","E7","G7"));
            assertEquals(ids,panel.getEventIds()); panel.undoGenerationEdit(); assertEquals(ids,panel.getEventIds());
            panel.rememberGenerationEdit(); Collections.swap(panel.getChordletsRaw(),0,1);
            assertEquals(ids.get(0),panel.getEventIds().get(1)); panel.undoGenerationEdit(); assertEquals(ids,panel.getEventIds());
            GUIConfig config = new GUIConfig(); config.setChordEventIds(ids); ids.clear(); assertEquals(3,config.getChordEventIds().size());
        });
    }

    @Test public void flatAndSharpRootAndInversionSurviveResetToTriad() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChordletPanel panel = new ChordletPanel(600,Arrays.asList("Bb7.1","F#m7.-1"));
            assertEquals(Arrays.asList("Bb7.1","F#m7.-1"),panel.getChordList());
            Chordlet flat = panel.getChordletsRaw().get(0), sharp = panel.getChordletsRaw().get(1);
            flat.resetToBaseChord(); sharp.resetToBaseChord();
            assertEquals(Arrays.asList("Bb.1","F#m.-1"),panel.getChordList());
        });
    }
}
