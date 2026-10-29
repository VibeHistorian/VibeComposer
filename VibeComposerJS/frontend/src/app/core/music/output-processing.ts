import type { CompositionProject } from '../project/project.model';
import type { PhraseNote } from './phrase-note';
import { JavaRandom } from './java-random';
import { ARRANGED_PARTS } from './part-role';

export function validOutputSettings(project: Partial<CompositionProject>): boolean {
  const h = project.humanization;
  return (h === undefined || !!h && typeof h === 'object' && !Array.isArray(h)
    && Object.keys(h).every(key => ['timingBeats', 'durationBeats'].includes(key))
    && [h.timingBeats, h.durationBeats].every(value => Number.isFinite(value) && value >= 0 && value <= .25))
    && (project.midiPadding === undefined || !!project.midiPadding && typeof project.midiPadding === 'object'
      && !Array.isArray(project.midiPadding) && Object.entries(project.midiPadding).every(([role, count]) =>
        ARRANGED_PARTS.includes(role as typeof ARRANGED_PARTS[number]) && Number.isInteger(count) && count >= 0 && count <= 32));
}

/** Final output after part timing and manual replacement. Independent stream preserves generator draws. */
export function processOutputNotes<T extends PhraseNote & { readonly sectionId?: string }>(
  project: CompositionProject, trackId: string, notes: readonly T[]): T[] {
  let identity = 0n;
  for (const char of trackId) identity = BigInt.asIntN(64, identity * 31n + BigInt(char.charCodeAt(0)));
  const random = new JavaRandom(BigInt(project.seed) ^ identity ^ 0x48554d414en);
  const h = project.humanization;
  return notes.map(note => {
    const timing = (random.nextDouble() * 2 - 1) * (h?.timingBeats ?? 0);
    const duration = (random.nextDouble() * 2 - 1) * (h?.durationBeats ?? 0);
    const db = project.arrangement.find(section => section.id === note.sectionId)?.context?.velocityDb ?? 0;
    return { ...note, startBeat: note.startBeat + timing, durationBeats: Math.max(1 / 960, note.durationBeats + duration),
      velocity: note.velocity <= 0 ? 0 : Math.max(1, Math.min(127, Math.round(note.velocity * 10 ** (db / 20)))) };
  });
}
