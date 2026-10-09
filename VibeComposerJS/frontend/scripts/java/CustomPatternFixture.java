import java.util.ArrayList;
import java.util.List;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Parts.ChordPart;

/** Calls the production InstPart custom-list rotation through ChordPart. */
public class CustomPatternFixture {
    public static void main(String[] args) {
        StringBuilder json = new StringBuilder("{\n");
        boolean first = true;
        for (int sample = 0; sample < 4; sample++) {
            List<Integer> grid = new ArrayList<>();
            for (int slot = 0; slot < 32; slot++) {
                grid.add(sample == 0 ? 0 : sample == 1 ? 1
                        : sample == 2 ? (slot % 5 == 0 ? 1 : 0)
                        : ((slot * 7 + slot / 3) % 11 < 4 ? 1 : 0));
            }
            ChordPart part = new ChordPart();
            part.setPattern(RhythmPattern.CUSTOM);
            part.setCustomPattern(grid);
            for (int hits = 1; hits <= 32; hits++) {
                part.setHitsPerPattern(hits);
                for (int shift = 0; shift <= 8; shift++) {
                    part.setPatternShift(shift);
                    if (!first) json.append(",\n");
                    first = false;
                    json.append("  \"").append(sample).append('/').append(hits).append('/')
                            .append(shift).append("\": ")
                            .append(part.getFinalPatternCopy().subList(0, hits));
                }
            }
        }
        System.out.println(json.append("\n}"));
    }
}
