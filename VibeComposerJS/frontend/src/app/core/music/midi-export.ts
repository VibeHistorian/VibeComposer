import { Midi } from '@tonejs/midi';
import type { CompositionProject } from '../project/project.model';
import type { CompositionTrack } from '../project/track.model';
import type { PhraseNote } from './phrase-note';
import { tracksInRoleOrder } from './part-role';
import { layOutTrackPhrase } from './phrase';
import { projectTempoMap } from './section-context';
import { mapDrumPitch, type DrumMapping } from './drum-instruments';

/** Export the current editor draft in phrase-relative beats, regardless of mute/solo state.
 * Notes already contain their sounding pitches; do not transpose them a second time. */
export function generatePhraseMidi(name: string, tempoBpm: number,
  tracks: readonly { track: CompositionTrack; notes: readonly PhraseNote[] }[],
  phraseBeats: number, drumMapping: DrumMapping = 'general-midi'): Uint8Array {
  const midi = new Midi();
  midi.name = name;
  midi.header.setTempo(tempoBpm);
  for (const { track: source, notes } of tracks) {
    const track = midi.addTrack();
    track.name = source.name;
    track.channel = source.midiChannel - 1;
    configureMix(track, source, false);
    for (const note of notes) {
      const start = Math.max(0, note.startBeat);
      const end = Math.min(phraseBeats, note.startBeat + note.durationBeats);
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
  midi.header.tempos = projectTempoMap(project).map(point => ({ ticks: point.beat * midi.header.ppq, bpm: point.bpm }));
  midi.header.update();
  const anySolo = project.tracks.some((track) => track.mix.solo);

  for (const compositionTrack of tracksInRoleOrder(project.tracks)) {
    const track = midi.addTrack();
    track.name = compositionTrack.name;
    track.channel = compositionTrack.midiChannel - 1;
    const included = configureMix(track, compositionTrack, anySolo);
    if (!included) continue;
    for (const note of layOutTrackPhrase(project, compositionTrack)) {
      track.addNote({
        midi: compositionTrack.role === 'drums' ? mapDrumPitch(note.midi, drumMapping) : note.midi,
        ticks: Math.round(note.startBeat * midi.header.ppq),
        durationTicks: Math.max(1, Math.round(note.durationBeats * midi.header.ppq)),
        velocity: note.velocity / 127,
      });
    }
  }
  return midi.toArray();
}

function configureMix(track: ReturnType<Midi['addTrack']>, compositionTrack: CompositionTrack, anySolo: boolean): boolean {
  const { mix } = compositionTrack;
  if (compositionTrack.role !== 'drums') track.instrument.number = mix.program;
  track.addCC({ number: 7, time: 0, value: Math.max(0, Math.min(1, mix.volumePercent / 100)) });
  track.addCC({ number: 10, time: 0, value: Math.round((mix.panPercent + 100) * 127 / 200) / 127 });
  return !mix.muted && (!anySolo || mix.solo);
}
