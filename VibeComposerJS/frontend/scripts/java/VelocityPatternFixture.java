import java.util.ArrayList;
import java.util.List;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.generation.MidiGeneratorUtils;

/** Exercises stored velocities and the production scaling helper with no arrangement volume scaling. */
public class VelocityPatternFixture {
    public static void main(String[] args) {
        StringBuilder json = new StringBuilder("{\n");
        boolean first = true;
        for (int sample = 0; sample < 4; sample++) {
            List<Integer> grid = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                grid.add(sample == 0 ? 0 : sample == 1 ? 127
                        : sample == 2 ? index * 7 % 128 : index % 2 == 0 ? 1 : 79);
            }
            ChordPart part = new ChordPart();
            part.setCustomVelocities(grid);
            for (int hits : new int[] {1, 3, 5, 8, 16, 31, 32}) {
                part.setHitsPerPattern(hits);
                for (int shift : new int[] {0, 1, 8}) {
                    part.setPatternShift(shift);
                    List<Integer> velocities = new ArrayList<>();
                    // Selection order from ChordPhraseGenerator's chordVelocityPattern loop.
                    for (int index = 0; index < part.getHitsPerPattern(); index++) {
                        velocities.add(MidiGeneratorUtils.multiplyVelocity(part.getCustomVelocities().get(index), 100, 0, 1));
                    }
                    if (!first) json.append(",\n");
                    first = false;
                    json.append("  \"").append(sample).append('/').append(hits).append('/')
                            .append(shift).append("\": ").append(velocities);
                }
            }
        }
        System.out.println(json.append("\n}"));
    }
}
