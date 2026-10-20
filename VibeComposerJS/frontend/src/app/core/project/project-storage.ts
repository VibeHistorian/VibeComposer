import type { ArrangedPart, CompositionProject, CompositionTrack } from './project.model';
import { ARRANGED_PARTS } from './project.model';

/** Array positions follow tracks filtered by role, preserving their order in the file. */
export function encodeProjectStorage(project: CompositionProject) {
  const groups = ARRANGED_PARTS.map(role => [role, project.tracks.filter(track => track.role === role)] as const);
  return { ...project, arrangement: project.arrangement.map(section => ({ ...section,
    trackPresence: Object.fromEntries(groups.map(([role, tracks]) =>
      [role, tracks.map(track => section.trackPresence[track.id] ?? false)])) as Record<ArrangedPart, boolean[]>,
  })) };
}

/** Accept existing ID maps as well as complete role arrays at the storage boundary. */
export function decodeStoredTrackPresence(value: unknown, tracks: readonly CompositionTrack[]): Record<string, boolean> | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const fields = value as Record<string, unknown>;
  const entries = Object.entries(fields);
  if (!entries.some(([, flags]) => Array.isArray(flags))) {
    const ids = new Set(tracks.map(track => track.id));
    return entries.every(([id, flag]) => ids.has(id) && typeof flag === 'boolean')
      ? Object.fromEntries(entries) as Record<string, boolean> : undefined;
  }
  if (entries.some(([role]) => !ARRANGED_PARTS.includes(role as ArrangedPart))) return undefined;
  const presence: [string, boolean][] = [];
  for (const role of ARRANGED_PARTS) {
    const members = tracks.filter(track => track.role === role);
    const flags = fields[role];
    if (flags === undefined && members.length === 0) continue;
    if (!Array.isArray(flags) || flags.length !== members.length || flags.some(flag => typeof flag !== 'boolean')) return undefined;
    members.forEach((track, index) => presence.push([track.id, flags[index]]));
  }
  return Object.fromEntries(presence);
}

/** Expand stored arrays before the app's ID-based arrangement validation. */
export function decodeStoredArrangement(value: unknown, tracks: readonly CompositionTrack[]): unknown {
  if (!Array.isArray(value)) return value;
  return value.map(section => {
    if (!section || typeof section !== 'object' || section.trackPresence === undefined) return section;
    const trackPresence = decodeStoredTrackPresence(section.trackPresence, tracks);
    return trackPresence ? { ...section, trackPresence } : null;
  });
}
