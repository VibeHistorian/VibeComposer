import type { ArrangedPart, ArrangementSection } from '../project/project.model';
import { JavaRandom } from './java-random';

const ARRANGEMENT_PART_SEED_OFFSETS: Readonly<Record<ArrangedPart, number>> = {
  bass: 50,
  chords: 100,
  arpeggio: 200,
  drums: 300,
};

/**
 * Decide whether a generated part enters a section using the Java planner's
 * per-role seed offsets and bounded nextInt(100) chance roll.
 */
export function shouldGeneratePartInSection(
  projectSeed: bigint | number,
  section: ArrangementSection,
  part: ArrangedPart,
): boolean {
  if (typeof projectSeed === 'number' && !Number.isSafeInteger(projectSeed)) {
    throw new RangeError('Numeric seeds must be safe integers; use bigint for 64-bit seeds.');
  }
  const javaIntSeed = BigInt.asIntN(32, BigInt(projectSeed));
  const partSeed = BigInt.asIntN(32, javaIntSeed + BigInt(ARRANGEMENT_PART_SEED_OFFSETS[part]));
  const chanceRoll = new JavaRandom(partSeed).nextInt(100);
  return section.parts[part] && chanceRoll < section.partChances[part];
}
