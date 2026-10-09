/** Playback uses a linear gain of volumePercent / 100. */
export function volumeDecibels(value: number): string {
  return value <= 0 ? '-Inf' : value >= 100 ? '0dB' : `${(20 * Math.log10(value / 100)).toFixed(1)} dB`;
}

export function panLabel(value: number): string {
  return value === 0 ? 'C' : `${Math.abs(value)}% ${value < 0 ? 'L' : 'R'}`;
}
