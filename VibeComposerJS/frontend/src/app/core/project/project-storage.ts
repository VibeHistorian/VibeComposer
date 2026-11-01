import { type ArrangedPart, ARRANGED_PARTS } from '../music/part-role';
import type { CompositionProject } from './project.model';
import type { CompositionTrack } from './track.model';

/** Included one-based track numbers follow each role's order in the file. */
export function encodeProjectStorage(project: CompositionProject) {
  const groups = ARRANGED_PARTS.map(role => [role, project.tracks.filter(track => track.role === role)] as const);
  return { ...project, arrangement: project.arrangement.map(section => ({ ...section,
    trackPresence: Object.fromEntries(groups.map(([role, tracks]) =>
      [role, tracks.flatMap((track, index) => section.trackPresence[track.id] ? [index + 1] : [])])) as Record<ArrangedPart, number[]>,
  })) };
}

/** Expand included track numbers; also accept existing ID maps and boolean arrays. */
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
  const legacyFlags = entries.some(([, values]) => Array.isArray(values) && values.some(value => typeof value === 'boolean'));
  const presence: [string, boolean][] = [];
  for (const role of ARRANGED_PARTS) {
    const members = tracks.filter(track => track.role === role);
    const flags = fields[role];
    if (flags === undefined && members.length === 0) continue;
    if (!Array.isArray(flags)) return undefined;
    if (legacyFlags) {
      if (flags.length !== members.length || flags.some(flag => typeof flag !== 'boolean')) return undefined;
      members.forEach((track, index) => presence.push([track.id, flags[index]]));
    } else {
      if (flags.some(number => !Number.isInteger(number) || number < 1 || number > members.length)
        || new Set(flags).size !== flags.length) return undefined;
      const included = new Set(flags);
      members.forEach((track, index) => presence.push([track.id, included.has(index + 1)]));
    }
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
