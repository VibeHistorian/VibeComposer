import type { SectionType } from '../project/arrangement.model';
import { JavaRandom } from './java-random';

const TEMPLATES: readonly (readonly SectionType[])[] = [
  ['INTRO', 'CHORUS1', 'CHORUS2', 'BREAKDOWN', 'CHILL', 'CHORUS3', 'CLIMAX', 'CLIMAX', 'OUTRO'],
  ['INTRO', 'BUILDUP1', 'CHORUS2', 'CHORUS3', 'VERSE1', 'VERSE2', 'CHORUS3', 'CLIMAX', 'OUTRO'],
  ['INTRO', 'BUILDUP1', 'CHORUS2', 'CHORUS3', 'VERSE1', 'BREAKDOWN', 'CHILL', 'BUILDUP1', 'BUILDUP2', 'CHORUS3', 'CLIMAX', 'BREAKDOWN', 'CHILL', 'CLIMAX', 'CLIMAX', 'HALF_CHORUS', 'OUTRO'],
  ['HALF_CHORUS', 'HALF_CHORUS', 'OUTRO', 'VERSE2', 'CHORUS2', 'VERSE2', 'CHORUS3', 'BREAKDOWN', 'BUILDUP1', 'BUILDUP2', 'CHORUS3', 'CLIMAX', 'OUTRO'],
];
const REPLACEMENTS: Partial<Record<SectionType, readonly SectionType[]>> = {
  INTRO: ['HALF_CHORUS', 'CHORUS1', 'BREAKDOWN', 'OUTRO'], CHORUS1: ['VERSE2'],
  CHORUS2: ['CHORUS3'], CHILL: ['VERSE2'], BREAKDOWN: ['OUTRO'],
};
const INSERTIONS: Partial<Record<SectionType, readonly SectionType[]>> = {
  INTRO: ['VERSE1', 'VERSE2', 'BUILDUP1'], CHORUS1: ['VERSE2'], CHORUS2: ['CHORUS3'],
  CHILL: ['VERSE2', 'BUILDUP1', 'BUILDUP2'],
};
const VARIABLE: readonly SectionType[] = ['VERSE2', 'CHORUS2', 'CLIMAX', 'CHILL', 'OUTRO'];

/** Arrangement.randomizeFully with Java GUI defaults (12, 50, 30, 2, 4, 15).
 * As in Java, 12 limits insertion opportunities, not template length or repeats.
 * Structure uses its own stream; presence never changes these random draws.
 */
export function generateArrangementTemplate(seed: bigint): { type: SectionType; chanceBoost: number }[] {
  const random = new JavaRandom(seed);
  const skeleton = TEMPLATES[random.nextInt(TEMPLATES.length)].map(type => {
    const replace = random.nextInt(100) < 50;
    const options = REPLACEMENTS[type];
    return replace && options ? options[random.nextInt(options.length)] : type;
  });
  const full: SectionType[] = [];
  let inserts = 0;
  skeleton.forEach((type, index) => {
    full.push(type);
    const options = INSERTIONS[type];
    if (options && inserts < 4 && full.length + skeleton.length - index < 12) {
      let count = 0;
      while (count < 2 && random.nextInt(100) < 30) {
        full.push(options[random.nextInt(options.length)]);
        count++;
        inserts++;
      }
    }
  });
  const result: { type: SectionType; chanceBoost: number }[] = [];
  let last: SectionType | undefined;
  for (const type of full) {
    if (type === last && type !== 'CLIMAX') continue;
    result.push({ type, chanceBoost: 0 });
    if (VARIABLE.includes(type) && random.nextInt(100) < 15) result.push({ type, chanceBoost: 10 });
    last = type;
  }
  return result;
}
