import { fitVariationCapacity } from './arrangement-variation-capacity';
import { ARRANGED_PARTS, type ArrangedPart } from '../music/part-role';
import { pruneVariations } from '../music/arrangement-variations';
import type { CompositionTrack } from './track.model';
import { decodeTracks } from './project-track-decoder';
import type { CompositionProject } from './project.model';

export interface TrackPreset {
  readonly name: string;
  readonly role: ArrangedPart | 'all';
  readonly tracks: readonly CompositionTrack[];
}

/** Decode the entire library before accepting an import or stored library. */
export function decodeTrackPresets(value: unknown): TrackPreset[] | undefined {
  if (!Array.isArray(value) || value.length > 256) return undefined;
  const presets: TrackPreset[] = [];
  for (const raw of value) {
    if (!raw || typeof raw !== 'object' || typeof raw.name !== 'string' || !raw.name.trim()
      || raw.name.length > 80 || !(raw.role === 'all' || ARRANGED_PARTS.includes(raw.role))) return undefined;
    const tracks = decodeTracks(raw.tracks);
    if (!tracks?.length || (raw.role !== 'all' && tracks.some(track => track.role !== raw.role))
      || presets.some(preset => preset.role === raw.role && preset.name === raw.name.trim())) return undefined;
    presets.push({ name: raw.name.trim(), role: raw.role, tracks });
  }
  return presets;
}

export function loadTrackPreset(project: CompositionProject, role: ArrangedPart | 'all', value: unknown, replace: boolean,
  presence: (tracks: readonly CompositionTrack[], section: CompositionProject['arrangement'][number]) => Readonly<Record<string, boolean>>): CompositionProject | undefined {
  const decoded = decodeTrackPresets([{ name: 'Load', role, tracks: value }]);
  if (!decoded || typeof replace !== 'boolean') return undefined;
  if (role === 'all') {
    let next = project;
    const delta = (r: ArrangedPart) => decoded[0].tracks.filter(t => t.role === r).length
      - project.tracks.filter(t => replace && t.role === r && !t.rerollLocked).length;
    // Free surplus slots first, avoiding intermediate capacity failures.
    for (const member of [...ARRANGED_PARTS].sort((a, b) => delta(a) - delta(b))) {
      const candidate = loadRolePreset(next, member, decoded[0].tracks.filter(t => t.role === member), replace, presence);
      if (!candidate) return undefined;
      next = candidate;
    }
    return next;
  }
  return loadRolePreset(project, role, decoded[0].tracks, replace, presence);
}

function loadRolePreset(project: CompositionProject, role: ArrangedPart, sources: readonly CompositionTrack[], replace: boolean,
  presence: (tracks: readonly CompositionTrack[], section: CompositionProject['arrangement'][number]) => Readonly<Record<string, boolean>>): CompositionProject | undefined {
  const destinations = project.tracks.filter(track => replace && track.role === role && !track.rerollLocked);
  const removed = new Set(destinations.slice(sources.length).map(track => track.id));
  const replacements = new Map(destinations.slice(0, sources.length).map((track, index) =>
    [track.id, structuredClone({ ...sources[index], id: track.id })]));
  const tracks = project.tracks.filter(track => !removed.has(track.id)).map(track => replacements.get(track.id) ?? track);
  const extraSources = sources.slice(destinations.length);
  if (tracks.length + extraSources.length > 64) return undefined;
  // Replaced slots retain identity and section state. Only additional tracks need fresh IDs and presence.
  const ids = new Set(project.tracks.map(track => track.id));
  const added = extraSources.map(source => {
    let number = 1;
    while (ids.has(`track-${role}-${number}`)) number++;
    const id = `track-${role}-${number}`;
    ids.add(id);
    const track = structuredClone({ ...source, id });
    tracks.push(track);
    return track;
  });
  const prune = <T>(map: Readonly<Record<string, T>> | undefined): Record<string, T> =>
    Object.fromEntries(Object.entries(map ?? {}).filter(([id]) => !removed.has(id)));
  const arrangement = !removed.size && !added.length ? project.arrangement : project.arrangement.map(section => ({ ...section,
    trackPresence: { ...prune(section.trackPresence), ...(added.length ? presence(added, section) : {}) },
    trackPartOverrides: prune(section.trackPartOverrides),
    ...(section.variations ? { variations: pruneVariations(section.variations, removed) } : {}),
    ...(section.trackPhrases !== undefined ? { trackPhrases: prune(section.trackPhrases) } : {}),
    ...(section.generatedTrackIds ? { generatedTrackIds: section.generatedTrackIds.filter(id => !removed.has(id)) } : {}),
  }));
  return fitVariationCapacity({ ...project, tracks, arrangement });
}
