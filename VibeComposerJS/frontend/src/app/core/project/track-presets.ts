import { ARRANGED_PARTS, type ArrangedPart } from '../music/part-role';
import type { CompositionTrack } from './track.model';
import { decodeTracks } from './project-track-decoder';
import type { CompositionProject } from './project.model';

export interface TrackPreset {
  readonly name: string;
  readonly role: ArrangedPart;
  readonly tracks: readonly CompositionTrack[];
}

/** Decode the entire library before accepting an import or stored library. */
export function decodeTrackPresets(value: unknown): TrackPreset[] | undefined {
  if (!Array.isArray(value) || value.length > 256) return undefined;
  const presets: TrackPreset[] = [];
  for (const raw of value) {
    if (!raw || typeof raw !== 'object' || typeof raw.name !== 'string' || !raw.name.trim()
      || raw.name.length > 80 || !ARRANGED_PARTS.includes(raw.role)) return undefined;
    const tracks = decodeTracks(raw.tracks);
    if (!tracks?.length || tracks.some(track => track.role !== raw.role)
      || presets.some(preset => preset.role === raw.role && preset.name === raw.name.trim())) return undefined;
    presets.push({ name: raw.name.trim(), role: raw.role, tracks });
  }
  return presets;
}

export function loadTrackPreset(project: CompositionProject, role: ArrangedPart, value: unknown, replace: boolean,
  presence: (tracks: readonly CompositionTrack[], section: CompositionProject['arrangement'][number]) => Readonly<Record<string, boolean>>): CompositionProject | undefined {
  const decoded = decodeTrackPresets([{ name: 'Load', role, tracks: value }]);
  if (!decoded || typeof replace !== 'boolean') return undefined;
  const sources = decoded[0].tracks;
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
    ...(section.trackPhrases !== undefined ? { trackPhrases: prune(section.trackPhrases) } : {}),
    ...(section.generatedTrackIds ? { generatedTrackIds: section.generatedTrackIds.filter(id => !removed.has(id)) } : {}),
  }));
  return { ...project, tracks, arrangement };
}
