import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.OMNI;
import java.util.Random;

/** Production fill weights and the audited ArpGUI hit-choice branch with an injected seed. */
public class ArpeggioPolicyFixture {
    public static void main(String[] args) {
        StringBuilder json = new StringBuilder("{\"fills\":[");
        for (int weight = 0; weight < 100; weight++) {
            if (weight > 0) json.append(',');
            json.append('"').append(ChordSpanFill.getWeighted(weight)).append('"');
        }
        json.append("],\"hits\":["); boolean first = true;
        for (String seed : new String[] { "42", "-2147483648", "9223372036854775807", "9007199254740993" }) {
            for (boolean powers : new boolean[] { false, true }) {
                Random random = new Random(Long.parseLong(seed));
                if (!first) json.append(','); first = false;
                json.append("{\"seed\":\"").append(seed).append("\",\"powerOfTwo\":").append(powers).append(",\"hits\":[");
                for (int index = 0; index < 32; index++) {
                    int hits;
                    if (powers) hits = OMNI.getRandomFromArray(random, new int[] { 2, 4, 4, 8, 8, 8, 8 }, 0);
                    else {
                        hits = random.nextInt(7) + 2;
                        if (hits == 5) hits = random.nextInt(7) + 2;
                        if (hits == 7) hits++;
                    }
                    if (index > 0) json.append(','); json.append(hits);
                }
                json.append("]}");
            }
        }
        System.out.println(json.append("]}"));
    }
}
