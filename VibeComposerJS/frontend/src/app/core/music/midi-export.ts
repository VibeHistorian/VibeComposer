import { Midi } from '@tonejs/midi';
import type { CompositionProject, CompositionTrack } from '../project/project.model';
import { layOutTrackPhrase } from './phrase';

/** Serialize every independent instrument track with its assigned channel and mix controls. */
export function generateCompositionMidi(project: CompositionProject): Uint8Array {
  const midi = new Midi();
  midi.name = project.name;
  midi.header.setTempo(project.tempoBpm);
  const secondsPerBeat = 60 / project.tempoBpm;
  const anySolo = project.tracks.some((track) => track.mix.solo);

  for (const compositionTrack of project.tracks) {
    const track = midi.addTrack();
    track.name = compositionTrack.name;
    track.channel = compositionTrack.midiChannel - 1;
    const included = configureMix(track, compositionTrack, anySolo);
    if (!included) continue;
    for (const note of layOutTrackPhrase(project, compositionTrack)) {
      track.addNote({
        midi: note.midi,
        time: note.startBeat * secondsPerBeat,
        duration: note.durationBeats * secondsPerBeat,
        velocity: note.velocity / 127,
      });
    }
  }
  return midi.toArray();
}

function configureMix(track: ReturnType<Midi['addTrack']>, compositionTrack: CompositionTrack, anySolo: boolean): boolean {
  const { mix } = compositionTrack;
  if (compositionTrack.role !== 'drums') track.instrument.number = mix.program;
  track.addCC({ number: 7, time: 0, value: mix.volumePercent / 100 });
  track.addCC({ number: 10, time: 0, value: Math.round((mix.panPercent + 100) * 127 / 200) / 127 });
  return !mix.muted && (!anySolo || mix.solo);
}
