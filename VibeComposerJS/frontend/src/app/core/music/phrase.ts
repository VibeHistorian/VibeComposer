import type { ArrangedPart, CompositionProject, PhraseNote } from '../project/project.model';
import { shouldGeneratePartInSection } from './arrangement-generator';
import { generateArpeggio } from './arpeggio-generator';
import { generateBassline } from './bass-generator';
import { generateChordPart } from './chord-generator';
import { generateDrumPart } from './drum-generator';

/** Convert the seeded generators to note-level events shared by the editor and MIDI exporter. */
export function generatePhrase(project: CompositionProject, part: ArrangedPart): PhraseNote[] {
  const seed = BigInt(project.seed);
  switch (part) {
    case 'bass':
      return generateBassline(seed, project.key, project.scale, project.progression,
        project.bass.rhythm, project.bass.noteVariation)
        .map((note, index) => ({ id: `bass-${index}`, midi: note.midi, startBeat: note.startBeat,
          durationBeats: note.durationBeats, velocity: note.velocity }));
    case 'chords':
      return generateChordPart(seed, project.key, project.scale, project.progression, project.chords)
        .flatMap((hit, hitIndex) => hit.pitches.map((midi, pitchIndex) => ({
          id: `chords-${hitIndex}-${pitchIndex}`, midi, startBeat: hit.startBeat,
          durationBeats: hit.durationBeats, velocity: hit.velocity,
        })));
    case 'arpeggio':
      return generateArpeggio(seed, project.key, project.scale, project.progression, project.arpeggio)
        .map((note, index) => ({ id: `arpeggio-${index}`, midi: note.midi, startBeat: note.startBeat,
          durationBeats: note.durationBeats, velocity: note.velocity }));
    case 'drums':
      return generateDrumPart(seed, project.progression.length, project.drums)
        .map((hit, index) => ({ id: `drums-${index}`, midi: hit.midi, startBeat: hit.startBeat,
          durationBeats: hit.durationBeats, velocity: hit.velocity }));
  }
}

/** Return saved note edits when present, otherwise build the phrase from project settings. */
export function phraseForProject(project: CompositionProject, part: ArrangedPart): readonly PhraseNote[] {
  return project.editedPhrases[part] ?? generatePhrase(project, part);
}

/** Repeat a phrase through arrangement sections where its part enters. */
export function layOutPhrase<Event extends { readonly startBeat: number }>(
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
