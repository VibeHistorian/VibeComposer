import { CATALOGUE } from './melody-catalogue';
import { JavaRandom } from './java-random';
import { mixContextualSeed } from './contextual-harmony';

const EXTRA = [ [[0,1,2,3,2],[0,1,0,-1,-2]], [[0,1,0,-1,0],[0,0,-1,0]],
  [[0,1,0,-1],[0,-1,1,0]], [[0,1,2,1],[0,2,1,-1]], [[0,-1,1,-2,0],[0,1,-1,0,2]],
  [[0,2,1],[0,3,1]], [[0,0,0,0]] ];
const members = (family: number) => family === 2 || family === 3;
const orientation = (shape: readonly number[]) => Math.sign(shape.find(n => n !== 0) ?? 0);
const SHAPES = CATALOGUE.map((family,type) => {
  const shapes: number[][] = [];
  const add = (shape: readonly number[]) => {
    for (const sign of [1,-1]) {
      const value = shape.map(n => n * sign);
      if (!shapes.some(existing => existing.length === value.length && existing.every((n,i) => n === value[i]))) shapes.push(value);
    }
  };
  for (const original of family) add(Array.from({ length: Math.max(3,original.length) }, (_,i) => {
    const step = original[Math.min(i,original.length - 1)];
    return members(type) ? step === 7 ? 3 : Math.floor(step / 2 + 0.5) : step;
  }));
  EXTRA[type].forEach(add);
  return shapes;
});

/** ContextualBlockCatalogue.Evaluator. Guide identity is independent of search order. */
export class ContextualCatalogue {
  private readonly guides = new Map<string, { family: number; steps: number[] } | null>();
  private readonly spaces = new Map<string, number[]>();
  constructor(private readonly seed: bigint, private readonly newChance: number) {}
  affinity(gesture: readonly number[], chord: ReadonlySet<number>, scale: ReadonlySet<number>, weights: readonly number[],
    motif: number, phase: number, direction: number): number {
    const segment = Math.floor(phase / 4), key = `${motif}/${segment}/${direction}/${weights}`;
    if (!this.guides.has(key)) {
      const random = new JavaRandom(mixContextualSeed(this.seed ^ BigInt(Math.abs(motif)) * 0x9e3779b97f4a7c15n ^ BigInt(segment) * 0xd1b54a32d192ed03n));
      const fresh = random.nextInt(100) < this.newChance, total = weights.reduce((sum,w) => sum+w,0);
      let guide: { family: number; steps: number[] } | null = null;
      if (total > 0) {
        let roll = random.nextInt(total), family = 6;
        for (let i = 0; i < weights.length; i++) if ((roll -= weights[i]) < 0) { family = i; break; }
        const eligible = SHAPES[family].filter(shape => !direction || !orientation(shape) || orientation(shape) === direction);
        let steps = [...eligible[random.nextInt(eligible.length)]];
        if (fresh) {
          if (!orientation(steps)) steps = [0,0,1,0];
          else switch (random.nextInt(3)) {
            case 0: { const end = steps.at(-1)!; steps = [...steps].reverse().map(n => n - end); break; }
            case 1: { const rotate = 1 + random.nextInt(steps.length - 1), source = steps;
              steps = source.map((_,i) => source[(rotate+i) % source.length] - source[rotate]); break; }
            default: { const end = steps.at(-1)!; steps.push(end + Math.sign(end),end); }
          }
        }
        if (orientation(steps) && direction && orientation(steps) !== direction) steps = steps.map(n => -n);
        guide = { family, steps };
      }
      this.guides.set(key,guide);
    }
    const guide = this.guides.get(key);
    if (!guide || gesture.length < 2) return 0;
    const classes = members(guide.family) ? chord : scale, spaceKey = [...classes].sort((a,b) => a-b).join(',');
    let pitches = this.spaces.get(spaceKey);
    if (!pitches) { pitches = Array.from({ length: 128 },(_,i) => i).filter(p => classes.has(p % 12)); this.spaces.set(spaceKey,pitches); }
    const position = pitches.indexOf(gesture[0]);
    if (position < 0) return 0;
    const offset = phase % 4, origin = guide.steps[offset % guide.steps.length];
    let distance = 0;
    for (let i = 1; i < gesture.length; i++) {
      const index = position + guide.steps[(offset+i) % guide.steps.length] - origin;
      if (index < 0 || index >= pitches.length) return 0;
      distance += Math.abs(gesture[i] - pitches[index]);
    }
    return Math.max(0,3 - distance * 0.45);
  }
}
