package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import org.junit.Test;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Section;
import javax.sound.midi.*;
import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Opt-in repeatable listening/profiling export: mvn -Dtest=GenerationCorpus test. */
public class GenerationCorpus {
    private static final class Sound {
        final int pitch, channel, velocity; final double start, duration;
        Sound(int pitch,int channel,int velocity,double start,double duration) {
            this.pitch = pitch; this.channel = channel; this.velocity = velocity; this.start = start; this.duration = duration;
        }
    }
    @Test public void export() throws Exception {
        Path directory = Paths.get("target","overhaul-corpus"); Files.createDirectories(directory);
        String[] styles = {"functional-major","functional-minor","modal-vamp","pop-loop"};
        String[][] chords = {{"C","Dm7","G7","C"},{"Cm","Dm7b5","G7","Cm"},{"Cm7","Fm7","Cm7","Bb"},{"C","G","Am","F"}};
        MidiUtils.ScaleMode[] modes = {MidiUtils.ScaleMode.IONIAN,MidiUtils.ScaleMode.AEOLIAN,MidiUtils.ScaleMode.DORIAN,MidiUtils.ScaleMode.IONIAN};
        List<int[]> jobs = new ArrayList<>();
        for (int style = 0; style < styles.length; style++) { for (int seed : new int[] {17,101}) { for (int engine = 0; engine < 2; engine++) { jobs.add(new int[] {style,seed,engine}); } } }
        Collections.shuffle(jobs,new Random(499));
        List<String> manifest = new ArrayList<>(); manifest.add("sample,style,seed,engine,planning_ms,melody_notes,max_leap");
        int sample = 0;
        for (int[] job : jobs) {
            String name = String.format("sample-%02d",++sample); int style = job[0], seed = job[1];
            GUIConfig config = new GUIConfig(); config.setScaleMode(modes[style]); config.setMelodyEnable(true);
            config.setMelodyBlockTypePreference(java.util.Arrays.stream(org.vibehistorian.vibecomposer.Enums.BlockType.values())
                    .map(type -> type.defaultChance).collect(java.util.stream.Collectors.toList()));
            config.setMelodyStyle(style == 2 ? ContextualMelodyPlanner.Style.MODAL : ContextualMelodyPlanner.Style.TONAL);
            config.setMelodyForm(MelodyPhrasePlan.Form.QUESTION_ANSWER); config.setMelodyEnding(style == 3 ? ContextualMelodyPlanner.Ending.LOOP : ContextualMelodyPlanner.Ending.CLOSED);
            MelodyPart part = new MelodyPart(); part.setOrder(1); part.setPatternSeed(seed); part.setSpeed(50);
            part.setOrderOffset(1);
            part.setPauseChance(10); part.setStartNoteChance(100); part.setMaxNoteExceptions(0); part.setNoteExceptionChance(0);
            part.setVelocityMin(89); part.setVelocityMax(91); part.setPatternFlexible(true);
            config.setMelodyParts(Collections.singletonList(part)); Section section = new Section(); section.setMeasures(1);
            List<int[]> raw = new ArrayList<>(); for (String text : chords[style]) { raw.add(ChordSymbol.parse(text).pitches(false)); }
            List<Double> durations = Arrays.asList(4.0,4.0,4.0,4.0);
            HarmonyTimeline harmony = HarmonyTimeline.fromSymbols(Arrays.asList(chords[style]),durations,new HarmonyTimeline.Context("C",0,modes[style],style != 2));
            Map<Integer,List<Note>> melody; ContextualMelodyPlanner.Result trace = null;
            long before = System.nanoTime();
            if (job[2] == 1) {
                config.setContextualMelody(true); ContextualMelodyGenerator generator = new ContextualMelodyGenerator(config,new MidiTiming(100),0.95);
                melody = generator.generate(part,raw,raw,durations,section,0,modes[style],true); trace = generator.lastResult;
            } else {
                MidiGenerator midi = new MidiGenerator(config); midi.setProgressionDurations(durations);
                melody = new MelodyGenerator(config,midi).makeFullMelodyMap(part,raw,raw,durations,0,section,new ArrayList<>(),Collections.emptyList());
                if (modes[style] != MidiUtils.ScaleMode.IONIAN) {
                    for (List<Note> notes : melody.values()) { MidiUtils.transposeNotes(notes,MidiUtils.ScaleMode.IONIAN.noteAdjustScale,modes[style].noteAdjustScale,false); }
                }
            }
            double elapsed = (System.nanoTime() - before) / 1000000.0;
            List<Sound> sounds = new ArrayList<>(); List<String> roles = new ArrayList<>(); roles.add("beat,duration,midi,role");
            double cursor = 0; int count = 0, leap = 0, previous = -1;
            for (int event = 0; event < raw.size(); event++) {
                for (Note note : melody.get(event)) {
                    if (note.getPitch() >= 0) {
                        sounds.add(new Sound(note.getPitch(),0,90,Math.max(0,cursor + note.getOffset()),note.getDuration()));
                        if (previous >= 0) { leap = Math.max(leap,Math.abs(note.getPitch() - previous)); } previous = note.getPitch();
                    }
                    roles.add(String.format(java.util.Locale.ROOT,"%.6f,%.6f,%d,%s",cursor,note.getRhythmValue(),note.getPitch(),
                            trace == null ? "compatibility" : trace.notes.get(count).role));
                    cursor += note.getRhythmValue(); count++;
                }
            }
            List<int[]> voices = HarmonyVoicingPlanner.voice(harmony,48,72,Collections.emptyMap(),Collections.emptyMap());
            for (HarmonyTimeline.Event event : harmony.events()) {
                for (int pitch : voices.get(event.index)) { sounds.add(new Sound(pitch,1,65,event.start / 960.0,(event.end - event.start) / 960.0 * 0.9)); }
            }
            midi(directory.resolve(name + ".mid"),sounds);
            wave(directory.resolve(name + ".wav"),sounds,harmony.length() / 960.0);
            Files.write(directory.resolve(name + ".csv"),roles,StandardCharsets.UTF_8);
            manifest.add(String.format(java.util.Locale.ROOT,"%s,%s,%d,%s,%.3f,%d,%d",name,styles[style],seed,
                    trace == null ? "compatibility" : trace.algorithm,elapsed,count,leap));
        }
        Files.write(directory.resolve("manifest.csv"),manifest,StandardCharsets.UTF_8);
        Files.write(directory.resolve("README.md"),Arrays.asList("# Listening corpus","",
                "16 shuffled samples: four harmonic contexts, two seeds, two engines. Listen to WAV files before opening manifest.csv.",
                "Both engines receive the same literal chord arrays. Compatibility retains its existing modal processing. Harmony, tempo (100 BPM), MIDI instruments and tone-render levels are shared.",
                "WAV files use a controlled additive reference tone, not the application's soundfont. MIDI files use flute plus piano and can be auditioned with the same soundfont.",
                "Compare phrase identity, arrivals, tension/resolution, contour, rhythm, and melody/accompaniment fit. No preference result is inferred from metrics.",
                "manifest.csv records planning time, note count, and largest realized leap; per-sample CSV contains rhythm/pitch/role traces.",
                "Regenerate with: mvn -Dtest=GenerationCorpus test. Files are overwritten reproducibly; timings depend on the machine."),StandardCharsets.UTF_8);
    }
    @Test public void profileLongUnevenAndOddMeterContexts() throws Exception {
        Path directory = Paths.get("target","overhaul-corpus"); Files.createDirectories(directory);
        List<String> measurements = new ArrayList<>(); measurements.add("context,chords,beats,meter,range,planning_ms,notes,candidates");
        String[][] patterns = {{"C","Dm7","G7","C"},{"Cm","Dm7b5","G7","Cm"},{"Cm7","Fm7","Cm7","Bb"},{"C","G","Am","F"}};
        MidiUtils.ScaleMode[] modes = {MidiUtils.ScaleMode.IONIAN,MidiUtils.ScaleMode.AEOLIAN,MidiUtils.ScaleMode.DORIAN,MidiUtils.ScaleMode.IONIAN};
        for (int context = 0; context < patterns.length; context++) {
            for (int count : new int[] {4,16,32}) {
                GUIConfig config = new GUIConfig(); config.setScaleMode(modes[context]); config.setContextualMelody(true);
                config.setMelodyBeatsPerBar(3); config.setMelodyRangeMin(count == 4 ? 60 : 48); config.setMelodyRangeMax(count == 4 ? 72 : 96);
                config.setMelodyStyle(context == 2 ? ContextualMelodyPlanner.Style.MODAL : ContextualMelodyPlanner.Style.TONAL);
                MelodyPart part = new MelodyPart(); part.setOrderOffset(1); part.setPatternSeed(101); part.setSpeed(80); part.setStartNoteChance(100);
                Section section = new Section(); section.setMeasures(1);
                List<String> names = new ArrayList<>(); List<Double> durations = new ArrayList<>(); List<int[]> chords = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    String name = patterns[context][i % 4]; names.add(name); chords.add(ChordSymbol.parse(name).pitches(false));
                    durations.add(new double[] {0.75,2.25,3,5}[i % 4]);
                }
                ContextualMelodyGenerator generator = new ContextualMelodyGenerator(config,new MidiTiming(100),0.95);
                long before = System.nanoTime(); generator.generate(part,chords,chords,durations,section,0,modes[context],true);
                double elapsed = (System.nanoTime() - before) / 1000000.0;
                ContextualMelodyPlanner.Result result = generator.lastResult;
                org.junit.Assert.assertTrue(result.candidatesExamined <= (long) result.notes.size() * 32 * 49);
                for (ContextualMelodyPlanner.PlannedNote note : result.notes) {
                    if (note.pitch >= 0) { org.junit.Assert.assertTrue(note.pitch >= config.getMelodyRangeMin() && note.pitch <= config.getMelodyRangeMax()); }
                }
                measurements.add(String.format(Locale.ROOT,"%d,%d,%.2f,3,%d-%d,%.3f,%d,%d",context,count,
                        durations.stream().mapToDouble(Double::doubleValue).sum(),config.getMelodyRangeMin(),config.getMelodyRangeMax(),elapsed,
                        result.notes.size(),result.candidatesExamined));
            }
        }
        Files.write(directory.resolve("profile.csv"),measurements,StandardCharsets.UTF_8);
    }

    private static void midi(Path path,List<Sound> sounds) throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ,960); Track melody = sequence.createTrack(), harmony = sequence.createTrack();
        MetaMessage tempo = new MetaMessage(); tempo.setMessage(0x51,new byte[] {9,39,(byte)192},3); melody.add(new MidiEvent(tempo,0));
        melody.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE,0,73,0),0));
        harmony.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE,1,0,0),0));
        for (Sound sound : sounds) {
            Track track = sound.channel == 0 ? melody : harmony;
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON,sound.channel,sound.pitch,sound.velocity),Math.round(sound.start * 960)));
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF,sound.channel,sound.pitch,0),Math.round((sound.start + sound.duration) * 960)));
        }
        MidiSystem.write(sequence,1,path.toFile()); MidiSystem.getSequence(path.toFile());
    }
    private static void wave(Path path,List<Sound> sounds,double beats) throws Exception {
        int rate = 22050, frames = (int) Math.ceil((beats * 0.6 + 0.5) * rate); double[] mix = new double[frames];
        for (Sound sound : sounds) {
            int from = (int) (sound.start * 0.6 * rate), length = (int) (sound.duration * 0.6 * rate);
            double frequency = 440 * Math.pow(2,(sound.pitch - 69) / 12.0), gain = sound.channel == 0 ? 0.18 : 0.035;
            for (int i = 0; i < length && from + i < frames; i++) {
                double time = i / (double) rate, envelope = Math.min(1,i / (rate * 0.008)) * Math.min(1,(length - i) / (rate * 0.025));
                mix[from + i] += gain * sound.velocity / 100.0 * envelope * (Math.sin(2 * Math.PI * frequency * time) + 0.2 * Math.sin(4 * Math.PI * frequency * time));
            }
        }
        byte[] pcm = new byte[frames * 2];
        for (int i = 0; i < frames; i++) { int value = (int) Math.round(Math.max(-1,Math.min(1,mix[i])) * 32767); pcm[2 * i] = (byte) value; pcm[2 * i + 1] = (byte) (value >> 8); }
        try (AudioInputStream audio = new AudioInputStream(new ByteArrayInputStream(pcm),new AudioFormat(rate,16,1,true,false),frames)) {
            AudioSystem.write(audio,AudioFileFormat.Type.WAVE,path.toFile());
        }
    }
}
