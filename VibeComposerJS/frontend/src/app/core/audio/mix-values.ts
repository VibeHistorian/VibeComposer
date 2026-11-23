import { drumPanIsAutomatic, type CompositionTrack, type MixChannelSettings } from '../project/track.model';

/** Playback uses a linear gain of volumePercent / 100. */
export const MAX_VOLUME_PERCENT = 100 * 10 ** (6 / 20);

export function validVolumePercent(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= MAX_VOLUME_PERCENT;
}

/** The one-decimal dB display has a single unity position, including either side of zero. */
export function normalizeVolumePercent(value: number): number {
  return value > 0 && Number((20 * Math.log10(value / 100)).toFixed(1)) === 0 ? 100 : value;
}

export function volumeDecibels(value: number): string {
  if (value <= 0) return '-Inf';
  if (normalizeVolumePercent(value) === 100) return '-0.0';
  const decibels = (20 * Math.log10(value / 100)).toFixed(1);
  return value > 100 ? `+${decibels}` : decibels;
}

export function panLabel(value: number): string {
  return value === 0 ? 'C' : `${Math.abs(value)}% ${value < 0 ? 'L' : 'R'}`;
}

/** Mute takes precedence over solo. Gain remains a separate consumer decision. */
export function mixEnabled(mix: Pick<MixChannelSettings, 'muted' | 'solo'>, anySolo: boolean): boolean {
  return !mix.muted && (!anySolo || mix.solo);
}

/** MIDI pan is a seven-bit controller with center at 64. */
export function midiPanValue(panPercent: number): number {
  return Math.round((panPercent + 100) * 127 / 200);
}

/** Auto pan lives in the audio mixer. MIDI percussion shares one channel-wide pan. */
export function drumChannelPan(tracks: readonly CompositionTrack[]): number | undefined {
  const drums = tracks.filter(track => track.role === 'drums');
  if (!drums.some(track => track.drumPan === 'auto')) return undefined;
  const authored = drums.filter(track => !drumPanIsAutomatic(track));
  const first = authored[0]?.mix.panPercent ?? 0;
  return authored.every(track => track.mix.panPercent === first) ? first : 0;
}

export function withDrumChannelPan(track: CompositionTrack, panPercent: number | undefined): CompositionTrack {
  return track.role === 'drums' && panPercent !== undefined && track.mix.panPercent !== panPercent
    ? { ...track, mix: { ...track.mix, panPercent } } : track;
}

/** MIDI channel volume has no separate master bus; combine levels only at the output boundary. */
export function withMasterVolume(track: import('../project/track.model').CompositionTrack, master = 100): import('../project/track.model').CompositionTrack {
  return master === 100 ? track : { ...track, mix: { ...track.mix, volumePercent: track.mix.volumePercent * master / 100 } };
}
