package org.vibehistorian.vibecomposer;

import org.junit.Test;
import org.vibehistorian.vibecomposer.generation.MidiGenerator;

import javax.xml.bind.JAXBContext;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Regenerates the reference MIDI fixture using the same config deserialization and
 * generator entry point used by the application, then compares the MIDI bytes.
 * Run from this module with: mvn -Dtest=GeneratorRegressionTest test
 */
public class GeneratorRegressionTest {
    private static final String CONFIG_FILE = "GENERATOR_REFACTOR_TEST_GUICONFIG.xml";
    private static final String OUTPUT_FILE = "GENERATOR_REFACTOR_TEST_OUTPUT.mid";
    private static final String EXPECTED_FILE = "GENERATOR_REFACTOR_TEST_OUTPUT2.mid";

    @Test
    public void configGeneratesReferenceMidi() throws Exception {
        Path projectDir = findProjectDirectory();
        Path configPath = projectDir.resolve(CONFIG_FILE);
        Path outputPath = projectDir.resolve(OUTPUT_FILE);
        Path expectedPath = projectDir.resolve(EXPECTED_FILE);
        Path temporarySequencePath = projectDir.resolve(Constants.TEMPORARY_SEQUENCE_MIDI_NAME);

        byte[] previousTemporarySequence = Files.exists(temporarySequencePath)
                ? Files.readAllBytes(temporarySequencePath) : null;
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        try {
            GUIConfig config = loadConfig(configPath.toFile());
            MidiGenerator.configureFromConfig(config);

            int seed = (int) config.getRandomSeed();
            new MidiGenerator(config).generateMasterpiece(seed, outputPath.toString());

            assertMidiMatches(expectedPath, outputPath);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            if (previousTemporarySequence == null) {
                Files.deleteIfExists(temporarySequencePath);
            } else {
                Files.write(temporarySequencePath, previousTemporarySequence);
            }
        }
    }

    private static GUIConfig loadConfig(File configFile) throws Exception {
        JAXBContext context = JAXBContext.newInstance(GUIConfig.class);
        try (FileReader reader = new FileReader(configFile)) {
            return (GUIConfig) context.createUnmarshaller().unmarshal(reader);
        }
    }

    private static void assertMidiMatches(Path expectedPath, Path actualPath) throws Exception {
        byte[] expected = Files.readAllBytes(expectedPath);
        byte[] actual = Files.readAllBytes(actualPath);
        int commonLength = Math.min(expected.length, actual.length);
        for (int i = 0; i < commonLength; i++) {
            if (expected[i] != actual[i]) {
                fail("Generated MIDI differs at byte offset " + i + " (expected length "
                        + expected.length + ", actual length " + actual.length + ")");
            }
        }
        assertEquals("Generated MIDI length differs", expected.length, actual.length);
    }

    private static Path findProjectDirectory() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        if (Files.exists(current.resolve(CONFIG_FILE))) {
            return current;
        }
        Path moduleDirectory = current.resolve("midimasterpiece");
        if (Files.exists(moduleDirectory.resolve(CONFIG_FILE))) {
            return moduleDirectory;
        }
        throw new IllegalStateException("Could not locate " + CONFIG_FILE + " from " + current);
    }
}
