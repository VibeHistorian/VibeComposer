import type { ArrangedPart, ArrangementSection, CompositionTrack } from '../project/project.model';
import { JavaRandom } from './java-random';

const ARRANGEMENT_PART_SEED_OFFSETS: Readonly<Record<ArrangedPart, number>> = {
  melody: 0,
  bass: 50,
  chords: 100,
  arpeggio: 200,
  drums: 300,
};

/** Roll and materialize per-track presence once from app-level section type chances. */
export function generateTrackPresence(
  tracks: readonly CompositionTrack[],
  chances: Readonly<Record<ArrangedPart, number>>,
  random: () => number = Math.random,
): Record<string, boolean> {
  return Object.fromEntries(tracks.map((track) => [track.id, random() * 100 < chances[track.role]]));
}

/** Current arrangement state is stored explicitly and does not reroll during rendering. */
export function shouldGenerateTrackInSection(
  section: ArrangementSection,
  track: { readonly id: string },
): boolean {
  return section.trackPresence[track.id] ?? false;
}

/** Preserve the former seeded chance result while migrating project files that stored chances per section. */
export function legacyTrackPresence(
  projectSeed: bigint | number,
  track: { readonly id: string; readonly role: ArrangedPart },
  included: boolean,
  chance: number,
): boolean {
  if (!included) return false;
  if (typeof projectSeed === 'number' && !Number.isSafeInteger(projectSeed)) {
    throw new RangeError('Numeric seeds must be safe integers; use bigint for 64-bit seeds.');
  }
  const primaryId = `track-${track.role}-1`;
  const idOffset = track.id === primaryId ? 0 : stableIdOffset(track.id);
  let trackSeed = BigInt(projectSeed);
  if (track.id !== primaryId) {
    let hash = 0xcbf29ce484222325n;
    for (let index = 0; index < track.id.length; index++) {
      hash ^= BigInt(track.id.charCodeAt(index));
      hash = BigInt.asUintN(64, hash * 0x100000001b3n);
    }
    trackSeed = BigInt.asIntN(64, trackSeed ^ hash);
  }
  const seed = BigInt.asIntN(32, trackSeed);
  const partSeed = BigInt.asIntN(32, seed + BigInt(ARRANGEMENT_PART_SEED_OFFSETS[track.role] + idOffset));
  return new JavaRandom(partSeed).nextInt(100) < chance;
}

function stableIdOffset(value: string): number {
  let hash = 0x811c9dc5;
  for (let index = 0; index < value.length; index++) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash | 0;
}
