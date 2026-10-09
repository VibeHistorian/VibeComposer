import type { ChordSettings } from '../project/project.model';

/** Midpoint of the legacy default velocity bounds (69–89). */
export const DEFAULT_CUSTOM_VELOCITIES: readonly number[] = Object.freeze(Array(32).fill(79));

export function isVelocityPattern(value: unknown): value is readonly number[] {
  return Array.isArray(value) && value.length === 32
    && Array.from(value).every((slot) => Number.isInteger(slot) && slot >= 0 && slot <= 127);
}

/** Java ChordPhraseGenerator takes the first Hits velocities without applying rhythm rotation/flip. */
export function chordVelocityPattern(settings: Pick<ChordSettings,
  'hitsPerPattern' | 'useCustomVelocities' | 'customVelocities'>): readonly number[] | undefined {
  const enabled = settings.useCustomVelocities ?? false;
  const grid = settings.customVelocities;
  const hits = settings.hitsPerPattern ?? 8;
  if (typeof enabled !== 'boolean' || !Number.isInteger(hits) || hits < 1 || hits > 32
    || (grid !== undefined && !isVelocityPattern(grid))) {
    throw new RangeError('Custom velocity settings are outside the supported range.');
  }
  return enabled ? (grid ?? DEFAULT_CUSTOM_VELOCITIES).slice(0, hits) : undefined;
}
