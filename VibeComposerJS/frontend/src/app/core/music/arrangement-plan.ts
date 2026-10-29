import type { SectionType } from '../project/arrangement.model';
import { JavaRandom } from './java-random';

/** Angular musical form: grow anticipation, release, recover, then return stronger.
 * Structure owns a stream independent of orchestration and phrase generation.
 */
export function generateArrangementPlan(seed: bigint): { type: SectionType }[] {
  const random = new JavaRandom(seed);
  // Most songs establish quietly; others open with a hook before settling into a verse.
  const openings: readonly SectionType[] = ['INTRO', 'INTRO', 'INTRO', 'HALF_CHORUS', 'CHORUS2'];
  const types: SectionType[] = [openings[random.nextInt(openings.length)]];
  const cycles = 2 + random.nextInt(2);
  for (let cycle = 0; cycle < cycles; cycle++) {
    types.push(cycle === 0 ? 'VERSE1' : random.nextBoolean() ? 'VERSE2' : 'CHILL');
    if (random.nextBoolean()) types.push('VERSE3');
    types.push('BUILDUP1');
    if (random.nextBoolean() || cycle === cycles - 1) types.push('BUILDUP2');
    types.push(cycle === cycles - 1 ? 'CLIMAX' : cycle === 0 ? 'CHORUS2' : 'CHORUS3');
    if (random.nextBoolean()) types.push(cycle === cycles - 1 ? 'CLIMAX' : 'CHORUS3');
    if (cycle < cycles - 1) types.push('BREAKDOWN');
  }
  types.push('HALF_CHORUS', 'OUTRO');
  return types.map(type => ({ type }));
}
