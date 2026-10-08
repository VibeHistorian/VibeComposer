import org.vibehistorian.vibecomposer.Enums.RhythmPattern;

/** Exhaustive supported grids, calling the current production Java algorithm. */
public class EuclideanPatternFixture {
    public static void main(String[] args) {
        StringBuilder json = new StringBuilder("{\n");
        boolean first = true;
        for (int hits = 1; hits <= 32; hits++) {
            for (int pulses = 0; pulses <= hits; pulses++) {
                for (int shift = 0; shift <= 8; shift++) {
                    if (!first) json.append(",\n");
                    first = false;
                    json.append("  \"").append(hits).append('/').append(pulses).append('/')
                            .append(shift).append("\": ")
                            .append(RhythmPattern.makeEuclideanPattern(hits, pulses, shift, null));
                }
            }
        }
        System.out.println(json.append("\n}"));
    }
}
