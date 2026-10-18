package org.vibehistorian.vibecomposer.gui;

import org.junit.Test;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.UITheme;
import org.vibehistorian.vibecomposer.Panels.MelodyPanel;
import org.vibehistorian.vibecomposer.Panels.SoloMuter;
import javax.swing.*;
import javax.xml.bind.JAXBContext;
import java.awt.Component;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.Collections;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import static org.junit.Assert.*;

public class ContextualMelodyUiTest {
    @Test public void rerollsUseBalancedOctavesAndPrimaryFillWhileRespectingFillLocks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JCheckBox previousAllowValues = ExtraSettingsGUI.allowValuesOutOfRange;
            try {
                ExtraSettingsGUI.allowValuesOutOfRange = new JCheckBox();
                MelodyGUI[] holder = new MelodyGUI[1];
                org.vibehistorian.vibecomposer.controllers.InstrumentPanelController controller =
                    new org.vibehistorian.vibecomposer.controllers.InstrumentPanelController(
                        context(org.vibehistorian.vibecomposer.controllers.InstrumentPanelController.Context.class),
                        role -> null, role -> holder[0], () -> 1, () -> null);
                MelodyGUI gui = holder[0] = new MelodyGUI(context(MelodyGUI.Context.class),controller);
                gui.initExtraSettingsMelody(new JPanel()); gui.initMelodyGenSettings();
                for (int order = 1; order <= 3; order++) {
                    MelodyPanel panel = gui.createPanel(context(SoloMuter.Context.class));
                    panel.setOrderAndOffset(order,order); gui.getPanels().add(panel);
                }
                MelodyPanel primary = gui.getPanels().get(0);
                for (boolean contextual : new boolean[] {true,false}) {
                    gui.contextualMelody.setSelected(contextual);
                    gui.melodyLegacyMode.setSelected(!contextual);
                    for (int seed = 1; seed <= 12; seed++) {
                        primary.setChordSpanFill(org.vibehistorian.vibecomposer.Enums.ChordSpanFill.ODD);
                        primary.setFillFlip(true);
                        gui.createRandomMelodyPanels(seed,3,false,null);
                        assertEquals(12,primary.getTranspose());
                        assertEquals(0,gui.getPanels().get(1).getTranspose());
                        assertEquals(-12,gui.getPanels().get(2).getTranspose());
                        assertEquals(org.vibehistorian.vibecomposer.Enums.ChordSpanFill.ALL,primary.getChordSpanFill());
                        assertFalse(primary.getFillFlip());
                    }
                }
                primary.setChordSpanFill(org.vibehistorian.vibecomposer.Enums.ChordSpanFill.ODD);
                java.lang.reflect.Field fill = org.vibehistorian.vibecomposer.Panels.InstPanel.class.getDeclaredField("chordSpanFill");
                fill.setAccessible(true); ((Component) fill.get(primary)).setEnabled(false);
                gui.createRandomMelodyPanels(42,3,false,null);
                assertEquals(org.vibehistorian.vibecomposer.Enums.ChordSpanFill.ODD,primary.getChordSpanFill());
            } catch (Exception failure) { throw new AssertionError(failure); }
            finally { ExtraSettingsGUI.allowValuesOutOfRange = previousAllowValues; }
        });
    }
    @SuppressWarnings("unchecked") private static <T> T context(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[] {type},(proxy,method,args) -> {
            Class<?> result = method.getReturnType();
            if (result == boolean.class) { return false; }
            if (result == int.class) { return 0; }
            if (java.util.List.class.isAssignableFrom(result)) { return Collections.emptyList(); }
            if (result.isInterface()) { return context(result); }
            return null;
        });
    }
    private static Component control(Object owner,String name) throws Exception {
        java.lang.reflect.Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true);
        return (Component) field.get(owner);
    }
    @Test public void freshLaunchAndLoadedConfigsHaveDifferentDefaultsAndVisibilitySurvivesModeChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            boolean previousFullMode = UITheme.isFullMode;
            JCheckBox previousAllowValues = ExtraSettingsGUI.allowValuesOutOfRange;
            try {
                ExtraSettingsGUI.allowValuesOutOfRange = new JCheckBox();
                UITheme.isFullMode = true;
                MelodyGUI gui = new MelodyGUI(context(MelodyGUI.Context.class),null);
                JPanel extraMelody = new JPanel(); gui.initExtraSettingsMelody(extraMelody); gui.initMelodyGenSettings();
                assertTrue(gui.contextualMelody.isSelected()); assertSame(extraMelody,gui.contextualMelody.getParent());
                assertSame(extraMelody,gui.melodyRangeRandomizeOnCompose.getParent());
                assertFalse(gui.melodyRangeRandomizeOnCompose.isSelected()); assertTrue(gui.melodyRangeRandomizeOnCompose.isVisible());
                assertFalse(gui.melodyChordNoteTarget.isVisible()); assertFalse(gui.melodyLegacyMode.isVisible());
                assertFalse(control(gui,"melodyBlockChoicePanel").isVisible()); assertFalse(control(gui,"melodyRhythmAccentsPanel").isVisible());
                assertFalse(gui.melodyCustomDurationsStrictMode.isVisible()); assertTrue(gui.melodyCustomDurationsRandomWeighting.isVisible());
                assertTrue(gui.melodyBlockTypePreferences.isVisible()); assertTrue(gui.melodyRangeMin.isVisible());
                MelodyPanel panel = gui.createPanel(context(SoloMuter.Context.class)); gui.getPanels().add(panel);
                gui.melodyRangeRandomizeOnCompose.setSelected(true);
                gui.randomizeContextualRangeOnCompose(true,17);
                assertEquals(48,gui.melodyRangeMin.getInt()); assertEquals(72,gui.melodyRangeMax.getInt());
                gui.randomizeContextualRangeOnCompose(false,17);
                int selectedLow = gui.melodyRangeMin.getInt(), selectedHigh = gui.melodyRangeMax.getInt();
                panel.setLockInst(true); gui.randomizeContextualRangeOnCompose(false,18);
                assertEquals(selectedLow,gui.melodyRangeMin.getInt()); assertEquals(selectedHigh,gui.melodyRangeMax.getInt()); panel.setLockInst(false);
                MelodyPart anchored = new MelodyPart(); anchored.setContextualPitchLocks("0=48,4=96"); panel.setFromInstPart(anchored);
                gui.randomizeContextualRangeOnCompose(false,17);
                assertTrue(gui.melodyRangeMin.getInt() <= 48); assertTrue(gui.melodyRangeMax.getInt() >= 96);
                anchored.setContextualRerollWindow("0,4"); panel.setFromInstPart(anchored);
                gui.melodyRangeMin.setInt(60); gui.melodyRangeMax.setInt(84); gui.randomizeContextualRangeOnCompose(false,18);
                assertEquals(60,gui.melodyRangeMin.getInt()); assertEquals(84,gui.melodyRangeMax.getInt());
                for (String field : new String[] {"maxNoteExceptions","noteExceptionChance","doubledRhythmChance","patternFlexible","fillPauses","alternatingRhythmChance"}) {
                    assertFalse(field,control(panel,field).isVisible());
                }
                assertTrue(control(panel,"anchorLocks").isVisible()); assertTrue(control(panel,"splitChance").isVisible());
                UITheme.isFullMode = false; panel.refreshControlVisibility();
                UITheme.isFullMode = true; panel.refreshControlVisibility(); assertFalse(control(panel,"noteExceptionChance").isVisible());
                GUIConfig old = (GUIConfig) JAXBContext.newInstance(GUIConfig.class).createUnmarshaller().unmarshal(new StringReader("<GUIConfig/>"));
                assertFalse(old.isContextualMelody()); gui.loadFromConfig(old);
                assertFalse(old.isMelodyRangeRandomizeOnCompose()); assertFalse(gui.melodyRangeRandomizeOnCompose.isVisible());
                assertFalse(gui.contextualMelody.isSelected()); assertTrue(gui.melodyChordNoteTarget.isVisible());
                assertTrue(control(panel,"noteExceptionChance").isVisible()); assertFalse(control(panel,"anchorLocks").isVisible());
                old.setContextualMelody(true); old.setMelodyRangeRandomizeOnCompose(true); gui.loadFromConfig(old);
                assertTrue(gui.melodyRangeRandomizeOnCompose.isSelected());
                assertTrue(gui.contextualMelody.isSelected()); assertFalse(control(panel,"noteExceptionChance").isVisible());
                MelodyPanel copied = gui.createPanel(context(SoloMuter.Context.class)); copied.refreshControlVisibility();
                assertFalse(control(copied,"doubledRhythmChance").isVisible());
                gui.contextualMelody.setSelected(false); copied.refreshControlVisibility(); assertTrue(control(copied,"doubledRhythmChance").isVisible());
                gui.contextualMelody.setSelected(true); gui.getPanels().add(copied);
                gui.melody1ForcePatterns.setSelected(true);
                panel.setChordNoteChoices(java.util.Arrays.asList(0,2,2,4)); copied.setChordNoteChoices(java.util.Arrays.asList(4,3,2,0));
                gui.prepareMelodyPatterns(true,false,17);
                assertEquals(java.util.Arrays.asList(4,3,2,0),copied.getChordNoteChoices());
                gui.melodyLegacyMode.setSelected(true); assertFalse(gui.melodyBlockTypePreferences.isVisible());
                gui.melodyLegacyMode.setSelected(false); assertTrue(gui.melodyBlockTypePreferences.isVisible());
            } catch (Exception failure) { throw new AssertionError(failure); }
            finally { UITheme.isFullMode = previousFullMode; ExtraSettingsGUI.allowValuesOutOfRange = previousAllowValues; }
        });
    }
}
