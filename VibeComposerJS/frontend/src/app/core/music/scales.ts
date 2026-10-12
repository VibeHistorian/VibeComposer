/** MidiUtils.Scales and ScaleMode, including Java's repeated pentatonic degrees.
 * The five nonzero-origin experimental scales are excluded: degree I and tonic
 * targets in this workspace refer to the selected key, not an offset tonic.
 */
export const SCALE_DEFINITIONS = {
  major: { label: 'Major', notes: [0,2,4,5,7,9,11], target: 0 },
  dorian: { label: 'Dorian', notes: [0,2,3,5,7,9,10], target: 2 },
  phrygian: { label: 'Phrygian', notes: [0,1,3,5,7,8,10], target: 1 },
  lydian: { label: 'Lydian', notes: [0,2,4,6,7,9,11], target: 3 },
  mixolydian: { label: 'Mixolydian', notes: [0,2,4,5,7,9,10], target: 6 },
  'natural-minor': { label: 'Natural minor (Aeolian)', notes: [0,2,3,5,7,8,10], target: 5 },
  locrian: { label: 'Locrian', notes: [0,1,3,4,6,8,10], target: 4 },
  blues: { label: 'Blues', notes: [0,2,3,4,7,9,12], target: -1 },
  'harmonic-minor': { label: 'Harmonic minor', notes: [0,2,3,5,7,8,11], target: 5 },
  turkish: { label: 'Turkish', notes: [0,1,3,5,7,10,11], target: -1 },
  indian: { label: 'Indian', notes: [0,1,1,4,5,8,10], target: 2 },
  'harmonic-major': { label: 'Harmonic major', notes: [0,2,4,5,7,8,11], target: 5 },
  whiskey: { label: 'Whiskey', notes: [0,3,5,6,7,10,11], target: 1 },
  'double-harmonic': { label: 'Double harmonic', notes: [0,1,4,5,7,8,11], target: 1 },
  'pentatonic-major': { label: 'Pentatonic major', notes: [0,2,4,4,7,7,9], target: 1 },
  'pentatonic-minor': { label: 'Pentatonic minor (Java)', notes: [0,3,4,4,7,7,10], target: 1 },
  'hungarian-minor': { label: 'Hungarian minor', notes: [0,2,3,6,7,8,11], target: 3 },
} as const;
export type ScaleMode = keyof typeof SCALE_DEFINITIONS;
export const SCALE_MODES = Object.keys(SCALE_DEFINITIONS) as ScaleMode[];
export function isScaleMode(value: unknown): value is ScaleMode {
  return typeof value === 'string' && Object.hasOwn(SCALE_DEFINITIONS, value);
}
export function scaleNotes(scale: ScaleMode): readonly number[] { return SCALE_DEFINITIONS[scale].notes; }
export function modeTargetPitch(scale: ScaleMode): number {
  const target = SCALE_DEFINITIONS[scale].target;
  return target < 0 ? -1 : scaleNotes('major')[target];
}
/** Java maps the first matching degree; repeated entries and 12 must not be normalized away. */
export function convertScalePitch(pitch: number, from: readonly number[], to: readonly number[], snap = false): number {
  if (pitch < 0) return pitch;
  const pc = pitch % 12, index = from.indexOf(pc);
  if (index >= 0) return pitch - from[index] + to[index < to.length ? index : Math.floor(to.length * index / from.length)];
  if (!snap || to.includes(pc)) return pitch;
  let closest = to[0];
  for (const candidate of to.slice(1)) if (Math.abs(pc - candidate) < Math.abs(pc - closest)) closest = candidate;
  return pitch - pc + closest;
}
