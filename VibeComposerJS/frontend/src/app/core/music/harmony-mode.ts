import type { CompositionProject } from '../project/project.model';
import { CHORD_SPICES, chordPitches, getPitchClass, progressionChords, type ProgressionChord, type ScaleMode } from './harmony';
import { harmonyScaleNotes } from './scales';
import { sectionProject } from './section-context';

export type HarmonyModeScope = 'off' | 'global' | 'all';
const mod = (n: number) => (n % 12 + 12) % 12;

/** Revoice scale positions, retaining register and inversions.
 * Chromatic notes use the nearest old harmonic slot (lower on a tie).
 * A dominant resolving down a fifth retains its intentional chromatic quality.
 */
export function reinterpretHarmonyChords(chords: readonly (ProgressionChord | null)[],
  degrees: readonly number[], key: string, from: ScaleMode, to: ScaleMode): (ProgressionChord | null)[] {
  if (from === to) return [...chords];
  const source = harmonyScaleNotes(from), target = harmonyScaleNotes(to), tonic = getPitchClass(key)!;
  const slot = (pitch: number) => {
    let best = 0, distance = Infinity;
    for (let octave = -1; octave <= 4; octave++) for (let index = 0; index < 7; index++) {
      const candidate = source[index] + octave * 12, delta = Math.abs(candidate - pitch);
      if (delta < distance) { best = index + octave * 7; distance = delta; }
    }
    return best;
  };
  const pitchAt = (degree: number) => target[(degree % 7 + 7) % 7] + 12 * Math.floor(degree / 7);
  const resolved = progressionChords(key, from, chords.map((_, i) => degrees[i % degrees.length]), chords);
  const mapped = chords.map((chord): ProgressionChord | null => {
    if (!chord) return null;
    const rootPitch = mod(chord.root - tonic), rootSlot = slot(rootPitch);
    const root = mod(tonic + pitchAt(rootSlot));
    const original = chordPitches({ ...chord, inversion: 0 }, 0).map(pitch => pitch - chord.root);
    const intervals = [...new Set(original.map(interval => Math.max(0,
      pitchAt(slot(rootPitch + interval)) - pitchAt(rootSlot))))].sort((a,b) => a-b);
    const spice = CHORD_SPICES.find(spice => {
      const pitches = chordPitches({ root: 0, spice, inversion: 0 }, 0);
      return pitches.length === intervals.length && pitches.every((pitch,i) => pitch === intervals[i]);
    });
    return { ...chord, root, spice: spice ?? '', intervals: spice === undefined ? intervals : undefined };
  });
  const destinations = progressionChords(key, to, chords.map((_, i) => degrees[i % degrees.length]), mapped);
  return mapped.map((chord, index) => {
    const original = chords[index];
    if (!chord || !original) return chord;
    const nextIndex = (index + 1) % resolved.length, next = destinations[nextIndex];
    const originalTones = chordPitches({ ...original, inversion: 0 }, 0).map(p => mod(p - original.root));
    const targetTones = chordPitches({ ...next, inversion: 0 }, 0).map(p => mod(p - next.root));
    const dominant = originalTones.includes(4) && originalTones.includes(10)
      && mod(resolved[nextIndex].root - original.root) === 5 && mod(next.root - chord.root) === 5
      && targetTones.includes(7) && (targetTones.includes(3) || targetTones.includes(4));
    return dominant ? { ...original, root: chord.root } : chord;
  });
}

/** Only invoked by musical edit commands; loading and undo restore saved chords exactly. */
export function followHarmonyMode(current: CompositionProject, next: CompositionProject, scope: HarmonyModeScope): CompositionProject {
  if (scope === 'off') return next;
  const progressionChords = current.scale !== next.scale && next.progressionChords
    ? reinterpretHarmonyChords(next.progressionChords, next.progression, current.key, current.scale, next.scale)
    : next.progressionChords;
  const arrangement = scope !== 'all' ? next.arrangement : next.arrangement.map(section => {
    const previous = current.arrangement.find(item => item.id === section.id);
    if (!previous) return section;
    const before = sectionProject(current, previous), after = sectionProject(next, section);
    if (before.scale === after.scale) return section;
    // A local mode edit also adapts inherited explicit global chords. Global mode
    // edits already convert that shared list once, so inheriting sections need no copy.
    const inherited = !section.chordDegrees && current.scale === next.scale ? next.progressionChords : undefined;
    const custom = section.customChords ?? inherited;
    if (!custom) return section;
    const expanded = section.customDurations && custom.length !== section.customDurations.length
      ? section.customDurations.map((_, i) => custom[i % custom.length]) : custom;
    return { ...section, customChords: reinterpretHarmonyChords(expanded,
      section.chordDegrees ?? next.progression, before.key, before.scale, after.scale) };
  });
  return { ...next, progressionChords, arrangement };
}
