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
import static org.junit.Assert.*;

public class ContextualMelodyUiTest {
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
            try {
                UITheme.isFullMode = true;
                MelodyGUI gui = new MelodyGUI(context(MelodyGUI.Context.class),null);
                JPanel extraMelody = new JPanel(); gui.initExtraSettingsMelody(extraMelody); gui.initMelodyGenSettings();
                assertTrue(gui.contextualMelody.isSelected()); assertSame(extraMelody,gui.contextualMelody.getParent());
                assertFalse(gui.melodyChordNoteTarget.isVisible()); assertFalse(gui.melodyLegacyMode.isVisible());
                assertFalse(control(gui,"melodyBlockChoicePanel").isVisible()); assertFalse(control(gui,"melodyRhythmAccentsPanel").isVisible());
                assertFalse(gui.melodyCustomDurationsStrictMode.isVisible()); assertTrue(gui.melodyCustomDurationsRandomWeighting.isVisible());
                assertTrue(gui.melodyBlockTypePreferences.isVisible()); assertTrue(gui.melodyRangeMin.isVisible());
                MelodyPanel panel = gui.createPanel(context(SoloMuter.Context.class)); gui.getPanels().add(panel);
                for (String field : new String[] {"maxNoteExceptions","noteExceptionChance","doubledRhythmChance","patternFlexible","fillPauses","alternatingRhythmChance"}) {
                    assertFalse(field,control(panel,field).isVisible());
                }
                assertTrue(control(panel,"anchorLocks").isVisible()); assertTrue(control(panel,"splitChance").isVisible());
                UITheme.isFullMode = false; panel.refreshControlVisibility();
                UITheme.isFullMode = true; panel.refreshControlVisibility(); assertFalse(control(panel,"noteExceptionChance").isVisible());
                GUIConfig old = (GUIConfig) JAXBContext.newInstance(GUIConfig.class).createUnmarshaller().unmarshal(new StringReader("<GUIConfig/>"));
                assertFalse(old.isContextualMelody()); gui.loadFromConfig(old);
                assertFalse(gui.contextualMelody.isSelected()); assertTrue(gui.melodyChordNoteTarget.isVisible());
                assertTrue(control(panel,"noteExceptionChance").isVisible()); assertFalse(control(panel,"anchorLocks").isVisible());
                old.setContextualMelody(true); gui.loadFromConfig(old);
                assertTrue(gui.contextualMelody.isSelected()); assertFalse(control(panel,"noteExceptionChance").isVisible());
                MelodyPanel copied = gui.createPanel(context(SoloMuter.Context.class)); copied.refreshControlVisibility();
                assertFalse(control(copied,"doubledRhythmChance").isVisible());
                gui.contextualMelody.setSelected(false); copied.refreshControlVisibility(); assertTrue(control(copied,"doubledRhythmChance").isVisible());
                gui.melodyLegacyMode.setSelected(true); assertFalse(gui.melodyBlockTypePreferences.isVisible());
                gui.melodyLegacyMode.setSelected(false); assertTrue(gui.melodyBlockTypePreferences.isVisible());
            } catch (Exception failure) { throw new AssertionError(failure); }
            finally { UITheme.isFullMode = previousFullMode; }
        });
    }
}
