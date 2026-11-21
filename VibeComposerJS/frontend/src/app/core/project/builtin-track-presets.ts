import type { TrackPreset } from './track-presets';
import type { CompositionTrack } from './track.model';
import type { PartSettingsByRole } from '../music/part-settings.model';
import type { ArpeggioPattern } from '../music/arpeggio-pattern';
import { drumInstrumentName } from '../music/drum-instruments';
import { builtinStyleSettings, type BuiltinStyleName } from './builtin-track-style-settings';

interface Style {
  name: BuiltinStyleName;
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

// Additional voices use the full GM catalogue, with sparse accents around the core groove.
// Each | separates four beats at the same subdivision.
const PERCUSSION: Readonly<Record<string, readonly (readonly [pitch: number, mask: string])[]>> = {
  'Deep House': [[46, '0010001000100010'], [63, '0001000000010010|0001000000010110'], [69, '1000100010001000']],
  'Techno': [[46, '0010001000100010'], [52, '1000000000000000'], [50, '0000000000000010|0000000000001011']],
  'Trance': [[46, '0010001000100010'], [49, '1000000000000000'], [57, '00001000|00000000|00000000|00000001']],
  'Synthwave': [[46, '0000001000000010'], [49, '1000000000000000'], [45, '0000000000001010|0000000000011011']],
  'Disco': [[46, '0010001000100010'], [56, '1000001000100000|1000001000100010'], [70, '1010101010101010']],
  'Funk': [[46, '0000001000000010'], [56, '1001000000100100'], [63, '0001001000010010']],
  'Boom Bap': [[44, '0000001000000010'], [51, '1000000000000000'], [69, '0010001000100010']],
  'Lo-fi Hip Hop': [[44, '0000001000000010'], [59, '10000000|00000000|00000010|00000000'], [70, '0010000000100000']],
  'Trap': [[46, '0000001000000010'], [55, '1000000000000000'], [76, '0000000000010000|0000000000010111']],
  'Drum and Bass': [[46, '0000001000000010'], [51, '1000100010001000'], [57, '1000000000000000']],
  'UK Garage': [[46, '0000001000000010'], [62, '0001000000010010'], [75, '0000000100000100']],
  'Dubstep': [[46, '0000001000000010'], [52, '1000000000000000'], [50, '0000000000001010']],
  'Reggae': [[46, '0000001000000010'], [64, '0001001000010010|0001001000010110'], [73, '0010001000100010']],
  'Dub': [[59, '0000000010000000'], [64, '0001000000000010'], [58, '10000000|00000000|00000000|00000010']],
  'Afrobeat': [[56, '1001001000101000|1001001001001010'], [63, '0010010001000010'], [64, '0001001000010100'], [67, '1000001000100000']],
  'Bossa Nova': [[69, '1010101010101010'], [75, '1001001000101000|0010100010010010'], [63, '0000001000000010']],
  'Samba': [[67, '1001001000101000'], [64, '1000001010000010'], [74, '0010010000100100|0010010000101110'], [78, '0001000000010000']],
  'Swing Jazz': [[44, '000100000100'], [51, '100101100101|100101100111'], [55, '100000000000']],
  'Blues Shuffle': [[46, '001000001000'], [51, '100101100101'], [45, '000000000010|000000001011']],
  'Indie Rock': [[46, '0000001000000010'], [49, '1000000000000000'], [48, '0000000000001000|0000000000111010']],
  'Pop Punk': [[46, '0010001000100010'], [57, '1000000010000000'], [50, '0000000000001000|0000000000001111']],
  'Acoustic Folk': [[70, '1010101010101010'], [77, '0000001000000010'], [81, '10000000|00000000|00000000|00000100']],
  'Ambient': [[59, '10000000|00000000|00000000|00001000'], [80, '0000000010000000'], [74, '0000000000001000']],
  'Cinematic Pulse': [[43, '1000000010000000'], [50, '0000001000000010'], [52, '1000000000000000'], [57, '00001000|00000000|00000000|00000011']],
};

// Each mask segment describes a four-beat bar. Twelve cells provide a triplet grid.
const STYLES: readonly Style[] = [
  { name: 'Deep House', bpm: '118–124', swing: 54, programs: [81, 38, 4, 89], bass: '0010001000100010', comp: '0010001000100010', kick: '1000100010001000', snare: '0000100000001000', hat: '0010001000100010', durations: [0.5, -0.5, 1, -1, 1], arp: 'up-down', gate: 65, variation: 8 },
  { name: 'Techno', bpm: '128–140', swing: 50, programs: [80, 38, 81, 80], bass: '1010101010101010', comp: '1000001000100000', kick: '1000100010001000', snare: '0000100000001000', hat: '1011101010111010', durations: [0.25, 0.25, -0.5, 0.5, -0.5, 1, -1], arp: 'up', gate: 40, variation: 0 },
  { name: 'Trance', bpm: '134–142', swing: 50, programs: [81, 38, 89, 81], bass: '0111011101110111', comp: '1000000010000000', kick: '1000100010001000', snare: '0000100000001000', hat: '0010001000100010', durations: [0.5, 0.5, 1, 0.5, 0.5, 1], arp: 'up-down', gate: 85, variation: 5 },
  { name: 'Synthwave', bpm: '90–110', swing: 50, programs: [81, 38, 90, 80], bass: '1010101010101010', comp: '1000000010000000', kick: '1000000010100000', snare: '0000100000001000', hat: '1010101010101010', durations: [1, 0.5, 0.5, -1, 1], arp: 'down-up', gate: 95, variation: 8 },
  { name: 'Disco', bpm: '110–126', swing: 52, programs: [62, 34, 4, 10], bass: '1010011010100110', comp: '0010001000100010', kick: '1000100010001000', snare: '0000100000001000', hat: '1111111111111111', durations: [-0.5, 0.5, 0.5, 0.5, 1, 1], arp: 'frog-up', gate: 55, variation: 35 },
  { name: 'Funk', bpm: '95–112', swing: 56, programs: [65, 36, 27, 11], bass: '1001001010010110', comp: '0010010000100101', kick: '1000001010000010|1000001010000110', snare: '0000100000001000', hat: '1110101111101011', durations: [0.25, -0.25, 0.5, -0.5, 0.5, 1, -1], arp: 'frog-down', gate: 40, variation: 45 },
  { name: 'Boom Bap', bpm: '82–96', swing: 62, programs: [65, 33, 4, 11], bass: '1000001000100000', comp: '1000000000100000', kick: '1000000010010000|1000000010010010', snare: '0000100000001000', hat: '1010101010101010', durations: [0.5, -0.5, 0.5, 0.5, -1, 1], arp: 'down', gate: 80, variation: 18 },
  { name: 'Lo-fi Hip Hop', bpm: '68–84', swing: 60, programs: [11, 32, 4, 8], bass: '1000000010000010', comp: '1000000000100000', kick: '1000000010000010', snare: '0000100000001000', hat: '1010001010100010', durations: [1, -0.5, 0.5, 1, -1], arp: 'random', gate: 110, variation: 20 },
  { name: 'Trap', bpm: '130–150 half-time', swing: 50, programs: [80, 38, 88, 10], bass: '1000000000100010', comp: '1000000000000000', kick: '1000001000100001|1000001000100011', snare: '0000000010000000', hat: '1111101011111011', durations: [0.25, 0.25, -0.5, 1, -1, 1], arp: 'down', gate: 70, variation: 0 },
  { name: 'Drum and Bass', bpm: '168–176', swing: 50, programs: [81, 39, 89, 80], bass: '1000001010000100', comp: '1000000000100000', kick: '1000000000100000|1000000000100010', snare: '0000100000001000', hat: '1011101010111010', durations: [0.5, 0.5, -1, 0.5, 0.5, 1], arp: 'up-down', gate: 65, variation: 12 },
  { name: 'UK Garage', bpm: '128–136', swing: 64, programs: [11, 38, 4, 10], bass: '1000010010100001', comp: '0010010000100100', kick: '1000000000100000|0000001000100010', snare: '0000100000001000', hat: '0010100100101001', durations: [-0.5, 0.5, 0.25, 0.25, -0.5, 1, -1], arp: 'frog-up', gate: 45, variation: 20 },
  { name: 'Dubstep', bpm: '138–144 half-time', swing: 50, programs: [81, 39, 91, 80], bass: '1000010010000010', comp: '1000000000001000', kick: '1000000000000010|1000010000000011', snare: '0000000010000000', hat: '1010101110101011', durations: [1, -1, 0.5, 0.5, -1], arp: 'down-up', gate: 50, variation: 5 },
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
  const bars = mask.split('|'), steps = bars.join('');
  if (bars.length > 4 || steps.length > 32 || bars.some(bar => bar.length !== bars[0].length || !/^[01]+$/.test(bar))) {
    throw new RangeError('Factory rhythm grids must fit 32 hits across up to four equal bars.');
  }
  return { rhythm: 'custom' as const, hitsPerPattern: steps.length,
    ...(bars.length > 1 ? { chordSpan: bars.length } : {}),
    customPattern: Array.from({ length: 32 }, (_, index) => Number(steps[index] ?? 0)) };
}

function drumGrid(mask: string, swing: number) {
  const rhythm = grid(mask);
  // Twelve cells per bar already encode a triplet pulse.
  return { ...rhythm, swingPercent: rhythm.hitsPerPattern / (rhythm.chordSpan ?? 1) === 12 ? 50 : swing };
}

/** A recognizable motif with a less frequent, style-specific answer rhythm. */
function durationSettings(primary: readonly number[], answer: readonly number[], weight = 75) {
  return { customDurations: [primary, answer, ...Array.from({ length: 8 }, () => [] as number[])],
    customDurationRandomWeighting: true, customDurationChances: [weight, 100 - weight, ...Array(8).fill(0)] };
}

function track<R extends keyof PartSettingsByRole>(role: R, name: string, index: number,
  program: number, generatorSettings: PartSettingsByRole[R], volumePercent = 80, panPercent = 0): CompositionTrack {
  return { id: `builtin-${role}-${index}`, role, name, midiChannel: { melody: 4, bass: 1, chords: 2, arpeggio: 3, drums: 10 }[role],
    generatorSettings, mix: { program, volumePercent, panPercent, muted: false, solo: false } } as CompositionTrack;
}

/** Factory presets stay separate from browser-owned libraries and never contain phrase data. */
export const BUILTIN_TRACK_PRESETS: readonly TrackPreset[] = STYLES.flatMap(style => {
  const character = builtinStyleSettings(style.name);
  const common = { patternSeed: 0, swingPercent: style.swing, velocityMin: 68, velocityMax: 100 };
  // Start with two statements of the same Word, then allow a contrasting phrase on longer loops.
  const melody = { ...common, algorithm: 'block' as const, rhythm: 'durations' as const, melodyPatternOffsets: [1, 1, 2, 1] };
  const bassCommon = { patternSeed: 0, velocityMin: 68, velocityMax: 100 };
  const durations = durationSettings(style.durations, character.answer);
  const leadRhythm = character.automaticLead
    ? { rhythm: 'melody1' as const, ...character.automaticLead } : durations;
  const name = `${style.name} · ${style.bpm} BPM`;
  const tracks: CompositionTrack[][] = [
    [track('melody', 'Lead', 1, style.programs[0], { ...melody,
      ending: 'OPEN', splitChance: 0, noteLengthMultiplier: Math.min(100, style.gate), ...character.lead, ...leadRhythm }),
    track('melody', 'Quiet response', 2, style.programs[3], { ...melody,
      ...character.lead, ...durationSettings(character.responseDurations ?? character.answer, style.durations, character.responseDurations ? 100 : 85),
      ending: 'OPEN', transpose: 12, noteLengthMultiplier: Math.min(100, style.gate), velocityMin: 45, velocityMax: 65,
      ...character.response }, 45, 18)],
    [track('bass', 'Foundation', 1, style.programs[1], { ...bassCommon, ...grid(style.bass), noteVariation: style.variation, octaveInterval: false,
      patternJoinMode: 'NOJOIN', noteLengthMultiplier: Math.min(style.gate, 95) }),
    track('bass', 'Low anchor', 2, style.programs[1], { ...bassCommon, ...grid(character.bassSupport),
      noteVariation: Math.floor(style.variation / 3), octaveInterval: false, patternJoinMode: 'NOJOIN',
      velocityMin: 50, velocityMax: 65, noteLengthMultiplier: Math.min(style.gate + 15, 110) }, 40)],
    [track('chords', 'Comping', 1, style.programs[2], { ...common, ...grid(style.comp), voicing: 'close',
      noteLengthPercent: style.gate, strum: style.strum ?? 0, patternJoinMode: 'NOJOIN', ...character.harmony }, 70, -15),
    track('chords', 'Sustained harmony', 2, style.programs[2], { ...common, rhythm: 'single', hitsPerPattern: 4,
      voicing: 'open', noteLengthPercent: 160, velocityMin: 45, velocityMax: 60, ...character.pad }, 40, 15)],
    [track('arpeggio', 'Moving figure', 1, style.programs[3], { ...common, pattern: style.arp, rate: 'sixteenth', octaves: 2,
      ...grid(style.comp), noteLengthMultiplier: Math.min(style.gate, 90), transpose: -12,
      arpContourChordMode: true, ...character.arp }, 60, 20),
    track('arpeggio', 'Answer figure', 2, style.programs[3], { ...common, pattern: 'down', rate: 'eighth', octaves: 1,
      rhythm: 'durations', ...durationSettings(character.answer, style.durations, 85),
      transpose: 12, velocityMin: 45, velocityMax: 65 }, 40, -20),
    track('arpeggio', 'Counterpulse', 3, style.programs[2], { ...common, pattern: 'up', rate: 'eighth', octaves: 1,
      ...grid(character.bassSupport), patternShift: 1, arpPatternRotate: 1,
      pauseChance: character.accentPause, exceptionChance: Math.floor(character.hatSplit / 2),
      noteLengthMultiplier: Math.min(style.gate, 75), velocityMin: 40, velocityMax: 60 }, 35, -8)],
    [track('drums', 'Kick', 1, 0, { ...common, ...drumGrid(style.kick, style.swing), pitch: 36, humanize: Math.floor(character.humanize / 3) }),
    track('drums', 'Snare / rim', 2, 0, { ...common, ...drumGrid(style.snare, style.swing), pitch: /Bossa|Reggae|Dub$/.test(style.name) ? 37 : 38,
      humanize: Math.floor(character.humanize / 2) }),
    track('drums', 'Hat / ride', 3, 0, { ...common, ...drumGrid(style.hat, style.swing), pitch: style.name === 'Swing Jazz' ? 53 : 42,
      velocityMin: 45, velocityMax: 75, humanize: character.humanize,
      exceptionChance: character.hatSplit }, 65, 12),
    ...PERCUSSION[style.name].map(([pitch, mask], index) => {
      const rhythm = drumGrid(mask, style.swing), authoredCycle = (rhythm.chordSpan ?? 1) > 1;
      return track('drums', drumInstrumentName(pitch), index + 4, 0,
        { ...common, ...rhythm, pitch, velocityMin: 42, velocityMax: 72, humanize: character.humanize,
          // Keep authored fill placements and sparse crashes dependable.
          pauseChance: !authoredCycle && rhythm.customPattern.filter(Boolean).length > 1 ? character.accentPause : 0,
          exceptionChance: authoredCycle ? 0 : Math.floor(character.hatSplit / 3) }, 50, index % 2 === 0 ? -18 : 18);
    })],
  ];
  return tracks.map(group => ({ name, role: group[0].role, tracks: group }));
});




/** Matching factory styles load every role in one gesture. */
export const BUILTIN_MULTI_ROLE_PRESETS: readonly TrackPreset[] = [...new Set(BUILTIN_TRACK_PRESETS.map(preset => preset.name))]
  .map(name => ({ name, role: 'all', tracks: BUILTIN_TRACK_PRESETS.filter(preset => preset.name === name).flatMap(preset => preset.tracks) }));
