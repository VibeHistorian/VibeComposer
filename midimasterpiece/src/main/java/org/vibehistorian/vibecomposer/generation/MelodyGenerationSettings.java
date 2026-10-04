package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.GUIConfig;

import java.util.List;
import java.util.Map;

/** Mutable melody inputs retained by the melody UI across compose and regenerate runs. */
public final class MelodyGenerationSettings {
    private Map<Integer, List<Integer>> targetNotes;
    private boolean randomizeTargetNotes;
    private Phrase userMelody;

    public static MelodyGenerationSettings fromConfig(GUIConfig config) {
        MelodyGenerationSettings settings = new MelodyGenerationSettings();
        if (config.getMelodyNotes() != null) {
            settings.setUserMelody(config.getMelodyNotes().makePhrase());
        }
        return settings;
    }

    public Map<Integer, List<Integer>> getTargetNotes() {
        return targetNotes;
    }

    public void setTargetNotes(Map<Integer, List<Integer>> targetNotes) {
        this.targetNotes = targetNotes;
    }

    Map<Integer, List<Integer>> getOrCreateTargetNotes() {
        if (targetNotes == null) {
            targetNotes = new java.util.HashMap<>();
        }
        return targetNotes;
    }

    public boolean isRandomizeTargetNotes() {
        return randomizeTargetNotes;
    }

    public void setRandomizeTargetNotes(boolean randomizeTargetNotes) {
        this.randomizeTargetNotes = randomizeTargetNotes;
    }

    public Phrase getUserMelody() {
        return userMelody;
    }

    public void setUserMelody(Phrase userMelody) {
        this.userMelody = userMelody;
    }
}
