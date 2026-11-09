/** Ascending, distinct pitch classes relative to the tonic, without a repeated octave.
 * Saved IDs remain stable; names and pitches follow music theory rather than Java bugs.
 */
export const SCALE_DEFINITIONS = {
  major: { label: 'Major', notes: [0,2,4,5,7,9,11], target: 0 },
  dorian: { label: 'Dorian', notes: [0,2,3,5,7,9,10], target: 2 },
  phrygian: { label: 'Phrygian', notes: [0,1,3,5,7,8,10], target: 1 },
  lydian: { label: 'Lydian', notes: [0,2,4,6,7,9,11], target: 3 },
  mixolydian: { label: 'Mixolydian', notes: [0,2,4,5,7,9,10], target: 6 },
  'natural-minor': { label: 'Natural minor (Aeolian)', notes: [0,2,3,5,7,8,10], target: 5 },
  locrian: { label: 'Locrian', notes: [0,1,3,5,6,8,10], target: 4 },
  blues: { label: 'Minor blues', notes: [0,3,5,6,7,10], target: -1 },
  'harmonic-minor': { label: 'Harmonic minor', notes: [0,2,3,5,7,8,11], target: 5 },
  turkish: { label: 'Turkish (custom)', notes: [0,1,3,5,7,10,11], target: -1 },
  indian: { label: 'Hon-kumoi-joshi', notes: [0,1,5,7,8], target: -1 },
  'harmonic-major': { label: 'Harmonic major', notes: [0,2,4,5,7,8,11], target: 5 },
  whiskey: { label: 'Whiskey (custom)', notes: [0,3,5,6,7,10,11], target: -1 },
  'double-harmonic': { label: 'Double harmonic', notes: [0,1,4,5,7,8,11], target: 1 },
  'pentatonic-major': { label: 'Pentatonic major', notes: [0,2,4,7,9], target: -1 },
  'pentatonic-minor': { label: 'Pentatonic minor', notes: [0,3,5,7,10], target: -1 },
  'hungarian-minor': { label: 'Hungarian minor', notes: [0,2,3,6,7,8,11], target: 3 },
} as const;
export type ScaleMode = keyof typeof SCALE_DEFINITIONS;
export const SCALE_MODES = Object.keys(SCALE_DEFINITIONS) as ScaleMode[];
export function isScaleMode(value: unknown): value is ScaleMode {
  return typeof value === 'string' && Object.hasOwn(SCALE_DEFINITIONS, value);
}
export function scaleNotes(scale: ScaleMode): readonly number[] { return SCALE_DEFINITIONS[scale].notes; }

/** The progression planners use seven harmonic slots. Missing diatonic degrees in
 * smaller collections share a nearby scale tone; these are aliases, not extra scale
 * members. Blues' chromatic passing tone remains available to melody and ornaments.
 */
export function harmonyScaleNotes(scale: ScaleMode): readonly number[] {
  switch (scale) {
    case 'pentatonic-major': return [0,2,4,4,7,9,9];
    case 'pentatonic-minor': return [0,3,3,5,7,10,10];
    case 'blues': return [0,3,3,5,7,10,10];
    case 'indian': return [0,1,5,5,7,8,8];
    default: return scaleNotes(scale);
  }
}
export function modeTargetPitch(scale: ScaleMode): number {
  const target = SCALE_DEFINITIONS[scale].target;
  return target < 0 ? -1 : scaleNotes('major')[target];
}
/** Map relative scale position between collections of any cardinality. Preserve
 * octave and rests; optional snapping considers adjacent octaves at the boundary.
 */
export function convertScalePitch(pitch: number, from: readonly number[], to: readonly number[], snap = false): number {
  if (pitch < 0) return pitch;
  const pc = pitch % 12, index = from.indexOf(pc);
  if (index >= 0) return pitch - from[index] + to[Math.floor(to.length * index / from.length)];
  if (!snap || to.includes(pc)) return pitch;
  let closest = to[0];
  for (const shift of [-12,0,12]) for (const note of to) {
    const candidate = note + shift;
    if (Math.abs(pc - candidate) < Math.abs(pc - closest)) closest = candidate;
  }
  return pitch - pc + closest;
}
