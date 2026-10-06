import { Midi } from '@tonejs/midi';
import type { CompositionProject } from '../project/project.model';
import type { ArrangedPart, MixChannelSettings } from '../project/project.model';
import { ARRANGED_PARTS } from '../project/project.model';
import { shouldGeneratePartInSection } from './arrangement-generator';
import { phraseForProject } from './phrase';

/** Serialize the current generated parts to a standard MIDI file byte array. */
export function generateCompositionMidi(project: CompositionProject): Uint8Array {
  const midi = new Midi();
  midi.name = project.name;
  midi.header.setTempo(project.tempoBpm);
  const secondsPerBeat = 60 / project.tempoBpm;

  const bassTrack = midi.addTrack();
  bassTrack.name = 'Bass';
  bassTrack.channel = 0;
  const includeBass = configureMix(bassTrack, project, 'bass');
  const bassPhrase = phraseForProject(project, 'bass');
  if (includeBass) {
    for (const note of layOutPhrase(project, 'bass', bassPhrase)) {
      bassTrack.addNote({
        midi: note.midi,
        time: note.startBeat * secondsPerBeat,
        duration: note.durationBeats * secondsPerBeat,
        velocity: note.velocity / 127,
      });
    }
  }

  const chordTrack = midi.addTrack();
  chordTrack.name = 'Chords';
  chordTrack.channel = 1;
  const includeChords = configureMix(chordTrack, project, 'chords');
  const chordPhrase = phraseForProject(project, 'chords');
  if (includeChords) {
    for (const note of layOutPhrase(project, 'chords', chordPhrase)) {
      chordTrack.addNote({
        midi: note.midi,
        time: note.startBeat * secondsPerBeat,
        duration: note.durationBeats * secondsPerBeat,
        velocity: note.velocity / 127,
      });
    }
  }

  const arpeggioTrack = midi.addTrack();
  arpeggioTrack.name = 'Arpeggio';
  arpeggioTrack.channel = 2;
  const includeArpeggio = configureMix(arpeggioTrack, project, 'arpeggio');
  const arpeggioPhrase = phraseForProject(project, 'arpeggio');
  if (includeArpeggio) {
    for (const note of layOutPhrase(project, 'arpeggio', arpeggioPhrase)) {
      arpeggioTrack.addNote({
        midi: note.midi,
        time: note.startBeat * secondsPerBeat,
        duration: note.durationBeats * secondsPerBeat,
        velocity: note.velocity / 127,
      });
    }
  }

  const drumTrack = midi.addTrack();
  drumTrack.name = 'Drums';
  drumTrack.channel = 9;
  const includeDrums = configureMix(drumTrack, project, 'drums');
  const drumPhrase = phraseForProject(project, 'drums');
  if (includeDrums) {
    for (const hit of layOutPhrase(project, 'drums', drumPhrase)) {
      drumTrack.addNote({
        midi: hit.midi,
        time: hit.startBeat * secondsPerBeat,
        duration: hit.durationBeats * secondsPerBeat,
        velocity: hit.velocity / 127,
      });
    }
  }

  return midi.toArray();
}

/** Apply channel mix controls and report whether notes should be written to this track. */
function configureMix(track: ReturnType<Midi['addTrack']>, project: CompositionProject, part: ArrangedPart): boolean {
  const settings: MixChannelSettings = project.mix[part];
  if (part !== 'drums') {
    track.instrument.number = settings.program;
  }
  track.addCC({ number: 7, time: 0, value: settings.volumePercent / 100 });
  track.addCC({ number: 10, time: 0, value: Math.round((settings.panPercent + 100) * 127 / 200) / 127 });
  const anySolo = ARRANGED_PARTS.some((candidate) => project.mix[candidate].solo);
  return !settings.muted && (!anySolo || settings.solo);
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
