package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.generation.MidiGenerator;

import javax.xml.bind.JAXBContext;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Run an authored Java configuration through the complete production MIDI pipeline. */
public final class SavedConfigParityFixture {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected configuration XML and output MIDI paths");
        }
        GUIConfig config;
        try (FileReader reader = new FileReader(args[0])) {
            config = (GUIConfig) JAXBContext.newInstance(GUIConfig.class).createUnmarshaller().unmarshal(reader);
        }
        Path temporarySequence = Paths.get(Constants.TEMPORARY_SEQUENCE_MIDI_NAME);
        byte[] previousSequence = Files.exists(temporarySequence) ? Files.readAllBytes(temporarySequence) : null;
        try {
            new MidiGenerator(config).generateMasterpiece((int) config.getRandomSeed(), args[1]);
        } finally {
            if (previousSequence == null) Files.deleteIfExists(temporarySequence);
            else Files.write(temporarySequence, previousSequence);
        }
    }
}
