package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Holds the chord names shown by the application between generation runs. */
public final class GeneratedChordState {
    private static volatile List<String> chordNames = Collections.emptyList();

    private GeneratedChordState() {
    }

    public static List<String> getChordNames() {
        return chordNames;
    }

    public static boolean hasChordNames() {
        return !chordNames.isEmpty();
    }

    public static void setChordNames(Collection<String> names) {
        chordNames = names == null || names.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(names));
    }

    public static void clear() {
        chordNames = Collections.emptyList();
    }
}
