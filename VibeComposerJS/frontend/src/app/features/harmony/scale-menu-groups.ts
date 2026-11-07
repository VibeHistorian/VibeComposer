import type { SelectMenuGroup } from '../../shared/menu-select.component';

export const SCALE_MENU_GROUPS: readonly SelectMenuGroup[] = [
  { id: 'diatonic', label: 'Diatonic modes', values: ['major', 'dorian', 'phrygian', 'lydian', 'mixolydian', 'natural-minor', 'locrian'] },
  { id: 'harmonic', label: 'Harmonic scales', values: ['harmonic-minor', 'harmonic-major', 'double-harmonic', 'hungarian-minor'] },
  { id: 'pentatonic', label: 'Pentatonic scales', values: ['pentatonic-major', 'pentatonic-minor'] },
  { id: 'other', label: 'Other scales', values: ['blues', 'turkish', 'indian', 'whiskey'] },
];
