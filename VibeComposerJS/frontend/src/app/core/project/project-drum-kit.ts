import type { CompositionProject } from './project.model';
import { CLASSIC_DRUM_INSTRUMENTS, MAX_DRUM_SOUNDS, drumFamily, type DrumSlots } from '../music/drum-instruments';

/** Count retained musical data, including muted/absent tracks and manual replacements. */
export function projectDrumPitches(project: Pick<CompositionProject, 'tracks' | 'arrangement' | 'editedPhrases'>, omitGeneratorIds: readonly string[] = []): number[] {
  const pitches = new Set<number>();
  const notes = (phrase: readonly { midi: number }[] | undefined) => phrase?.forEach(note => pitches.add(note.midi));
  notes(project.editedPhrases.drums);
  for (const track of project.tracks) {
    if (track.role !== 'drums') continue;
    if (!omitGeneratorIds.includes(track.id)) pitches.add(track.generatorSettings.pitch);
    notes(track.editedPhrase);
    track.phraseEdits?.forEach(edit => notes(edit.notes));
    for (const section of project.arrangement) notes(section.trackPhrases?.[track.id]);
  }
  return [...pitches].sort((a, b) => a - b);
}

export function validDrumKit(project: CompositionProject): boolean {
  if (projectDrumPitches(project).length > MAX_DRUM_SOUNDS) return false;
  return validDrumSlots(project.drumSlots);
}

export function validDrumSlots(value: unknown): boolean {
  if (value === undefined) return true;
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const entries = Object.entries(value);
  return entries.length <= MAX_DRUM_SOUNDS && new Set(entries.map(([, slot]) => slot)).size === entries.length
    && entries.every(([pitch, slot]) => /^(0|[1-9]\d*)$/.test(pitch) && Number(pitch) <= 127
      && Number.isInteger(slot) && slot >= 36 && slot < 36 + MAX_DRUM_SOUNDS);
}

/** Preserve survivors, transfer unique replacements, then prefer legacy/family slots. Never compact. */
export function reconcileDrumKit(project: CompositionProject, previous?: CompositionProject, extraPitches: readonly number[] = []): CompositionProject | undefined {
  if (!validDrumKit(project)) return undefined;
  const pitches = [...new Set([...projectDrumPitches(project), ...extraPitches])].sort((a, b) => a - b);
  if (pitches.length > MAX_DRUM_SOUNDS) return undefined;
  const slots: Record<string, number> = {};
  const old = project.drumSlots ?? previous?.drumSlots ?? {};
  for (const pitch of pitches) if (old[pitch] !== undefined) slots[pitch] = old[pitch];
  if (previous) for (const track of project.tracks) {
    if (track.role !== 'drums' || slots[track.generatorSettings.pitch] !== undefined) continue;
    const source = previous.tracks.find(item => item.id === track.id);
    if (source?.role !== 'drums' || pitches.includes(source.generatorSettings.pitch)) continue;
    const slot = old[source.generatorSettings.pitch];
    if (slot !== undefined && !Object.values(slots).includes(slot)) slots[track.generatorSettings.pitch] = slot;
  }
  // Reserve all available exact legacy assignments before assigning unfamiliar sounds.
  for (const pitch of pitches) {
    const index = CLASSIC_DRUM_INSTRUMENTS.findIndex(item => item.midi === pitch);
    if (slots[pitch] === undefined && index >= 0 && !Object.values(slots).includes(36 + index)) slots[pitch] = 36 + index;
  }
  for (const pitch of pitches) if (slots[pitch] === undefined) {
    const free = Array.from({ length: MAX_DRUM_SOUNDS }, (_, index) => 36 + index).filter(slot => !Object.values(slots).includes(slot));
    slots[pitch] = free.find(slot => drumFamily(CLASSIC_DRUM_INSTRUMENTS[slot - 36].midi) === drumFamily(pitch)) ?? free[0];
  }
  return project.drumSlots && Object.keys(project.drumSlots).length === pitches.length
    && pitches.every(pitch => project.drumSlots![pitch] === slots[pitch]) ? project : { ...project, drumSlots: slots };
}

export function canSelectDrumPitch(project: CompositionProject, trackId: string, pitch: number): boolean {
  return new Set([...projectDrumPitches(project, [trackId]), pitch]).size <= MAX_DRUM_SOUNDS;
}

/** Predict one menu choice without rescanning every retained phrase for every catalogue entry. */
export function drumSelectionSlot(pitch: number, sourcePitch: number, retained: readonly number[], slots: DrumSlots): number | undefined {
  if (slots[pitch] !== undefined) return slots[pitch];
  if (retained.length >= MAX_DRUM_SOUNDS) return undefined;
  if (!retained.includes(sourcePitch) && slots[sourcePitch] !== undefined) return slots[sourcePitch];
  const occupied = new Set(retained.map(value => slots[value]));
  const free = Array.from({ length: MAX_DRUM_SOUNDS }, (_, index) => 36 + index).filter(slot => !occupied.has(slot));
  const classic = CLASSIC_DRUM_INSTRUMENTS.findIndex(item => item.midi === pitch);
  return free.find(slot => slot === 36 + classic)
    ?? free.find(slot => drumFamily(CLASSIC_DRUM_INSTRUMENTS[slot - 36].midi) === drumFamily(pitch)) ?? free[0];
}

const slotCache = new WeakMap<CompositionProject, DrumSlots>();
export function resolvedDrumSlots(project: CompositionProject, extraPitches: readonly number[] = []): DrumSlots {
  const cached = slotCache.get(project);
  if (cached && extraPitches.every(pitch => cached[pitch] !== undefined)) return cached;
  const normalized = reconcileDrumKit(project, undefined, extraPitches);
  if (!normalized) throw new RangeError(`A project supports at most ${MAX_DRUM_SOUNDS} distinct drum sounds.`);
  if (!extraPitches.length) slotCache.set(project, normalized.drumSlots!);
  return normalized.drumSlots!;
}
