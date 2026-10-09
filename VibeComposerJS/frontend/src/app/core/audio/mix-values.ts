/** Playback uses a linear gain of volumePercent / 100. */
export function volumeDecibels(value: number): string {
  return value <= 0 ? '-Inf' : value >= 100 ? '-0.0' : (20 * Math.log10(value / 100)).toFixed(1);
}

export function panLabel(value: number): string {
  return value === 0 ? 'C' : `${Math.abs(value)}% ${value < 0 ? 'L' : 'R'}`;
}
