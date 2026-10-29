import { Midi } from '@tonejs/midi';
import { midiPanValue, mixEnabled } from '../audio/mix-values';
import type { CompositionProject } from '../project/project.model';
import type { CompositionTrack } from '../project/track.model';
import type { PhraseNote } from './phrase-note';
import { ARRANGED_PARTS, tracksInRoleOrder } from './part-role';
import { layOutTrackPhrase } from './phrase';
import { projectTempoMap } from './section-context';
import { mapDrumPitch, type DrumMapping } from './drum-instruments';

/** Export the current editor draft in phrase-relative beats, regardless of mute/solo state.
 * Prepend pickup time and preserve tails. Notes already contain their sounding pitches. */
export function generatePhraseMidi(name: string, tempoBpm: number,
  tracks: readonly { track: CompositionTrack; notes: readonly PhraseNote[] }[],
  _phraseBeats: number, drumMapping: DrumMapping = 'general-midi'): Uint8Array {
  const midi = new Midi();
  const offset = -tracks.reduce((start, { notes }) => notes.reduce((minimum, note) => Math.min(minimum, note.startBeat), start), 0);
  midi.name = name;
  midi.header.setTempo(tempoBpm);
  for (const { track: source, notes } of tracks) {
    const track = midi.addTrack();
    track.name = source.name;
    track.channel = source.midiChannel - 1;
    configureMix(track, source, false);
    for (const note of notes) {
      const start = note.startBeat + offset;
      const end = start + note.durationBeats;
      if (end <= start) continue;
      track.addNote({
        midi: source.role === 'drums' ? mapDrumPitch(note.midi, drumMapping) : note.midi,
        ticks: Math.round(start * midi.header.ppq),
        durationTicks: Math.max(1, Math.round(end * midi.header.ppq) - Math.round(start * midi.header.ppq)),
        velocity: note.velocity / 127,
      });
    }
  }
  return midi.toArray();
}

/** Serialize every independent instrument track with its assigned channel and mix controls. */
export function generateCompositionMidi(project: CompositionProject, drumMapping: DrumMapping = 'general-midi'): Uint8Array {
  const midi = new Midi();
  midi.name = project.name;
  const phrases = new Map(project.tracks.map(track => [track.id, layOutTrackPhrase(project, track)]));
  const offset = -[...phrases.values()].reduce((start, notes) => notes.reduce((minimum, note) => Math.min(minimum, note.startBeat), start), 0);
  midi.header.tempos = projectTempoMap(project).map(point => ({ ticks: (point.beat + offset) * midi.header.ppq, bpm: point.bpm }));
  if (offset > 0) midi.header.tempos.unshift({ ticks: 0, bpm: midi.header.tempos[0].bpm });
  midi.header.update();
  const anySolo = project.tracks.some((track) => track.mix.solo);

  for (const role of ARRANGED_PARTS) {
    const members = tracksInRoleOrder(project.tracks).filter(track => track.role === role);
    for (const compositionTrack of members) {
      const track = midi.addTrack();
      track.name = compositionTrack.name;
      track.channel = compositionTrack.midiChannel - 1;
      const included = configureMix(track, compositionTrack, anySolo);
      if (!included) continue;
      for (const note of phrases.get(compositionTrack.id)!) {
        track.addNote({
          midi: compositionTrack.role === 'drums' ? mapDrumPitch(note.midi, drumMapping) : note.midi,
          ticks: Math.round((note.startBeat + offset) * midi.header.ppq),
          durationTicks: Math.max(1, Math.round(note.durationBeats * midi.header.ppq)),
          velocity: note.velocity / 127,
        });
      }
    }
    for (let index = members.length; index < (project.midiPadding?.[role] ?? 0); index++) {
      const filler = midi.addTrack();
      filler.name = `${role} padding ${index + 1}`;
      filler.channel = role === 'drums' ? 9 : 0;
    }
  }
  return midi.toArray();
}

function configureMix(track: ReturnType<Midi['addTrack']>, compositionTrack: CompositionTrack, anySolo: boolean): boolean {
  const { mix } = compositionTrack;
  if (compositionTrack.role !== 'drums') track.instrument.number = mix.program;
  track.addCC({ number: 7, time: 0, value: Math.max(0, Math.min(1, mix.volumePercent / 100)) });
  track.addCC({ number: 10, time: 0, value: midiPanValue(mix.panPercent) / 127 });
  return mixEnabled(mix, anySolo);
}
