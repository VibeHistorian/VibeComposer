export interface MelodySkeletonNote { readonly midi: number; readonly rhythm: number }
export type MelodySkeleton = readonly (readonly MelodySkeletonNote[])[];

export const INFERENCE_CHORD_NAMES = ['C', 'Dm', 'Em', 'F', 'G', 'Am', 'Bdim'] as const;
const PITCHES = [[0,4,7],[2,5,9],[4,7,11],[5,9,0],[7,11,2],[9,0,4],[11,2,5]];
// Production HashMap iteration order, verified by the Java inference fixture.
const CHORD_ORDER = [0,3,4,2,1,6,5];
const CIRCLE = [0,3,6,2,5,1,4,0];

/** Production eighth-note weighting and rank-two/circle tie selection, before phrase repair. */
export function inferMelodyChordDegrees(skeleton: MelodySkeleton, durations: readonly number[] = skeleton.map(() => 4)): number[] {
  if (!skeleton.length || skeleton.length > 32 || !durations.length || durations.some(duration => !Number.isFinite(duration) || duration <= 0)
    || skeleton.some(notes => notes.some(note => !Number.isInteger(note.midi) || !Number.isFinite(note.rhythm) || note.rhythm < 0))) throw new RangeError('Invalid Melody skeleton.');
  let previous = -1;
  return skeleton.map((notes, index) => {
    const counts = new Map<number, number>();
    let time = 0;
    for (const note of notes) for (let duration = 0; duration < note.rhythm && time < durations[Math.min(index, durations.length - 1)]; duration += 0.5) {
      // Java remainder preserves the rest's negative class, which matches no chord.
      const pitch = note.midi % 12;
      counts.set(pitch, (counts.get(pitch) ?? 0) + 1); time += 0.5;
    }
    // Integer HashMap bins for classes -11..11: negative values spread to (-pitch-1).
    const bucket = (pitch: number) => pitch < 0 ? -pitch - 1 : pitch;
    const top = [...counts].sort((a,b) => bucket(a[0]) - bucket(b[0]))
      .sort((a,b) => b[1] - a[1]).slice(0,4);
    let ranked = CHORD_ORDER.map(chord => ({ chord, count: top.reduce((sum,[pitch,count]) => sum + (PITCHES[chord].includes(pitch) ? count : 0), 0) }))
      .sort((a,b) => b.count - a.count).slice(0,5);
    const bestCount = ranked.filter(candidate => candidate.count === ranked[0].count).length;
    let chosen: number | undefined;
    const circleIndex = CIRCLE.indexOf(previous);
    if (circleIndex >= 0 && bestCount > 1) {
      if (bestCount >= 2) ranked = ranked.filter(candidate => candidate.count === ranked[0].count);
      const successor = CIRCLE[circleIndex + 1];
      if (ranked.some(candidate => candidate.chord === successor)) chosen = successor;
    }
    previous = chosen ?? ranked[Math.min(1, ranked.length - 1)].chord;
    return previous + 1;
  });
}
