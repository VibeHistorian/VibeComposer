import type { ArpeggioSettings, ChordSettings, MelodySettings } from '../music/part-settings.model';

interface StyleSettings {
  readonly lead: Partial<MelodySettings>;
  readonly answer: readonly number[];
  readonly bassSupport: string;
  readonly harmony: Partial<ChordSettings>;
  readonly pad: Partial<ChordSettings>;
  readonly arp: Partial<ArpeggioSettings>;
  readonly humanize: number;
  readonly hatSplit: number;
  readonly accentPause: number;
}

// Probabilities are consumed by the existing JavaRandom phrase streams. Zero part
// seeds follow the project/track seed; loading a preset never rolls or bakes notes.
export const BUILTIN_STYLE_SETTINGS = {
  'Deep House': {
    lead: { speed: 25, maxBlockChange: 2, blockJump: 0, chordNoteTarget: 85, newBlocksChance: 12 },
    answer: [-1.5, 0.5, -1, 1], bassSupport: '1000000010000000',
    harmony: { voicing: 'open', strum: 12, strumType: 'HUMAN_U', pauseChance: 4 },
    pad: { chordSpan: 2, noteLengthPercent: 190, transpose: 12 },
    arp: { octaves: 1, arpContour: [0, 2, 4, 2], pauseChance: 9, exceptionChance: 3 },
    humanize: 8, hatSplit: 2, accentPause: 8,
  },
  'Techno': {
    lead: { speed: 80, maxBlockChange: 1, blockJump: 0, newBlocksChance: 5, targetDirection: 'ASC' },
    answer: [-2.5, 0.25, 0.25, -0.5, 0.5], bassSupport: '1000100010001000',
    harmony: { transpose: -12, pauseChance: 3 }, pad: { noteLengthPercent: 70, chordSpanFill: 'ODD' },
    arp: { octaves: 1, arpContour: [0, 0, 1, 0], exceptionChance: 7, pauseChance: 4 },
    humanize: 0, hatSplit: 6, accentPause: 10,
  },
  'Trance': {
    lead: { speed: 70, maxBlockChange: 4, blockJump: 2, targetDirection: 'ASC', newBlocksChance: 25 },
    answer: [-1, 0.5, 0.5, 1, 1], bassSupport: '0010001000100010',
    harmony: { voicing: 'open', strum: 8, strumType: 'HUMAN_U', transitionChance: 12, transitionSplit: 500 }, pad: { chordSpan: 2, noteLengthPercent: 180 },
    arp: { arpContour: [0, 2, 4, 7], exceptionChance: 4, pauseChance: 3 },
    humanize: 0, hatSplit: 3, accentPause: 6,
  },
  'Synthwave': {
    lead: { speed: 35, maxBlockChange: 3, blockJump: 1, targetDirection: 'DESC', newBlocksChance: 18 },
    answer: [-2, 0.5, 0.5, 1], bassSupport: '1000000010001000',
    harmony: { voicing: 'open', strum: 20, strumType: 'HUMAN', transitionChance: 8 }, pad: { noteLengthPercent: 175, transpose: 12 },
    arp: { arpContour: [0, 4, 2, 7], pauseChance: 5, exceptionChance: 2 },
    humanize: 6, hatSplit: 2, accentPause: 8,
  },
  'Disco': {
    lead: { speed: 60, maxBlockChange: 4, blockJump: 2, chordNoteTarget: 75, newBlocksChance: 32 },
    answer: [-0.5, 0.5, -1, 0.5, 0.5, 1], bassSupport: '1000001000100000',
    harmony: { strum: 22, strumType: 'HUMAN_D', pauseChance: 5, transitionChance: 15 }, pad: { noteLengthPercent: 95, chordSpanFill: 'EVEN' },
    arp: { octaves: 2, arpContour: [0, 4, 7, 4], pauseChance: 8, exceptionChance: 5 },
    humanize: 20, hatSplit: 3, accentPause: 12,
  },
  'Funk': {
    lead: { speed: 75, maxBlockChange: 4, blockJump: 2, chordNoteTarget: 80, newBlocksChance: 40 },
    answer: [-0.75, 0.25, -0.5, 0.5, -1, 0.5, 0.5], bassSupport: '1000000000100010',
    harmony: { strum: 18, strumType: 'RAND_U', strumPauseChance: 8, pauseChance: 9 }, pad: { noteLengthPercent: 55, chordSpanFill: 'EVEN' },
    arp: { octaves: 1, arpContour: [0, 3, 1, 4], pauseChance: 16, exceptionChance: 9 },
    humanize: 30, hatSplit: 7, accentPause: 18,
  },
  'Boom Bap': {
    lead: { speed: 10, maxBlockChange: 2, blockJump: 0, newBlocksChance: 15, targetDirection: 'DESC' },
    answer: [-2, 1, -0.5, 0.5], bassSupport: '1000000000100000',
    harmony: { strum: 35, strumType: 'HUMAN', pauseChance: 5 }, pad: { noteLengthPercent: 125, transpose: -12, chordSpanFill: 'ODD' },
    arp: { octaves: 1, arpContour: [0, -2, 1, -1], pauseChance: 15, exceptionChance: 3 },
    humanize: 55, hatSplit: 4, accentPause: 16,
  },
  'Lo-fi Hip Hop': {
    lead: { speed: -25, maxBlockChange: 2, blockJump: 0, newBlocksChance: 20, chordNoteTarget: 90 },
    answer: [-1.5, 0.5, 1, -1], bassSupport: '1000000000001000',
    harmony: { voicing: 'open', strum: 55, strumType: 'HUMAN_U', strumPauseChance: 5 }, pad: { noteLengthPercent: 190, chordSpan: 2, transpose: -12 },
    arp: { octaves: 1, arpContour: [0, 2, -1, 4], pauseChance: 20, exceptionChance: 2 },
    humanize: 85, hatSplit: 3, accentPause: 20,
  },
  'Trap': {
    lead: { speed: 65, maxBlockChange: 2, blockJump: 1, newBlocksChance: 10, targetDirection: 'DESC' },
    answer: [-3, 0.25, 0.25, 0.5], bassSupport: '1000000000000010',
    harmony: { voicing: 'open', strum: 8, strumType: 'HUMAN' }, pad: { noteLengthPercent: 190, chordSpan: 2 },
    arp: { octaves: 1, arpContour: [7, 4, 2, 0], pauseChance: 18, exceptionChance: 14 },
    humanize: 6, hatSplit: 24, accentPause: 14,
  },
  'Drum and Bass': {
    lead: { speed: 80, maxBlockChange: 4, blockJump: 2, newBlocksChance: 28 },
    answer: [-1, 0.5, -1, 0.5, 1], bassSupport: '1000000010000100',
    harmony: { voicing: 'open', pauseChance: 7, transitionChance: 10 }, pad: { chordSpan: 2, noteLengthPercent: 180, transpose: 12 },
    arp: { arpContour: [0, 4, 1, 7], pauseChance: 10, exceptionChance: 8 },
    humanize: 12, hatSplit: 13, accentPause: 12,
  },
  'UK Garage': {
    lead: { speed: 55, maxBlockChange: 3, blockJump: 1, newBlocksChance: 35 },
    answer: [-0.75, 0.25, -1, 0.5, -0.5, 1], bassSupport: '1000000000100001',
    harmony: { strum: 16, strumType: 'RAND_D', pauseChance: 12, transitionChance: 18 }, pad: { noteLengthPercent: 80, chordSpanFill: 'EVEN' },
    arp: { octaves: 1, arpContour: [0, 4, 2, -1], pauseChance: 14, exceptionChance: 10 },
    humanize: 28, hatSplit: 11, accentPause: 17,
  },
  'Dubstep': {
    lead: { speed: 20, maxBlockChange: 5, blockJump: 3, newBlocksChance: 18 },
    answer: [-2, 0.5, -0.5, 1], bassSupport: '1000000000001000',
    harmony: { transpose: -12, pauseChance: 5 }, pad: { noteLengthPercent: 180, chordSpanFill: 'ODD' },
    arp: { arpContour: [0, -3, 0, 4], pauseChance: 20, exceptionChance: 12 },
    humanize: 4, hatSplit: 16, accentPause: 15,
  },
  'Reggae': {
    lead: { speed: 5, maxBlockChange: 3, blockJump: 1, newBlocksChance: 22 },
    answer: [-1, 1, -0.5, 0.5, 1], bassSupport: '1000000000000100',
    harmony: { strum: 15, strumType: 'HUMAN_D', pauseChance: 4 }, pad: { noteLengthPercent: 45, patternShift: 2, hitsPerPattern: 8 },
    arp: { octaves: 1, arpContour: [0, 2, 4, 1], pauseChance: 12, exceptionChance: 3 },
    humanize: 38, hatSplit: 3, accentPause: 14,
  },
  'Dub': {
    lead: { speed: -50, maxBlockChange: 2, blockJump: 0, newBlocksChance: 8 },
    answer: [-2.5, 0.5, -0.5, 0.5], bassSupport: '1000000000000000',
    harmony: { pauseChance: 10, feedbackCount: 2, feedbackDuration: 750, feedbackVol: 45 },
    pad: { noteLengthPercent: 60, chordSpanFill: 'EVEN', feedbackCount: 1, feedbackDuration: 1500, feedbackVol: 35 },
    arp: { octaves: 1, arpContour: [0, -2, 2], pauseChance: 18, feedbackCount: 2, feedbackDuration: 750, feedbackVol: 40 },
    humanize: 45, hatSplit: 2, accentPause: 22,
  },
  'Afrobeat': {
    lead: { speed: 60, maxBlockChange: 3, blockJump: 2, newBlocksChance: 32 },
    answer: [-0.5, 0.5, -0.5, 1, 0.5, 1], bassSupport: '1000001000000010',
    harmony: { strum: 14, strumType: 'HUMAN_U', pauseChance: 7, transitionChance: 12 }, pad: { noteLengthPercent: 65, chordSpanFill: 'ODD' },
    arp: { octaves: 1, arpContour: [0, 2, 4, 1, 3], pauseChance: 8, exceptionChance: 6 },
    humanize: 32, hatSplit: 5, accentPause: 10,
  },
  'Bossa Nova': {
    lead: { speed: 15, maxBlockChange: 3, blockJump: 1, newBlocksChance: 28, chordNoteTarget: 85 },
    answer: [-0.75, 0.25, 1, -0.5, 0.5, 1], bassSupport: '1000000000000010',
    harmony: { voicing: 'open', strumType: 'HUMAN_U', strumPauseChance: 4, transitionChance: 18 }, pad: { noteLengthPercent: 115, chordSpanFill: 'EVEN' },
    arp: { octaves: 1, arpContour: [0, 4, 2, 5], pauseChance: 10, exceptionChance: 4 },
    humanize: 40, hatSplit: 2, accentPause: 9,
  },
  'Samba': {
    lead: { speed: 70, maxBlockChange: 4, blockJump: 2, newBlocksChance: 38 },
    answer: [-0.5, 0.25, 0.25, -1, 0.5, 0.5, 1], bassSupport: '1000000010000010',
    harmony: { strumType: 'RAND_U', strumPauseChance: 5, transitionChance: 20 }, pad: { noteLengthPercent: 75, chordSpanFill: 'ODD' },
    arp: { octaves: 1, arpContour: [0, 3, 1, 4, 2], pauseChance: 7, exceptionChance: 8 },
    humanize: 35, hatSplit: 6, accentPause: 11,
  },
  'Swing Jazz': {
    lead: { speed: 45, maxBlockChange: 5, blockJump: 3, newBlocksChance: 55, chordNoteTarget: 65 },
    answer: [-1, 0.5, 0.5, -0.5, 0.5, 1], bassSupport: '1000000010000000',
    harmony: { voicing: 'open', strum: 40, strumType: 'HUMAN', pauseChance: 12, transitionChance: 35 },
    pad: { noteLengthPercent: 105, chordSpanFill: 'EVEN', strum: 25, strumType: 'HUMAN_D' },
    arp: { octaves: 1, arpContour: [0, 2, 4, 6, 3], pauseChance: 18, exceptionChance: 10 },
    humanize: 65, hatSplit: 4, accentPause: 18,
  },
  'Blues Shuffle': {
    lead: { speed: 30, maxBlockChange: 3, blockJump: 2, newBlocksChance: 35 },
    answer: [-1, 0.5, 0.5, 1, -1], bassSupport: '100000100000',
    harmony: { strumType: 'HUMAN_D', transitionChance: 22, pauseChance: 5 }, pad: { noteLengthPercent: 100, chordSpanFill: 'ODD' },
    arp: { octaves: 1, arpContour: [0, 2, 4, 5, 4, 2], pauseChance: 8, exceptionChance: 5 },
    humanize: 50, hatSplit: 5, accentPause: 13,
  },
  'Indie Rock': {
    lead: { speed: 55, maxBlockChange: 4, blockJump: 2, newBlocksChance: 25 },
    answer: [-1, 1, 0.5, 0.5, -1], bassSupport: '1000001000001000',
    harmony: { strumType: 'HUMAN_D', strumPauseChance: 3, transitionChance: 8 }, pad: { noteLengthPercent: 150, chordSpanFill: 'EVEN', transpose: 12 },
    arp: { arpContour: [0, 4, 1, 5], pauseChance: 6, exceptionChance: 4 },
    humanize: 28, hatSplit: 4, accentPause: 12,
  },
  'Pop Punk': {
    lead: { speed: 90, maxBlockChange: 3, blockJump: 2, newBlocksChance: 15, targetDirection: 'ASC' },
    answer: [-2, 0.5, 0.5, 0.5, 0.5], bassSupport: '1000100010001000',
    harmony: { strumType: 'HUMAN_D', strumPauseChance: 2 }, pad: { noteLengthPercent: 85, transpose: -12, chordSpanFill: 'ODD' },
    arp: { octaves: 1, arpContour: [0, 4, 7, 4, 2], pauseChance: 3, exceptionChance: 3 },
    humanize: 18, hatSplit: 7, accentPause: 8,
  },
  'Acoustic Folk': {
    lead: { speed: -10, maxBlockChange: 2, blockJump: 1, newBlocksChance: 20, chordNoteTarget: 90 },
    answer: [-1, 1, -1, 0.5, 0.5], bassSupport: '1000001000000000',
    harmony: { voicing: 'open', strumType: 'HUMAN_U', strumPauseChance: 5, transitionChance: 10 },
    pad: { noteLengthPercent: 145, chordSpan: 2, strum: 80, strumType: 'HUMAN_D' },
    arp: { octaves: 1, arpContour: [0, 4, 2, 4, 1, 4], pauseChance: 7, exceptionChance: 3 },
    humanize: 60, hatSplit: 2, accentPause: 15,
  },
  'Ambient': {
    lead: { speed: -80, maxBlockChange: 1, blockJump: 0, newBlocksChance: 8 },
    answer: [-2, 2], bassSupport: '1000000000000000',
    harmony: { voicing: 'open', strum: 120, strumType: 'HUMAN', transitionChance: 15 },
    pad: { chordSpan: 4, noteLengthPercent: 200, transpose: 12, strum: 160, strumType: 'HUMAN_U' },
    arp: { rate: 'eighth', octaves: 2, chordSpan: 2, arpContour: [0, 4, 8, 2], pauseChance: 12, feedbackCount: 2, feedbackDuration: 1500, feedbackVol: 35 },
    humanize: 90, hatSplit: 0, accentPause: 8,
  },
  'Cinematic Pulse': {
    lead: { speed: 40, maxBlockChange: 5, blockJump: 3, newBlocksChance: 22, targetDirection: 'ASC' },
    answer: [-1.5, 1.5, 0.5, 0.5], bassSupport: '1000000010000000',
    harmony: { voicing: 'open', strum: 30, strumType: 'HUMAN_U', transitionChance: 18 },
    pad: { chordSpan: 2, noteLengthPercent: 195, transpose: -12 },
    arp: { arpContour: [0, 4, 0, 7, 0, 2], pauseChance: 4, exceptionChance: 5 },
    humanize: 15, hatSplit: 6, accentPause: 9,
  },
} as const satisfies Record<string, StyleSettings>;

export type BuiltinStyleName = keyof typeof BUILTIN_STYLE_SETTINGS;
export function builtinStyleSettings(name: BuiltinStyleName): StyleSettings {
  return BUILTIN_STYLE_SETTINGS[name];
}
