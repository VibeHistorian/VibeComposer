import org.vibehistorian.vibecomposer.Enums.RhythmPattern;

/** Calls the production Java enum; compile with its source and the Java dependency JAR. */
public class RhythmPatternFixture {
    public static void main(String[] args) {
        RhythmPattern[] patterns = {RhythmPattern.FULL, RhythmPattern.ALT, RhythmPattern.ONEPER4,
                RhythmPattern.TRESILLO, RhythmPattern.SINGLE, RhythmPattern.ONESIX};
        int[] hits = {1, 3, 5, 8, 9, 16, 31, 32};
        int[] shifts = {0, 1, 3, 8};
        StringBuilder json = new StringBuilder("{\n");
        boolean first = true;
        for (RhythmPattern pattern : patterns) {
            for (int length : hits) {
                for (int shift : shifts) {
                    if (!first) json.append(",\n");
                    first = false;
                    json.append("  \"").append(pattern.name()).append('/').append(length).append('/')
                            .append(shift).append("\": ").append(pattern.getPatternByLength(length, shift));
                }
            }
        }
        System.out.println(json.append("\n}"));
    }
}
