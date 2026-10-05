import { Midi } from '@tonejs/midi';
import type { CompositionProject } from '../project/project.model';
import { generateArpeggio } from './arpeggio-generator';
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
  for (const note of generateBassline(
    seed, project.key, project.scale, project.progression,
    project.bass.rhythm, project.bass.noteVariation,
  )) {
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
  for (const hit of generateChordPart(
    seed, project.key, project.scale, project.progression, project.chords,
  )) {
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
  for (const note of generateArpeggio(
    seed, project.key, project.scale, project.progression, project.arpeggio,
  )) {
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
  for (const hit of generateDrumPart(seed, project.progression.length, project.drums)) {
    drumTrack.addNote({
      midi: hit.midi,
      time: hit.startBeat * secondsPerBeat,
      duration: hit.durationBeats * secondsPerBeat,
      velocity: hit.velocity / 127,
    });
  }

  return midi.toArray();
}
