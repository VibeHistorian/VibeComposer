package org.vibehistorian.vibecomposer.Parts.Defaults;

import java.util.Random;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.generation.MelodyUtils;

/** Production melody helper/blueprint data and audited BassGUI int-seeded creation branches. */
public class RolePolicyFixture {
    public static void main(String[] args) {
        StringBuilder json = new StringBuilder("{\"melodyPatterns\":[");
        for (int seed = -32; seed < 32; seed++) {
            if (seed > -32) json.append(',');
            json.append("{\"seed\":").append(seed).append(",\"pattern\":")
                .append(MelodyUtils.getRandomMelodyPattern(0, seed)).append('}');
        }
        json.append("],\"drums\":[");
        for (int family = 0; family < 5; family++) {
            if (family > 0) json.append(',');
            DrumSettings settings = DrumDefaults.drumSettings[family];
            json.append('[');
            for (int index = 0; index < settings.getPatterns().length; index++) {
                if (index > 0) json.append(',');
                json.append("[\"").append(settings.getPatterns()[index]).append("\",")
                    .append(settings.getHits()[index]).append(',').append(settings.getChords()[index])
                    .append(',').append(settings.getShift()[index]).append(']');
            }
            json.append(']');
        }
        json.append("],\"customDrums\":").append(DrumSettings.COOL_16_PATTERNS);
        json.append(",\"bass\":["); boolean first = true;
        ChordSpanFill[] fills = { ChordSpanFill.ALL, ChordSpanFill.ALL, ChordSpanFill.EVEN,
            ChordSpanFill.ODD, ChordSpanFill.HALF1, ChordSpanFill.HALF2 };
        for (String seedText : new String[] { "42", "-2147483648", "9007199254740993", "9223372036854775807" }) {
            for (int order : new int[] {1, 2, 3}) for (int span : new int[] {1, 2, 4}) {
                int seed = (int) Long.parseLong(seedText);
                Random random = new Random(seed);
                if (!first) json.append(','); first = false;
                json.append("{\"seed\":\"").append(seedText).append("\",\"order\":").append(order)
                    .append(",\"span\":").append(span).append(",\"values\":{");
                int max, min, length, transpose;
                if (order > 1) {
                    ChordSpanFill fill = fills[random.nextInt(fills.length)];
                    random.nextInt(40); // Pause is baked by the legacy GUI, not a supported TS Bass field.
                    max = 50 + random.nextInt(20); min = 30 + random.nextInt(20);
                    transpose = order % 2 == 0 ? 0 : 12; length = 60 + random.nextInt(40);
                    RhythmPattern pattern = RhythmPattern.SINGLE;
                    if (random.nextInt(100) < 50) {
                        pattern = RhythmPattern.VIABLE_PATTERNS.get(random.nextInt(RhythmPattern.VIABLE_PATTERNS.size()));
                        if (pattern == RhythmPattern.MELODY1) pattern = RhythmPattern.FULL;
                    }
                    int hits = 4;
                    while (random.nextBoolean() && hits < 16) hits *= 2;
                    if (hits / span >= 8) hits /= 2;
                    json.append("\"chordSpanFill\":\"").append(fill).append("\",\"patternSeed\":").append(seed)
                        .append(",\"javaRhythm\":\"").append(pattern).append("\",\"hitsPerPattern\":").append(hits * 2).append(',');
                } else {
                    random.nextInt(10); transpose = 0;
                    max = 60 + random.nextInt(30); min = 40 + random.nextInt(25); length = 80 + random.nextInt(25);
                }
                json.append("\"velocityMax\":").append(max).append(",\"velocityMin\":").append(min)
                    .append(",\"noteLengthMultiplier\":").append(length).append(",\"transpose\":").append(transpose)
                    .append(",\"fillFlip\":false}}");
            }
        }
        System.out.println(json.append("]}"));
    }
}
