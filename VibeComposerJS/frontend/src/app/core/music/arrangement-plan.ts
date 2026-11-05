import type { SectionType } from '../project/arrangement.model';
import { JavaRandom } from './java-random';

/** Angular musical form: grow anticipation, release, recover, then return stronger.
 * Structure owns a stream independent of orchestration and phrase generation.
 */
export function generateArrangementPlan(seed: bigint): { type: SectionType }[] {
  const random = new JavaRandom(seed);
  // Most songs establish quietly; others open with a hook before settling into a verse.
  const openings: readonly SectionType[] = ['INTRO', 'INTRO', 'INTRO', 'HALF_CHORUS', 'CHORUS2'];
  const opening = openings[random.nextInt(openings.length)];
  // Taking the lower of two uniform draws gives lengths 8–20 decreasing weights 25, 23, …, 1.
  const length = 8 + Math.min(random.nextInt(13), random.nextInt(13));
  const cycles = length >= 14 ? 3 : 2;
  const slots: { type: SectionType; optional: boolean }[] = [{ type: opening, optional: false }];
  const add = (type: SectionType, optional = false) => slots.push({ type, optional });
  for (let cycle = 0; cycle < cycles; cycle++) {
    const last = cycle === cycles - 1;
    const release = last ? 'CLIMAX' : cycle === 0 ? 'CHORUS2' : 'CHORUS3';
    add(cycle === 0 ? 'VERSE1' : random.nextBoolean() ? 'VERSE2' : 'CHILL');
    add('VERSE3', true);
    add('BUILDUP1', last);
    add('BUILDUP2', !last);
    add(release);
    add(last ? 'CLIMAX' : 'CHORUS3', true);
    add(last ? 'HALF_CHORUS' : 'BREAKDOWN', true);
  }
  add('OUTRO');
  // Select additions before rendering so every size retains its full musical arc.
  const optional = slots.filter(slot => slot.optional);
  for (let index = optional.length - 1; index > 0; index--) {
    const other = random.nextInt(index + 1);
    [optional[index], optional[other]] = [optional[other], optional[index]];
  }
  const selected = new Set(optional.slice(0, length - (slots.length - optional.length)));
  return slots.filter(slot => !slot.optional || selected.has(slot)).map(({ type }) => ({ type }));
}
