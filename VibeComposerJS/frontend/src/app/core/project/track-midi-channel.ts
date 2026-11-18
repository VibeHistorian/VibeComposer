import type { ArrangedPart } from '../music/part-role';
import type { CompositionTrack } from './track.model';

export function assignTrackMidiChannel(role: ArrangedPart, tracks: readonly CompositionTrack[]): number {
  if (role === 'drums') return 10;
  const used = new Set(tracks.filter(track => track.role !== 'drums').map(track => track.midiChannel));
  for (let channel = 1; channel <= 16; channel++) if (channel !== 10 && !used.has(channel)) return channel;
  return 1;
}
