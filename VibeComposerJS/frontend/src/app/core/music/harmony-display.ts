import type { ArrangedPart } from './part-role';
import type { ArrangementSection } from '../project/arrangement.model';
import type { CompositionProject } from '../project/project.model';
import type { CompositionTrack } from '../project/track.model';
import { chordPitches, chordRootHue, effectiveKey, KEYS, progressionChords } from './harmony';
import { baseGenerationHarmony, sectionGenerationHarmony } from './project-harmony';
import { sectionProject } from './section-context';

export interface HarmonyChordLabel { readonly label: string; readonly hue: number; }
export interface SectionHarmonyDisplay {
  readonly chords: readonly HarmonyChordLabel[];
  readonly durations: readonly number[];
  readonly chordsDiffer: boolean;
  readonly durationsDiffer: boolean;
}

/** Compare repeating musical cycles, including explicit copies of the same progression. */
function cyclesDiffer<T>(left: readonly T[], right: readonly T[], equal: (a: T, b: T) => boolean): boolean {
  for (let index = 0; index < left.length * right.length; index++) {
    if (!equal(left[index % left.length], right[index % right.length])) return true;
  }
  return false;
}

const displays = new WeakMap<CompositionProject, Map<ArrangementSection, Map<ArrangedPart, SectionHarmonyDisplay>>>();

/** The sounding chord names and timing shared by arrangement and score headers. */
export function sectionHarmonyDisplay(project: CompositionProject, section: ArrangementSection, role: ArrangedPart = 'chords'): SectionHarmonyDisplay {
  let sections = displays.get(project);
  if (!sections) { sections = new Map(); displays.set(project, sections); }
  let roles = sections.get(section);
  if (!roles) { roles = new Map(); sections.set(section, roles); }
  const cached = roles.get(role);
  if (cached) return cached;
  const contextual = sectionProject(project, section);
  const harmony = sectionGenerationHarmony(project, section, role), global = baseGenerationHarmony(project);
  const chords = progressionChords(contextual.key, contextual.scale, harmony.degrees, harmony.customChords);
  const primary = progressionChords(project.key, project.scale, global.degrees, global.customChords);
  const shift = section.context?.keyChange ?? 0;
  const transpose = (project.transposeSemitones ?? 0) + shift;
  const key = effectiveKey(contextual.key, transpose);
  const pitches = chords.map(chord => chordPitches(chord, 0).map(pitch => pitch + shift));
  const primaryPitches = primary.map(chord => chordPitches(chord, 0));
  const result: SectionHarmonyDisplay = {
    chords: chords.map(chord => {
      const root = ((chord.root + transpose) % 12 + 12) % 12;
      return { label: KEYS[root] + (chord.intervals ? `[${chord.intervals.join(',')}]` : chord.spice)
        + (chord.inversion ? '.' + chord.inversion : ''), hue: chordRootHue(root, key) };
    }),
    durations: harmony.durations,
    chordsDiffer: cyclesDiffer(pitches, primaryPitches, (a, b) => a.length === b.length && a.every((pitch, index) => pitch === b[index])),
    durationsDiffer: cyclesDiffer(harmony.durations, global.durations, (a, b) => a === b),
  };
  roles.set(role, result);
  return result;
}

/** Section-wide context may affect generated notes without taking ownership of track settings. */
export function trackHasSectionHarmony(project: CompositionProject, track: CompositionTrack, section: ArrangementSection): boolean {
  const harmony = sectionHarmonyDisplay(project, section, track.role), context = section.context;
  return (track.role !== 'drums' && harmony.chordsDiffer) || harmony.durationsDiffer
    || context?.tempoBpm !== undefined && context.tempoBpm !== project.tempoBpm
    || track.role !== 'bass' && context?.swingPercent !== undefined && context.swingPercent !== (track.generatorSettings.swingPercent ?? 50)
    || track.role !== 'drums' && (context?.key !== undefined && context.key !== project.key
      || context?.scale !== undefined && context.scale !== project.scale);
}
