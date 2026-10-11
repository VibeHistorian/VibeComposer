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
