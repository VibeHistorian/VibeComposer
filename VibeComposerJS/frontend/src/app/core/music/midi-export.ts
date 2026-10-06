import { Midi } from '@tonejs/midi';
import type { CompositionProject } from '../project/project.model';
import type { ArrangedPart } from '../project/project.model';
import { generateArpeggio } from './arpeggio-generator';
import { shouldGeneratePartInSection } from './arrangement-generator';
import { generateBassline } from './bass-generator';
import { generateChordPart } from './chord-generator';
import { generateDrumPart } from './drum-generator';

/** Serialize the current generated parts to a standard MIDI file byte array. */
export function generateCompositionMidi(project: CompositionProject): Uint8Array {
  const seed = BigInt(project.seed);
  const midi = new Midi();
  midi.name = project.name;
  midi.header.setTempo(project.tempoBpm);
  const secondsPerBeat = 60 / project.tempoBpm;

  const bassTrack = midi.addTrack();
  bassTrack.name = 'Bass';
  bassTrack.channel = 0;
  bassTrack.instrument.number = 33;
  const bassPhrase = generateBassline(
    seed, project.key, project.scale, project.progression,
    project.bass.rhythm, project.bass.noteVariation,
  );
  for (const note of layOutPhrase(project, 'bass', bassPhrase)) {
    bassTrack.addNote({
      midi: note.midi,
      time: note.startBeat * secondsPerBeat,
      duration: note.durationBeats * secondsPerBeat,
      velocity: note.velocity / 127,
    });
  }

  const chordTrack = midi.addTrack();
  chordTrack.name = 'Chords';
  chordTrack.channel = 1;
  chordTrack.instrument.number = 0;
  const chordPhrase = generateChordPart(
    seed, project.key, project.scale, project.progression, project.chords,
  );
  for (const hit of layOutPhrase(project, 'chords', chordPhrase)) {
    for (const pitch of hit.pitches) {
      chordTrack.addNote({
        midi: pitch,
        time: hit.startBeat * secondsPerBeat,
        duration: hit.durationBeats * secondsPerBeat,
        velocity: hit.velocity / 127,
      });
    }
  }

  const arpeggioTrack = midi.addTrack();
  arpeggioTrack.name = 'Arpeggio';
  arpeggioTrack.channel = 2;
  arpeggioTrack.instrument.number = 11;
  const arpeggioPhrase = generateArpeggio(
    seed, project.key, project.scale, project.progression, project.arpeggio,
  );
  for (const note of layOutPhrase(project, 'arpeggio', arpeggioPhrase)) {
    arpeggioTrack.addNote({
      midi: note.midi,
      time: note.startBeat * secondsPerBeat,
      duration: note.durationBeats * secondsPerBeat,
      velocity: note.velocity / 127,
    });
  }

  const drumTrack = midi.addTrack();
  drumTrack.name = 'Drums';
  drumTrack.channel = 9;
  const drumPhrase = generateDrumPart(seed, project.progression.length, project.drums);
  for (const hit of layOutPhrase(project, 'drums', drumPhrase)) {
    drumTrack.addNote({
      midi: hit.midi,
      time: hit.startBeat * secondsPerBeat,
      duration: hit.durationBeats * secondsPerBeat,
      velocity: hit.velocity / 127,
    });
  }

  return midi.toArray();
}

/** Repeat one generated progression phrase through the arrangement's sections and part entrances. */
function layOutPhrase<Event extends { readonly startBeat: number }>(
  project: CompositionProject,
  part: ArrangedPart,
  phrase: readonly Event[],
): Array<Event & { readonly startBeat: number }> {
  const result: Array<Event & { readonly startBeat: number }> = [];
  let arrangementBeat = 0;
  for (const section of project.arrangement) {
    const partEnters = shouldGeneratePartInSection(BigInt(project.seed), section, part);
    for (let measure = 0; measure < section.measures; measure++) {
      if (partEnters) {
        const sourceMeasure = measure % project.progression.length;
        const sourceStartBeat = sourceMeasure * 4;
        const sourceEndBeat = sourceStartBeat + 4;
        for (const event of phrase) {
          if (event.startBeat >= sourceStartBeat && event.startBeat < sourceEndBeat) {
            result.push({ ...event, startBeat: arrangementBeat + event.startBeat - sourceStartBeat });
          }
        }
      }
      arrangementBeat += 4;
    }
  }
  return result;
}
