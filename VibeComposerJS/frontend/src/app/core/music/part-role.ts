export type ArrangedPart = 'melody' | 'bass' | 'chords' | 'arpeggio' | 'drums';

/** Explicit Java group correspondence, independent of track IDs and seed derivation. */
export const PART_TYPES: Readonly<Record<ArrangedPart, number>> = { melody: 0, bass: 1, chords: 2, arpeggio: 3, drums: 4 };

export const ARRANGED_PARTS: readonly ArrangedPart[] = ['melody', 'chords', 'arpeggio', 'bass', 'drums'];

/** All five roles have active phrase consumers. */
export const PART_GENERATION_AVAILABLE: Readonly<Record<ArrangedPart, boolean>> = {
  melody: true, bass: true, chords: true, arpeggio: true, drums: true,
};

export function tracksInRoleOrder<T extends { readonly role: ArrangedPart }>(tracks: readonly T[]): T[] {
  return [...tracks].sort((left, right) => ARRANGED_PARTS.indexOf(left.role) - ARRANGED_PARTS.indexOf(right.role));
}
