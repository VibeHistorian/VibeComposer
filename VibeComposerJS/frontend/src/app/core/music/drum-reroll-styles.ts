import type { DrumJob } from './drum-kit-pattern';

export const DRUM_REROLL_STYLE_OPTIONS = [
  { value: 'any', label: 'Any' },
  { value: 'deep-house', label: 'Deep House' },
  { value: 'trap', label: 'Trap' },
  { value: 'reggae', label: 'Reggae' },
  { value: 'swing-jazz', label: 'Swing Jazz' },
  { value: 'dnb', label: 'DnB' },
  { value: 'funk', label: 'Funk' },
  { value: 'trance', label: 'Trance' },
  { value: 'samba', label: 'Samba' },
  { value: 'techno', label: 'Techno' },
  { value: 'synthwave', label: 'Synthwave' },
  { value: 'disco', label: 'Disco' },
  { value: 'boom-bap', label: 'Boom Bap' },
  { value: 'lo-fi-hip-hop', label: 'Lo-fi Hip Hop' },
  { value: 'uk-garage', label: 'UK Garage' },
  { value: 'dubstep', label: 'Dubstep' },
  { value: 'dub', label: 'Dub' },
  { value: 'afrobeat', label: 'Afrobeat' },
  { value: 'bossa-nova', label: 'Bossa Nova' },
  { value: 'blues-shuffle', label: 'Blues Shuffle' },
  { value: 'indie-rock', label: 'Indie Rock' },
  { value: 'pop-punk', label: 'Pop Punk' },
  { value: 'acoustic-folk', label: 'Acoustic Folk' },
  { value: 'ambient', label: 'Ambient' },
  { value: 'cinematic-pulse', label: 'Cinematic Pulse' },
] as const;

export type DrumRerollStyleId = typeof DRUM_REROLL_STYLE_OPTIONS[number]['value'];

export interface DrumStyleVoice {
  /** Positions in one zero-based bar, ordered by importance at low density. */
  readonly accents: readonly number[];
  readonly ornaments?: readonly number[];
  /** Preferred General MIDI pitches in order. The instrument pool still defines eligibility. */
  readonly pitches?: readonly number[];
  readonly pitchBias?: number;
}

interface DrumRerollStyle {
  readonly grid: 12 | 16;
  readonly swing: number;
  readonly voices: Readonly<Partial<Record<DrumJob, DrumStyleVoice>>>;
  readonly sharedPulseAndAnswer?: boolean;
}

// Distill the factory grooves into signature accents and slots that can vary on a reroll.
const fourOnTheFloor = [0, 8, 4, 12];
const backbeat = [4, 12];
const offbeats = [2, 10, 6, 14];
const eighths = [0, 8, 4, 12, 2, 10, 6, 14];

export const DRUM_REROLL_STYLES: Readonly<Record<Exclude<DrumRerollStyleId, 'any'>, DrumRerollStyle>> = {
  'deep-house': { grid: 16, swing: 50, voices: {
    pulse: { accents: fourOnTheFloor, pitches: [36, 35] },
    answer: { accents: backbeat, ornaments: [11, 15], pitches: [39, 38] },
    time: { accents: offbeats, ornaments: [1, 5, 9, 13], pitches: [42, 82, 69] },
    reply: { accents: [3, 11], ornaments: [7, 14], pitches: [63, 60] },
  } },
  trap: { grid: 16, swing: 50, voices: {
    pulse: { accents: [0, 10], ornaments: [6, 15, 14], pitches: [35, 36] },
    answer: { accents: [8], ornaments: [7, 15], pitches: [38, 39, 40] },
    time: { accents: [0, 4, 8, 12], ornaments: [2, 6, 10, 14, 9, 11, 15], pitches: [42, 44] },
    accent: { accents: [6, 14], pitches: [46, 55] },
  } },
  reggae: { grid: 16, swing: 54, sharedPulseAndAnswer: true, voices: {
    pulse: { accents: [8], pitches: [36, 35] },
    answer: { accents: [8], pitches: [37, 38] },
    time: { accents: offbeats, ornaments: [0, 4, 8, 12], pitches: [42, 44, 82] },
    reply: { accents: [3, 9], ornaments: [6, 13, 15], pitches: [64, 60, 73] },
  } },
  'swing-jazz': { grid: 12, swing: 50, voices: {
    pulse: { accents: [0], ornaments: [6, 11], pitches: [36, 35] },
    answer: { accents: [3, 9], ornaments: [2, 8, 10], pitches: [37, 38] },
    time: { accents: [3, 9, 5, 11], ornaments: [0, 6], pitches: [53, 51, 59], pitchBias: 7 },
    accent: { accents: [0], ornaments: [11], pitches: [55, 59] },
  } },
  dnb: { grid: 16, swing: 50, voices: {
    pulse: { accents: [0, 10], ornaments: [14, 3, 7], pitches: [36, 35] },
    answer: { accents: backbeat, ornaments: [7, 11, 15], pitches: [38, 40] },
    time: { accents: [0, 4, 8, 12], ornaments: [2, 6, 10, 14, 3, 7], pitches: [42, 44] },
    accent: { accents: [0, 8], ornaments: [4, 12], pitches: [51, 53, 46] },
  } },
  funk: { grid: 16, swing: 56, voices: {
    pulse: { accents: [0, 6, 8, 14], ornaments: [13], pitches: [36, 35] },
    answer: { accents: backbeat, ornaments: [3, 9, 11, 15], pitches: [38, 37] },
    time: { accents: [0, 4, 8, 12, 6, 14], ornaments: [1, 2, 5, 7, 9, 10, 13, 15], pitches: [42, 44] },
    reply: { accents: [3, 10], ornaments: [0, 6, 13], pitches: [56, 63, 60] },
  } },
  trance: { grid: 16, swing: 50, voices: {
    pulse: { accents: fourOnTheFloor, pitches: [36, 35] },
    answer: { accents: backbeat, pitches: [38, 39, 40] },
    time: { accents: offbeats, pitches: [46], pitchBias: 6 },
    accent: { accents: [0], pitches: [49, 57, 53] },
  } },
  samba: { grid: 16, swing: 50, voices: {
    pulse: { accents: [8, 0, 6, 14], pitches: [36, 43, 41] },
    answer: { accents: [2, 5, 10, 13], ornaments: [8, 15], pitches: [37, 38] },
    time: { accents: [0, 4, 8, 12, 2, 6, 10, 14], ornaments: [1, 3, 5, 7, 9, 11, 13, 15], pitches: [82, 70, 42] },
    reply: { accents: [0, 3, 6, 10, 12], ornaments: [9, 14], pitches: [67, 64, 60] },
  } },
  techno: { grid: 16, swing: 50, voices: {
    pulse: { accents: fourOnTheFloor, pitches: [35, 36] },
    answer: { accents: backbeat, ornaments: [15], pitches: [40, 39] },
    time: { accents: [0, 2, 3, 4, 8, 10, 11, 12], ornaments: [6, 14, 15], pitches: [42, 44] },
    accent: { accents: offbeats, pitches: [46] },
  } },
  synthwave: { grid: 16, swing: 50, voices: {
    pulse: { accents: [0, 8, 10], ornaments: [14], pitches: [36, 35] },
    answer: { accents: backbeat, pitches: [40], pitchBias: 6 },
    time: { accents: eighths, pitches: [42, 44] },
    reply: { accents: [12, 14], ornaments: [15], pitches: [45, 47, 41] },
  } },
  disco: { grid: 16, swing: 50, voices: {
    pulse: { accents: fourOnTheFloor, pitches: [36, 35] },
    answer: { accents: backbeat, pitches: [38, 39] },
    time: { accents: [0, 4, 8, 12, 1, 5, 9, 13], ornaments: [3, 7, 11, 15], pitches: [42, 44], pitchBias: 6 },
    accent: { accents: offbeats, pitches: [46] },
    reply: { accents: [0, 6, 10], ornaments: [14], pitches: [56, 54, 60] },
  } },
  'boom-bap': { grid: 16, swing: 55, voices: {
    pulse: { accents: [0, 8, 11], ornaments: [14], pitches: [35, 36] },
    answer: { accents: backbeat, ornaments: [3, 10, 15], pitches: [38, 40] },
    time: { accents: [0, 4, 8, 12, 6, 14], ornaments: [2, 10], pitches: [44, 42] },
    accent: { accents: [0], pitches: [51, 53] },
  } },
  'lo-fi-hip-hop': { grid: 16, swing: 54, voices: {
    pulse: { accents: [0, 8, 14], pitches: [35, 36] },
    answer: { accents: backbeat, ornaments: [11], pitches: [37, 38] },
    time: { accents: [0, 2, 6, 8, 10, 14], pitches: [44, 82, 70] },
    accent: { accents: [8], pitches: [59, 53] },
  } },
  'uk-garage': { grid: 16, swing: 56, voices: {
    pulse: { accents: [0, 6, 10], ornaments: [14], pitches: [36, 35] },
    answer: { accents: backbeat, pitches: [39, 38] },
    time: { accents: [2, 4, 7, 10, 12, 15], ornaments: [6, 14], pitches: [42, 44] },
    reply: { accents: [3, 11, 14], ornaments: [7], pitches: [62, 60, 75] },
  } },
  dubstep: { grid: 16, swing: 50, voices: {
    pulse: { accents: [0, 14], ornaments: [5, 15], pitches: [35, 36] },
    answer: { accents: [8], pitches: [40, 38] },
    time: { accents: [0, 2, 4, 8, 10, 12], ornaments: [6, 7, 14, 15], pitches: [42, 44] },
    accent: { accents: [0], pitches: [52, 49, 53] },
  } },
  dub: { grid: 16, swing: 50, sharedPulseAndAnswer: true, voices: {
    pulse: { accents: [8], pitches: [35, 36] },
    answer: { accents: [8], pitches: [37, 38] },
    time: { accents: offbeats, pitches: [44, 82, 42] },
    reply: { accents: [3, 14], ornaments: [11], pitches: [64, 61, 60] },
    accent: { accents: [8], pitches: [59, 53] },
  } },
  afrobeat: { grid: 16, swing: 50, voices: {
    pulse: { accents: [0, 6, 8, 14], pitches: [36, 35] },
    answer: { accents: backbeat, ornaments: [7, 15], pitches: [38, 37] },
    time: { accents: [0, 2, 3, 5, 7, 8, 10, 11, 13, 15], pitches: [82, 42, 70] },
    reply: { accents: [0, 3, 6, 10, 12], ornaments: [14], pitches: [56, 60, 63, 67] },
  } },
  'bossa-nova': { grid: 16, swing: 50, voices: {
    pulse: { accents: [0, 8, 6, 14], pitches: [36, 35] },
    answer: { accents: [3, 10, 13], pitches: [37], pitchBias: 6 },
    time: { accents: eighths, pitches: [69, 82, 44] },
    reply: { accents: [0, 3, 6, 10, 12], ornaments: [15], pitches: [75, 60, 63] },
  } },
  'blues-shuffle': { grid: 12, swing: 50, voices: {
    pulse: { accents: [0, 6], ornaments: [8, 11], pitches: [36, 35] },
    answer: { accents: [3, 9], ornaments: [11], pitches: [38, 37] },
    time: { accents: [0, 2, 3, 5, 6, 8, 9, 11], pitches: [42, 44, 51, 53] },
    reply: { accents: [8, 11], ornaments: [10], pitches: [45, 47, 41] },
  } },
  'indie-rock': { grid: 16, swing: 50, voices: {
    pulse: { accents: [0, 8, 10], ornaments: [15], pitches: [35, 36] },
    answer: { accents: backbeat, ornaments: [11, 15], pitches: [38, 37] },
    time: { accents: eighths, pitches: [42, 44] },
    reply: { accents: [12, 14], ornaments: [11, 15], pitches: [48, 47, 41] },
  } },
  'pop-punk': { grid: 16, swing: 50, voices: {
    pulse: { accents: [0, 8, 6, 14], ornaments: [7, 15], pitches: [36, 35] },
    answer: { accents: backbeat, pitches: [38, 40] },
    time: { accents: eighths, ornaments: [7, 15], pitches: [42, 44] },
    accent: { accents: [0, 8], pitches: [57, 49, 53] },
    reply: { accents: [12, 14, 15], ornaments: [13], pitches: [50, 47, 41] },
  } },
  'acoustic-folk': { grid: 16, swing: 50, voices: {
    pulse: { accents: [0, 8], pitches: [36, 35] },
    answer: { accents: backbeat, pitches: [37], pitchBias: 6 },
    time: { accents: fourOnTheFloor, pitches: [70, 82, 54] },
    reply: { accents: [6, 14], pitches: [77, 60] },
  } },
  ambient: { grid: 16, swing: 50, voices: {
    pulse: { accents: [0], pitches: [35, 36] },
    answer: { accents: [12], pitches: [37, 38] },
    time: { accents: [6, 14], pitches: [82, 44, 42] },
    reply: { accents: [8], pitches: [80, 61, 60] },
    accent: { accents: [0], pitches: [59, 53] },
  } },
  'cinematic-pulse': { grid: 16, swing: 50, voices: {
    pulse: { accents: [0, 8, 14], pitches: [36, 35] },
    answer: { accents: [12], ornaments: [15], pitches: [40, 38] },
    time: { accents: eighths, pitches: [42, 44] },
    reply: { accents: [6, 14], ornaments: [11, 15], pitches: [50, 47, 41], pitchBias: 6 },
    accent: { accents: [0], pitches: [52, 57, 53] },
  } },
};
