/** Physical row order: white keys beginning with C2 on 1. */
export const NOTE_CODES = ['Digit1','Digit2','Digit3','Digit4','Digit5','Digit6','Digit7','Digit8','Digit9','Digit0',
  ...'QWERTYUIOPASDFGHJKLZXCVBNM'.split('').map(key => 'Key' + key)];
/** Scale pitch classes are in degree order, shared with the phrase editor's scale/key snapping. */
export function keyboardMidi(code: string, shift: boolean, scale: readonly number[] = [], tonic?: number): number | null {
  const index = NOTE_CODES.indexOf(code);
  if (index < 0) return null;
  if (scale.length) {
    const root = scale[0], degree = index % scale.length;
    return Math.min(127, (tonic ?? 36 + root) + Math.floor(index / scale.length) * 12 + (scale[degree] - root + 12) % 12 + Number(shift));
  }
  return 36 + Math.floor(index / 7) * 12 + [0,2,4,5,7,9,11][index % 7] + Number(shift);
}
export function keyboardLabels(scale: readonly number[] = [], tonic?: number): readonly { midi: number; label: string }[] {
  return NOTE_CODES.flatMap(code => [false, true].map(shift => ({ midi: keyboardMidi(code, shift, scale, tonic)!,
    label: (shift ? '⇧' : '') + code.replace('Digit', '').replace('Key', '') })));
}
/** Position and tick boundaries also support fractional phrase/section lengths. */
export function recordingPosition(beat: number, length: number): number { return (beat % length + length) % length; }
export function recordingTickAtOrAfter(beat: number, length: number): number {
  const cycle = Math.floor(beat / length), local = beat - cycle * length;
  return Math.min((cycle + 1) * length, cycle * length + Math.ceil(local - 1e-7));
}
export function nextRecordingTick(beat: number, length: number): number { return recordingTickAtOrAfter(beat + 1e-5, length); }
export function recordingCell(beat: number, length: number, cells: number): number {
  return Math.min(cells - 1, Math.floor(((beat % length + length) % length) / length * cells));
}
export interface RecordedSpan { start: number; end: number; midi: number; velocity: number; }
export function recordedSegments(span: RecordedSpan, length: number, grid: number, live = false): RecordedSpan[] {
  const start = Math.floor(span.start / grid + 1e-7) * grid;
  const end = live ? Math.max(start + 0.02, span.end) : Math.max(start + grid, Math.round(span.end / grid) * grid);
  const result: RecordedSpan[] = [];
  for (let cursor = start; cursor < end; ) {
    const local = (cursor % length + length) % length;
    const duration = Math.min(end - cursor, length - local);
    result.push({ ...span, start: local, end: local + duration }); cursor += duration;
  }
  return result;
}
