package org.vibehistorian.vibecomposer.generation;

import java.util.*;
public class MelodyTargetFixture {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("modes")) { targetModes(); return; }
        boolean extended = args.length > 0 && args[0].equals("extended");
        org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.OFF);
        org.apache.logging.log4j.core.config.Configurator.setLevel("org.vibehistorian.vibecomposer.LG", org.apache.logging.log4j.Level.OFF);
        String[] symbols = {"C", "Dm", "Em", "F", "G", "Am", "Bdim"};
        int[][] progressions = {{1,5,6,4}, {1}, {7,3,2,6,5}, {4,6,2,5,1,3,7,1}};
        StringJoiner json = new StringJoiner(",\n", "[\n", "\n]");
        for (int index = 0; index < 32; index++) {
            int seed = index == 0 ? Integer.MIN_VALUE : index == 1 ? Integer.MAX_VALUE : index * 13240927;
            for (int[] progression : progressions) {
              for (int variation = extended ? 1 : 3; variation <= (extended ? 6 : 3); variation++) {
               for (MelodyUtils.NoteTargetDirection direction : extended ? MelodyUtils.NoteTargetDirection.values() : new MelodyUtils.NoteTargetDirection[] {MelodyUtils.NoteTargetDirection.ASC}) {
                for (int progressionDirections = 0; progressionDirections < (extended ? 2 : 1); progressionDirections++) {
                List<String> chords = new ArrayList<>();
                for (int degree : progression) chords.add(symbols[degree - 1]);
                List<Integer> targets = MelodyTargetUtils.generateNoteTargetOffsets(chords, seed, 2, variation, null, direction, progressionDirections == 1);
                json.add("{\"seed\":" + seed + ",\"progression\":" + Arrays.toString(progression)
                    + ",\"globalSettings\":{\"targetNoteVariation\":" + variation + ",\"targetDirection\":\"" + direction.name()
                    + "\",\"useDirectionsFromProgression\":" + (progressionDirections == 1) + "},\"targets\":" + targets + "}");
                }
               }
              }
            }
        }
        System.out.println(json);
    }

    private static void targetModes() {
        org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.OFF);
        String[] symbols = {"C", "Dm", "Em", "F", "G", "Am", "Bdim"};
        int[][] progressions = {{1,5,6,4}, {1}, {7,3,2,6,5}, {4,6,2,5,1,3,7,1}};
        String[] keys = {"C", "D", "A\\u266d", "F\\u266f"}; int[] tonics = {0,2,8,6};
        StringJoiner json = new StringJoiner(",\n", "[\n", "\n]");
        for (int sample = 0; sample < 16; sample++) {
          int seed = sample == 0 ? Integer.MIN_VALUE : sample == 1 ? Integer.MAX_VALUE : sample * 13240927;
          for (int[] progression : progressions) for (int harmony = 0; harmony < 3; harmony++) {
            boolean minor = harmony == 2 || harmony == 0 && sample % 2 == 1;
            List<int[]> roots = new ArrayList<>();
            StringJoiner custom = new StringJoiner(",", "[", "]");
            for (int index = 0; index < progression.length; index++) {
              if (harmony == 0) { roots.add(org.vibehistorian.vibecomposer.MidiUtils.getNormalMappedChord(symbols[progression[index]-1], false)); custom.add("null"); }
              else {
                int root = index == 0 ? 6 : (sample + index * 5) % 12;
                String spice = index == 0 ? "sus2" : org.vibehistorian.vibecomposer.MidiUtils.SPICE_NAMES_LIST.get((sample + index * 3) % 17);
                int[] voicing = org.vibehistorian.vibecomposer.MidiUtils.getNormalMappedChord(org.vibehistorian.vibecomposer.MidiUtils.SEMITONE_LETTERS.get(root) + spice, true);
                if (minor) voicing = org.vibehistorian.vibecomposer.MidiUtils.transposeChord(voicing,
                    org.vibehistorian.vibecomposer.MidiUtils.ScaleMode.AEOLIAN.noteAdjustScale,
                    org.vibehistorian.vibecomposer.MidiUtils.ScaleMode.IONIAN.noteAdjustScale, true);
                roots.add(voicing);
                custom.add("{\"root\":" + ((root + tonics[sample % 4]) % 12) + ",\"spice\":\"" + spice + "\",\"inversion\":" + ((sample + index) % 11 - 5) + "}");
              }
            }
            for (int mode = 0; mode < 3; mode++) for (int variation = 1; variation <= 6; variation++)
              for (MelodyUtils.NoteTargetDirection direction : MelodyUtils.NoteTargetDirection.values()) for (int fromProgression = 0; fromProgression < 2; fromProgression++) {
                List<Integer> targets = MelodyTargetUtils.generateNoteTargetOffsets(roots, seed, mode, variation, direction, fromProgression == 1);
                StringJoiner starts = new StringJoiner(",", "[", "]");
                for (int index = 0; index <= roots.size(); index++) {
                  try { starts.add(String.valueOf(MelodyBlockUtils.getStartingNote(roots, targets, index, mode))); }
                  catch (NullPointerException failure) { starts.add("null"); }
                }
                json.add("{\"seed\":" + seed + ",\"progression\":" + Arrays.toString(progression)
                  + ",\"key\":\"" + keys[sample % 4] + "\",\"scale\":\"" + (minor ? "natural-minor" : "major")
                  + "\",\"customChords\":" + custom + ",\"globalSettings\":{\"blockTargetMode\":" + mode
                  + ",\"targetNoteVariation\":" + variation + ",\"targetDirection\":\"" + direction.name()
                  + "\",\"useDirectionsFromProgression\":" + (fromProgression == 1) + "},\"targets\":" + targets + ",\"starting\":" + starts + "}");
              }
          }
        }
        System.out.println(json);
    }
}
