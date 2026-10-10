package org.vibehistorian.vibecomposer.generation;

import java.util.*;
public class MelodyTargetFixture {
    public static void main(String[] args) {
        org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.OFF);
        org.apache.logging.log4j.core.config.Configurator.setLevel("org.vibehistorian.vibecomposer.LG", org.apache.logging.log4j.Level.OFF);
        String[] symbols = {"C", "Dm", "Em", "F", "G", "Am", "Bdim"};
        int[][] progressions = {{1,5,6,4}, {1}, {7,3,2,6,5}, {4,6,2,5,1,3,7,1}};
        StringJoiner json = new StringJoiner(",\n", "[\n", "\n]");
        for (int index = 0; index < 32; index++) {
            int seed = index == 0 ? Integer.MIN_VALUE : index == 1 ? Integer.MAX_VALUE : index * 13240927;
            for (int[] progression : progressions) {
                List<String> chords = new ArrayList<>();
                for (int degree : progression) chords.add(symbols[degree - 1]);
                List<Integer> targets = MelodyTargetUtils.generateNoteTargetOffsets(chords, seed, 2, 3, null, MelodyUtils.NoteTargetDirection.ASC, false);
                json.add("{\"seed\":" + seed + ",\"progression\":" + Arrays.toString(progression) + ",\"targets\":" + targets + "}");
            }
        }
        System.out.println(json);
    }
}
