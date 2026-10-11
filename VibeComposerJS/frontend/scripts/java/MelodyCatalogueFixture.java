package org.vibehistorian.vibecomposer.generation;

import java.util.*;
import org.apache.commons.lang3.tuple.Pair;

/** Calls production catalogue selection, including the subsequent random draw. */
public class MelodyCatalogueFixture {
    public static void main(String[] args) {
        org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.OFF);
        org.apache.logging.log4j.core.config.Configurator.setLevel("org.vibehistorian.vibecomposer.LG", org.apache.logging.log4j.Level.OFF);
        String[] seeds = {"42", "-2147483648", "2147483647", "9223372036854775807"};
        int[][] weights = {{100,100,100,100,100,100,100}, {0,0,0,0,0,0,0},
                {100,0,0,0,0,0,0}, {0,0,100,60,20,100,100}, {0,0,0,0,0,100,100}};
        List<List<Integer>> preferences = Arrays.asList(Collections.emptyList(),
                Arrays.asList(0,7,4,3,1,2,5,6), Arrays.asList(7,6,5,4,3,2,1,0));
        StringJoiner json = new StringJoiner(",\n", "[\n", "\n]");
        for (String seed : seeds) for (int change = -7; change <= 7; change++) {
            for (int jump : new int[] {0,1,4}) for (Integer length : new Integer[] {null,1,2,3,4}) {
                for (int config = 0; config < weights.length; config++) {
                    int variance = config % 2 == 0 ? 0 : 4, directions = config % 3;
                    List<Integer> preference = preferences.get(config % preferences.size());
                    List<Integer> typeWeights = new ArrayList<>();
                    for (int weight : weights[config]) typeWeights.add(weight);
                    Random random = new Random(Long.parseLong(seed));
                    Pair<Integer, Integer[]> result = MelodyBlockUtils.getRandomByApproxBlockChangeAndLength(
                            change, jump, random, length, variance, directions, preference, typeWeights);
                    json.add("{\"seed\":\"" + seed + "\",\"change\":" + change + ",\"jump\":" + jump
                            + ",\"length\":" + length + ",\"variance\":" + variance + ",\"directions\":" + directions
                            + ",\"preference\":" + preference + ",\"weights\":" + typeWeights
                            + ",\"type\":" + result.getLeft() + ",\"notes\":" + Arrays.toString(result.getRight())
                            + ",\"nextInt\":" + random.nextInt() + "}");
                }
            }
        }
        System.out.println(json);
    }
}
