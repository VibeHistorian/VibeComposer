import type { TrackPreset } from './track-presets';
import type { CompositionTrack } from './track.model';
import type { PartSettingsByRole } from '../music/part-settings.model';
import type { ArpeggioPattern } from '../music/arpeggio-pattern';

interface Style {
  name: string;
  bpm: string;
  swing: number;
  programs: readonly [number, number, number, number];
  bass: string;
  comp: string;
  kick: string;
  snare: string;
  hat: string;
  durations: readonly number[];
  arp: ArpeggioPattern;
  gate: number;
  variation: number;
  strum?: number;
}

// All masks describe a four-beat bar. Twelve cells provide true triplet grids.
const STYLES: readonly Style[] = [
  { name: 'Deep House', bpm: '118–124', swing: 54, programs: [81, 38, 4, 89], bass: '0010001000100010', comp: '0010001000100010', kick: '1000100010001000', snare: '0000100000001000', hat: '0010001000100010', durations: [0.5, -0.5, 1, -1, 1], arp: 'up-down', gate: 65, variation: 8 },
  { name: 'Techno', bpm: '128–140', swing: 50, programs: [80, 38, 81, 80], bass: '1010101010101010', comp: '1000001000100000', kick: '1000100010001000', snare: '0000100000001000', hat: '1011101010111010', durations: [0.25, 0.25, -0.5, 0.5, -0.5, 1, -1], arp: 'up', gate: 40, variation: 0 },
  { name: 'Trance', bpm: '134–142', swing: 50, programs: [81, 38, 89, 81], bass: '0111011101110111', comp: '1000000010000000', kick: '1000100010001000', snare: '0000100000001000', hat: '0010001000100010', durations: [0.5, 0.5, 1, 0.5, 0.5, 1], arp: 'up-down', gate: 85, variation: 5 },
  { name: 'Synthwave', bpm: '90–110', swing: 50, programs: [81, 38, 90, 80], bass: '1010101010101010', comp: '1000000010000000', kick: '1000000010100000', snare: '0000100000001000', hat: '1010101010101010', durations: [1, 0.5, 0.5, -1, 1], arp: 'down-up', gate: 95, variation: 8 },
  { name: 'Disco', bpm: '110–126', swing: 52, programs: [62, 34, 4, 10], bass: '1010011010100110', comp: '0010001000100010', kick: '1000100010001000', snare: '0000100000001000', hat: '1111111111111111', durations: [-0.5, 0.5, 0.5, 0.5, 1, 1], arp: 'frog-up', gate: 55, variation: 35 },
  { name: 'Funk', bpm: '95–112', swing: 56, programs: [65, 36, 27, 11], bass: '1001001010010110', comp: '0010010000100101', kick: '1000001010000010', snare: '0000100000001000', hat: '1110101111101011', durations: [0.25, -0.25, 0.5, -0.5, 0.5, 1, -1], arp: 'frog-down', gate: 40, variation: 45 },
  { name: 'Boom Bap', bpm: '82–96', swing: 62, programs: [65, 33, 4, 11], bass: '1000001000100000', comp: '1000000000100000', kick: '1000000010010000', snare: '0000100000001000', hat: '1010101010101010', durations: [0.5, -0.5, 0.5, 0.5, -1, 1], arp: 'down', gate: 80, variation: 18 },
  { name: 'Lo-fi Hip Hop', bpm: '68–84', swing: 60, programs: [11, 32, 4, 8], bass: '1000000010000010', comp: '1000000000100000', kick: '1000000010000010', snare: '0000100000001000', hat: '1010001010100010', durations: [1, -0.5, 0.5, 1, -1], arp: 'random', gate: 110, variation: 20 },
  { name: 'Trap', bpm: '130–150 half-time', swing: 50, programs: [80, 38, 88, 10], bass: '1000000000100010', comp: '1000000000000000', kick: '1000001000100001', snare: '0000000010000000', hat: '1111101011111011', durations: [0.25, 0.25, -0.5, 1, -1, 1], arp: 'down', gate: 70, variation: 0 },
  { name: 'Drum and Bass', bpm: '168–176', swing: 50, programs: [81, 39, 89, 80], bass: '1000001010000100', comp: '1000000000100000', kick: '1000000000100000', snare: '0000100000001000', hat: '1011101010111010', durations: [0.5, 0.5, -1, 0.5, 0.5, 1], arp: 'up-down', gate: 65, variation: 12 },
  { name: 'UK Garage', bpm: '128–136', swing: 64, programs: [11, 38, 4, 10], bass: '1000010010100001', comp: '0010010000100100', kick: '1000000000100000', snare: '0000100000001000', hat: '0010100100101001', durations: [-0.5, 0.5, 0.25, 0.25, -0.5, 1, -1], arp: 'frog-up', gate: 45, variation: 20 },
  { name: 'Dubstep', bpm: '138–144 half-time', swing: 50, programs: [81, 39, 91, 80], bass: '1000010010000010', comp: '1000000000001000', kick: '1000000000000010', snare: '0000000010000000', hat: '1010101110101011', durations: [1, -1, 0.5, 0.5, -1], arp: 'down-up', gate: 50, variation: 5 },
  { name: 'Reggae', bpm: '70–90', swing: 54, programs: [65, 33, 27, 16], bass: '1000001010000100', comp: '0010001000100010', kick: '0000000010000000', snare: '0000000010000000', hat: '1010101010101010', durations: [-0.5, 0.5, 1, -0.5, 0.5, 1], arp: 'up', gate: 35, variation: 15 },
  { name: 'Dub', bpm: '65–82', swing: 55, programs: [16, 33, 16, 98], bass: '1000001000001000', comp: '0010000000100000', kick: '0000000010000000', snare: '0000000010000000', hat: '0010001000100010', durations: [1, -1, 0.5, -1.5], arp: 'down', gate: 45, variation: 8 },
  { name: 'Afrobeat', bpm: '100–118', swing: 50, programs: [56, 34, 27, 12], bass: '1001001000101000', comp: '0010010010010010', kick: '1000001010000010', snare: '0000100000001000', hat: '1011010110101101', durations: [0.5, -0.5, 1, -1, 1], arp: 'frog-up', gate: 55, variation: 30 },
  { name: 'Bossa Nova', bpm: '120–144', swing: 50, programs: [73, 32, 24, 11], bass: '1000001000000010', comp: '1001001000100100', kick: '1000001010000010', snare: '0001000000100100', hat: '1010101010101010', durations: [0.75, 0.25, -0.5, 0.5, 1, 1], arp: 'down-up', gate: 80, variation: 25, strum: 25 },
  { name: 'Samba', bpm: '96–116', swing: 50, programs: [73, 32, 24, 12], bass: '1000001010000010', comp: '1001010010010010', kick: '1000001010000010', snare: '0010010000100100', hat: '1111111111111111', durations: [0.5, 0.25, 0.25, -0.5, 0.5, 1, 1], arp: 'frog-down', gate: 50, variation: 30, strum: 20 },
  { name: 'Swing Jazz', bpm: '110–160', swing: 67, programs: [66, 32, 0, 11], bass: '1000100010001000', comp: '1000001000000010', kick: '1000000000000000', snare: '0000100000001000', hat: '100101100101', durations: [0.5, 0.5, 1, -0.5, 0.5, 1], arp: 'random', gate: 85, variation: 65 },
  { name: 'Blues Shuffle', bpm: '90–120', swing: 67, programs: [26, 33, 0, 16], bass: '101101101101', comp: '100100100100', kick: '100000100000', snare: '000100000100', hat: '101101101101', durations: [1, 0.5, 0.5, 1, 1], arp: 'up-down', gate: 70, variation: 40, strum: 35 },
  { name: 'Indie Rock', bpm: '110–140', swing: 50, programs: [27, 34, 25, 10], bass: '1010101010101010', comp: '1011101010111010', kick: '1000000010100000', snare: '0000100000001000', hat: '1010101010101010', durations: [1, 0.5, 0.5, 1, -1], arp: 'down-up', gate: 80, variation: 10, strum: 45 },
  { name: 'Pop Punk', bpm: '160–190', swing: 50, programs: [29, 34, 30, 27], bass: '1010101010101010', comp: '1010101010101010', kick: '1000001010000010', snare: '0000100000001000', hat: '1010101010101010', durations: [0.5, 0.5, 1, 1, 1], arp: 'up', gate: 65, variation: 0, strum: 15 },
  { name: 'Acoustic Folk', bpm: '88–112', swing: 52, programs: [73, 32, 25, 24], bass: '1000000010000000', comp: '1000101010001010', kick: '1000000010000000', snare: '0000100000001000', hat: '1000100010001000', durations: [1, 1, 0.5, 0.5, 1], arp: 'frog-up', gate: 95, variation: 12, strum: 65 },
  { name: 'Ambient', bpm: '55–80', swing: 50, programs: [88, 38, 89, 98], bass: '1000000000000000', comp: '1000000000000000', kick: '1000000000000000', snare: '0000000000001000', hat: '0000001000000010', durations: [2, -1, 1], arp: 'random', gate: 160, variation: 0 },
  { name: 'Cinematic Pulse', bpm: '90–120', swing: 50, programs: [60, 43, 48, 45], bass: '1010101010101010', comp: '1000000010000000', kick: '1000000010000010', snare: '0000000000001000', hat: '1010101010101010', durations: [1.5, 0.5, 1, 1], arp: 'up-down', gate: 90, variation: 5 },
];

function grid(mask: string) {
  return { rhythm: 'custom' as const, hitsPerPattern: mask.length,
    customPattern: Array.from({ length: 32 }, (_, index) => Number(mask[index] ?? 0)) };
}

function track<R extends keyof PartSettingsByRole>(role: R, name: string, index: number,
  program: number, generatorSettings: PartSettingsByRole[R], volumePercent = 80, panPercent = 0): CompositionTrack {
  return { id: `builtin-${role}-${index}`, role, name, midiChannel: { melody: 4, bass: 1, chords: 2, arpeggio: 3, drums: 10 }[role],
    generatorSettings, mix: { program, volumePercent, panPercent, muted: false, solo: false } } as CompositionTrack;
}

/** Factory presets stay separate from browser-owned libraries and never contain phrase data. */
export const BUILTIN_TRACK_PRESETS: readonly TrackPreset[] = STYLES.flatMap(style => {
  const common = { swingPercent: style.swing, velocityMin: 68, velocityMax: 100 };
  const bassCommon = { velocityMin: 68, velocityMax: 100 };
  const durations = [style.durations, ...Array.from({ length: 9 }, () => [] as number[])];
  const name = `${style.name} · ${style.bpm} BPM`;
  const tracks: CompositionTrack[][] = [
    [track('melody', 'Lead', 1, style.programs[0], { ...common, algorithm: 'block', rhythm: 'durations', customDurations: durations,
      speed: 50, maxBlockChange: 3, blockJump: 1, splitChance: 0, noteLengthMultiplier: Math.min(100, style.gate) }),
    track('melody', 'Quiet response', 2, style.programs[3], { ...common, algorithm: 'block', rhythm: 'durations',
      customDurations: [[-2, 1, 1], ...Array.from({ length: 9 }, () => [])], transpose: 12, patternSeed: 271, velocityMin: 45, velocityMax: 65 }, 45, 18)],
    [track('bass', 'Foundation', 1, style.programs[1], { ...bassCommon, ...grid(style.bass), noteVariation: style.variation, octaveInterval: false,
      patternJoinMode: 'NOJOIN', noteLengthMultiplier: Math.min(style.gate, 95) }),
    track('bass', 'Sub support', 2, 38, { ...bassCommon, rhythm: 'single', hitsPerPattern: 4, noteVariation: 0, octaveInterval: false,
      velocityMin: 50, velocityMax: 65, noteLengthMultiplier: 90 }, 40)],
    [track('chords', 'Comping', 1, style.programs[2], { ...common, ...grid(style.comp), voicing: 'close',
      noteLengthPercent: style.gate, strum: style.strum ?? 0, patternJoinMode: 'NOJOIN' }, 70, -15),
    track('chords', 'Sustained harmony', 2, style.programs[2], { ...common, rhythm: 'single', hitsPerPattern: 4,
      voicing: 'open', noteLengthPercent: 160, velocityMin: 45, velocityMax: 60 }, 40, 15)],
    [track('arpeggio', 'Moving figure', 1, style.programs[3], { ...common, pattern: style.arp, rate: 'sixteenth', octaves: 2,
      ...grid(style.comp), noteLengthMultiplier: Math.min(style.gate, 90) }, 60, 20),
    track('arpeggio', 'Answer figure', 2, style.programs[3], { ...common, pattern: 'down', rate: 'eighth', octaves: 1,
      rhythm: 'durations', customDurations: durations, transpose: 12, velocityMin: 45, velocityMax: 65 }, 40, -20)],
    [track('drums', 'Kick', 1, 0, { ...common, ...grid(style.kick), pitch: 36 }),
    track('drums', 'Snare / rim', 2, 0, { ...common, ...grid(style.snare), pitch: /Bossa|Reggae|Dub$/.test(style.name) ? 37 : 38 }),
    track('drums', 'Hat / ride', 3, 0, { ...common, ...grid(style.hat), pitch: style.name === 'Swing Jazz' ? 53 : 42,
      velocityMin: 45, velocityMax: 75 }, 65, 12)],
  ];
  return tracks.map(group => ({ name, role: group[0].role, tracks: group }));
});



