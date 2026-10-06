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

/** Stable per-track entrance decisions, preserving the original Java stream for the first role track. */
export function shouldGenerateTrackInSection(
  projectSeed: bigint | number,
  section: ArrangementSection,
  track: { readonly id: string; readonly role: ArrangedPart },
): boolean {
  if (typeof projectSeed === 'number' && !Number.isSafeInteger(projectSeed)) {
    throw new RangeError('Numeric seeds must be safe integers; use bigint for 64-bit seeds.');
  }
  const explicitPresence = section.trackPresence?.[track.id];
  if (explicitPresence !== undefined) return explicitPresence;
  const included = section.trackParts?.[track.id] ?? section.parts[track.role];
  const chance = section.trackPartChances?.[track.id] ?? section.partChances[track.role];
  const seed = BigInt.asIntN(32, BigInt(projectSeed));
  const initialTrackId = `track-${track.role}-1`;
  const idOffset = track.id === initialTrackId ? 0 : stableIdOffset(track.id);
  const partSeed = BigInt.asIntN(32, seed + BigInt(ARRANGEMENT_PART_SEED_OFFSETS[track.role] + idOffset));
  return included && new JavaRandom(partSeed).nextInt(100) < chance;
}

function stableIdOffset(value: string): number {
  let hash = 0x811c9dc5;
  for (let index = 0; index < value.length; index++) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash | 0;
}
