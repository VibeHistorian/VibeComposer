import org.vibehistorian.vibecomposer.MidiUtils;
import java.util.*;

/** Production chord spelling and inversion, independent of the Swing widget. */
public class ChordletHarmonyFixture {
    public static void main(String[] args) {
        StringJoiner json = new StringJoiner(",\n", "[\n", "\n]");
        for (int root = 0; root < 12; root++) for (String spice : MidiUtils.SPICE_NAMES_LIST) for (int inversion = -5; inversion <= 5; inversion++) {
            int[] notes = MidiUtils.getNormalMappedChord(MidiUtils.SEMITONE_LETTERS.get(root) + spice + "." + inversion, false);
            json.add("{\"root\":" + root + ",\"spice\":\"" + spice + "\",\"inversion\":" + inversion + ",\"notes\":" + Arrays.toString(notes) + "}");
        }
        System.out.println(json);
    }
}
