import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;

/** Emits masks from the actual Java enum; compile alongside its source, with the Java app's dependency JAR. */
public class ChordSpanFillFixture {
    public static void main(String[] args) {
        int[] lengths = {0, 1, 2, 3, 4, 5, 7, 8, 9, 16, 32};
        StringBuilder json = new StringBuilder("{\n");
        boolean first = true;
        for (ChordSpanFill fill : ChordSpanFill.values()) {
            for (int length : lengths) {
                for (boolean flipped : new boolean[] {false, true}) {
                    if (!first) json.append(",\n");
                    first = false;
                    json.append("  \"").append(fill.name()).append('/').append(length).append('/')
                            .append(flipped).append("\": ")
                            .append(fill.getPatternByLength(length, flipped));
                }
            }
        }
        System.out.println(json.append("\n}"));
    }
}
