/** Physical row order: white keys beginning with C2 on 1. */
export const NOTE_CODES = ['Digit1','Digit2','Digit3','Digit4','Digit5','Digit6','Digit7','Digit8','Digit9','Digit0',
  ...'QWERTYUIOPASDFGHJKLZXCVBNM'.split('').map(key => 'Key' + key)];
export function keyboardMidi(code: string, shift: boolean): number | null {
  const index = NOTE_CODES.indexOf(code);
  return index < 0 ? null : 36 + Math.floor(index / 7) * 12 + [0,2,4,5,7,9,11][index % 7] + Number(shift);
}
export function recordingCell(beat: number, length: number, cells: number): number {
  return Math.min(cells - 1, Math.floor(((beat % length + length) % length) / length * cells));
}
export interface RecordedSpan { start: number; end: number; midi: number; velocity: number; }
export function recordedSegments(span: RecordedSpan, length: number, grid: number): RecordedSpan[] {
  const start = Math.floor(span.start / grid + 1e-7) * grid;
  const end = Math.max(start + grid, Math.round(span.end / grid) * grid);
  const result: RecordedSpan[] = [];
  for (let cursor = start; cursor < end; ) {
    const local = (cursor % length + length) % length;
    const duration = Math.min(end - cursor, length - local);
    result.push({ ...span, start: local, end: local + duration }); cursor += duration;
  }
  return result;
}
