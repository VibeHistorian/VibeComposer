/** Playback uses a linear gain of volumePercent / 100. */
export const MAX_VOLUME_PERCENT = 100 * 10 ** (6 / 20);

export function validVolumePercent(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= MAX_VOLUME_PERCENT;
}

export function volumeDecibels(value: number): string {
  if (value <= 0) return '-Inf';
  if (value === 100) return '-0.0';
  const decibels = (20 * Math.log10(value / 100)).toFixed(1);
  return value > 100 ? `+${decibels}` : decibels;
}

export function panLabel(value: number): string {
  return value === 0 ? 'C' : `${Math.abs(value)}% ${value < 0 ? 'L' : 'R'}`;
}
