import { DRUM_INSTRUMENTS } from '../../core/music/drum-instruments';
import type { SelectMenuGroup } from '../../shared/menu-select.component';

export const INSTRUMENT_MENU_GROUPS: readonly SelectMenuGroup[] = [
  'Piano', 'Chromatic percussion', 'Organ', 'Guitar', 'Bass', 'Strings', 'Ensemble', 'Brass',
  'Reed', 'Pipe', 'Synth lead', 'Synth pad', 'Synth effects', 'Ethnic', 'Percussive', 'Sound effects',
].map((label, family) => ({ id: String(family), label, values: Array.from({ length: 8 }, (_, index) => String(family * 8 + index)) }));

export const DRUM_MENU_GROUPS: readonly SelectMenuGroup[] = [
  { id: 'kicks', label: 'Kicks', values: ['35', '36'] },
  { id: 'snares', label: 'Snare, clap and stick', values: ['37', '38', '39', '40'] },
  { id: 'hats', label: 'Hi-hats', values: ['42', '44', '46'] },
  { id: 'toms', label: 'Toms', values: ['41', '47'] },
  { id: 'cymbals', label: 'Cymbals', values: ['53'] },
  { id: 'other', label: 'Other percussion', values: ['54', '60', '61', '82'] },
].map(group => ({ ...group, values: DRUM_INSTRUMENTS.filter(instrument => instrument.family === group.id).map(instrument => String(instrument.midi)) }));
