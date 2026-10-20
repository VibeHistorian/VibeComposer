import type { CompositionProject } from './project.model';
import type { MixChannelSettings } from './track.model';

/** Audition switches persist with the project, but are independent of editing history. */
export function historyContent(project: CompositionProject): string {
  const mix = ({ muted, solo, ...settings }: MixChannelSettings) => settings;
  return JSON.stringify({ ...project,
    tracks: project.tracks.map(track => ({ ...track, mix: mix(track.mix) })),
  });
}

export function preserveAudition(target: CompositionProject, current: CompositionProject): CompositionProject {
  const withSwitches = (mix: MixChannelSettings, live: MixChannelSettings) =>
    mix.muted === live.muted && mix.solo === live.solo ? mix : { ...mix, muted: live.muted, solo: live.solo };
  const tracksById = new Map(current.tracks.map(track => [track.id, track]));
  const updatedTracks = target.tracks.map(track => {
    const live = tracksById.get(track.id);
    const mix = live ? withSwitches(track.mix, live.mix) : track.mix;
    return mix === track.mix ? track : { ...track, mix };
  });
  const tracks = updatedTracks.every((track, index) => track === target.tracks[index]) ? target.tracks : updatedTracks;
  return tracks === target.tracks ? target : { ...target, tracks };
}

const label = (key: string) => key.replace(/([a-z])([A-Z])/g, '$1 $2').replaceAll('_', ' ');
const value = (raw: unknown): string => {
  if (raw === undefined) return 'inherited';
  if (typeof raw === 'boolean') return raw ? 'on' : 'off';
  if (Array.isArray(raw) && raw.some(item => item && typeof item === 'object')) return `${raw.length} items`;
  const text = typeof raw === 'object' && raw !== null ? 'custom' : String(raw);
  return text.length > 48 ? `${text.slice(0, 45)}…` : text;
};

/** Bound both traversal and output for bulk generation and phrase edits. */
export function projectDiff(before: CompositionProject, after: CompositionProject): string[] {
  const changes: string[] = [];
  const visit = (old: unknown, next: unknown, path: string): void => {
    if (changes.length >= 12 || JSON.stringify(old) === JSON.stringify(next)) return;
    if (Array.isArray(old) && Array.isArray(next) && ['Tracks', 'Arrangement'].includes(path)) {
      const previous = new Map(old.map(item => [item.id, item]));
      const following = new Map(next.map(item => [item.id, item]));
      for (const item of old) {
        if (!following.has(item.id)) changes.push(`${path}: removed ${item.name ?? item.type ?? item.id}`);
        if (changes.length >= 12) return;
      }
      for (const item of next) {
        const name = item.name ?? `${label(item.type ?? item.role)} (${item.id})`;
        if (!previous.has(item.id)) changes.push(`${path}: added ${name}`);
        else visit(previous.get(item.id), item, name);
        if (changes.length >= 12) break;
      }
      if (old.length === next.length && old.some((item, index) => item.id !== next[index]?.id)
        && old.every(item => following.has(item.id))) changes.push(`${path}: reordered`);
      return;
    }
    if (old && next && typeof old === 'object' && typeof next === 'object' && !Array.isArray(old) && !Array.isArray(next)) {
      const a = old as Record<string, unknown>, b = next as Record<string, unknown>;
      for (const key of new Set([...Object.keys(a), ...Object.keys(b)])) {
        if (key === 'muted' || key === 'solo') continue;
        visit(a[key], b[key], path ? `${path} · ${label(key)}` : label(key).replace(/^./, char => char.toUpperCase()));
        if (changes.length >= 12) break;
      }
      return;
    }
    const from = value(old), to = value(next);
    changes.push(from === to ? `${path}: ${to} (edited)` : `${path}: ${from} → ${to}`);
  };
  visit(before, after, '');
  return changes.slice(0, 12);
}
