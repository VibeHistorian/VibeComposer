import { isScaleMode } from './scales';
import type { ArrangementSection, CompositionProject, PhraseNote, SectionMusicalContext } from '../project/project.model';
import { chordPitches, KEYS, progressionChords, validProgressionChords } from './harmony';

export function validSectionContext(value: unknown): value is SectionMusicalContext {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  return Object.entries(value).every(([key, field]) => {
    switch (key) {
      case 'tempoBpm': return Number.isInteger(field) && field >= 30 && field <= 300;
      case 'swingPercent': return Number.isInteger(field) && field >= 0 && field <= 100;
      case 'key': return KEYS.includes(field);
      case 'scale': return isScaleMode(field);
      case 'keyChange': return Number.isInteger(field) && field >= -24 && field <= 24;
      case 'keyChangeType': return field === 'direct' || field === 'two-five-one';
      case 'beatDurationMultiplier': return [0.5, 1, 2].includes(field);
      default: return false;
    }
  });
}

/** Musical arrays replace whole values; structural equality keeps no-ops out of history. */
export function sectionValuesEqual(left: unknown, right: unknown): boolean {
  if (left === right) return true;
  if (Array.isArray(left) && Array.isArray(right)) return left.length === right.length
    && left.every((value, index) => sectionValuesEqual(value, right[index]));
  if (!left || !right || typeof left !== 'object' || typeof right !== 'object' || Array.isArray(left) || Array.isArray(right)) return false;
  const a = left as Record<string, unknown>, b = right as Record<string, unknown>;
  return Object.keys(a).length === Object.keys(b).length && Object.keys(a).every(key =>
    Object.hasOwn(b, key) && sectionValuesEqual(a[key], b[key]));
}

export function sectionDegrees(project: CompositionProject, section: ArrangementSection): readonly number[] {
  return section.chordDegrees ?? (section.customChords
    ? section.customChords.map((_, index) => project.progression[index % project.progression.length]) : project.progression);
}

/** Compare musical chords, rather than the presence of stored section overrides.
 * Resolve primary harmony in the same section key/scale; timing stays independent.
 * Keep matching explicit values stored so later primary edits preserve ownership. */
export function sectionHasChordOverride(project: CompositionProject, section: ArrangementSection): boolean {
  if (!section.chordDegrees && !section.customChords) return false;
  const contextual = sectionProject(project, section);
  const resolve = (degrees: readonly number[], custom: CompositionProject['progressionChords']) => {
    const expanded = section.customDurations ? section.customDurations.map((_, index) => degrees[index % degrees.length]) : degrees;
    const chords = custom && custom.length !== expanded.length ? expanded.map((_, index) => custom[index % custom.length]) : custom;
    return progressionChords(contextual.key, contextual.scale, expanded, chords);
  };
  const primary = resolve(project.progression, project.progressionChords);
  const local = resolve(sectionDegrees(project, section), section.customChords ?? (section.chordDegrees ? undefined : project.progressionChords));
  return local.length !== primary.length || local.some((chord, index) => chord.root !== primary[index].root
    || chord.inversion !== primary[index].inversion || !sectionValuesEqual(chordPitches(chord, 60), chordPitches(primary[index], 60)));
}

export function validSectionHarmony(section: ArrangementSection): boolean {
  return (section.context === undefined || validSectionContext(section.context))
    && validProgressionChords(section.customChords, section.chordDegrees?.length ?? section.customChords?.length ?? 0)
    && (section.customChords === undefined || section.customChords.length > 0 && section.customChords.length <= 32)
    && (section.customDurations === undefined || Array.isArray(section.customDurations)
      && section.customDurations.length > 0 && section.customDurations.length <= 32
      && section.customDurations.every(duration => Number.isFinite(duration) && duration >= 0.25 && duration <= 32)
      && (section.customChords === undefined || section.customDurations.length === section.customChords.length)
      && (section.chordDegrees === undefined || section.customDurations.length === section.chordDegrees.length));
}

/** Duration-only overrides keep the existing harmony, repeating its slots as needed. */
export function sectionChordDurations(project: CompositionProject, section: ArrangementSection): number[] {
  const count = section.customDurations?.length ?? sectionDegrees(project, section).length;
  const multiplier = section.context?.beatDurationMultiplier ?? 1;
  return Array.from({ length: count }, (_, index) => (section.customDurations?.[index] ?? 4) * multiplier);
}

/** Preserve the workspace's chord-slot measure count; each slot now has an explicit duration. */
export function sectionDurationBeats(project: CompositionProject, section: ArrangementSection): number {
  const durations = sectionChordDurations(project, section);
  return Array.from({ length: section.measures }, (_, index) => durations[index % durations.length]).reduce((sum, value) => sum + value, 0);
}

export function sectionProject(project: CompositionProject, section?: ArrangementSection): CompositionProject {
  if (!section?.context) return project;
  const context = section.context;
  return { ...project, key: context.key ?? project.key,
    scale: context.scale ?? project.scale, tempoBpm: context.tempoBpm ?? project.tempoBpm };
}

/** Seeded generators work in normalized four-beat slots. Map onset and end separately,
 * including notes crossing a chord boundary, then add absolute part offsets/feedback. */
export function mapChordBeat(beat: number, durations: readonly number[]): number {
  if (beat < 0) return beat * durations[0] / 4;
  const slot = Math.floor((beat + 1e-9) / 4);
  const cycles = Math.floor(slot / durations.length), index = slot % durations.length;
  return cycles * durations.reduce((sum, value) => sum + value, 0)
    + durations.slice(0, index).reduce((sum, value) => sum + value, 0) + (beat - slot * 4) * durations[index] / 4;
}

export function mapChordNotes(notes: readonly PhraseNote[], durations: readonly number[]): PhraseNote[] {
  return notes.map(note => ({ ...note, startBeat: mapChordBeat(note.startBeat, durations),
    durationBeats: mapChordBeat(note.startBeat + note.durationBeats, durations) - mapChordBeat(note.startBeat, durations) }));
}

export interface TempoPoint { readonly beat: number; readonly bpm: number; readonly seconds: number; }
export function projectTempoMap(project: CompositionProject): TempoPoint[] {
  const result: TempoPoint[] = [];
  let beat = 0, seconds = 0;
  for (const section of project.arrangement) {
    const bpm = section.context?.tempoBpm ?? project.tempoBpm;
    if (!result.length || result.at(-1)!.bpm !== bpm) result.push({ beat, seconds, bpm });
    const duration = sectionDurationBeats(project, section);
    beat += duration; seconds += duration * 60 / bpm;
  }
  return result.length ? result : [{ beat: 0, seconds: 0, bpm: project.tempoBpm }];
}
export function beatToSeconds(map: readonly TempoPoint[], beat: number): number {
  const point = [...map].reverse().find(point => point.beat <= beat) ?? map[0];
  return point.seconds + (beat - point.beat) * 60 / point.bpm;
}
export function secondsToBeat(map: readonly TempoPoint[], seconds: number): number {
  const point = [...map].reverse().find(point => point.seconds <= seconds) ?? map[0];
  return point.beat + (seconds - point.seconds) * point.bpm / 60;
}
