import type { LoudnessProfile } from './normalized-instrument-engine';

/** Chiptune (TinySynth 1.1.3). Editable offsets in dB; programs are ZERO-BASED.
 * Positive values boost, negative values attenuate. Drums use MIDI note numbers.
 * Measured with @audio/loudness-lufs: -24 LUFS, +/-12 dB, -3 dBFS sample-peak ceiling.
 * Probe details live in instrument-calibration.ts. Noise RNG seed: 0x51f15e.
 * Regenerate: pnpm run calibrate:chiptune (OVERWRITES manual edits).
 * Runtime always uses this file; old browser calibration caches are ignored. */
export const CHIPTUNE_MELODIC_OFFSETS_DB: Readonly<Record<number, number>> = {
  0: 2.955929, // Acoustic Grand Piano
  1: 2.955929, // Bright Acoustic Piano
  2: 2.955929, // Electric Grand Piano
  3: 2.955929, // Honky-tonk Piano
  4: 2.955929, // Electric Piano 1
  5: -0.650601, // Electric Piano 2
  6: -3.472307, // Harpsichord
  7: -3.472307, // Clavinet
  8: -5.041355, // Celesta
  9: -5.041355, // Glockenspiel
  10: -5.041355, // Music Box
  11: 1.012745, // Vibraphone
  12: -5.041355, // Marimba
  13: 1.012745, // Xylophone
  14: 1.022960, // Tubular Bells
  15: -4.265167, // Dulcimer
  16: -4.034233, // Drawbar Organ
  17: -2.757670, // Percussive Organ
  18: -2.757670, // Rock Organ
  19: -4.034233, // Church Organ
  20: -3.818534, // Reed Organ
  21: -6.967756, // Accordion
  22: -6.967756, // Harmonica
  23: -6.967756, // Tango Accordion
  24: 4.022167, // Nylon Guitar
  25: 4.022167, // Steel Guitar
  26: -4.648580, // Jazz Guitar
  27: -3.969076, // Clean Guitar
  28: -2.556056, // Muted Guitar
  29: -4.525414, // Overdriven Guitar
  30: -4.525414, // Distortion Guitar
  31: -3.888177, // Guitar Harmonics
  32: -2.626686, // Acoustic Bass
  33: -2.029266, // Fingered Bass
  34: -2.029266, // Picked Bass
  35: -4.392952, // Fretless Bass
  36: -2.361387, // Slap Bass 1
  37: -2.361387, // Slap Bass 2
  38: 4.839034, // Synth Bass 1
  39: -0.594414, // Synth Bass 2
  40: -5.600592, // Violin
  41: -6.163436, // Viola
  42: -4.859421, // Cello
  43: -4.859421, // Contrabass
  44: -2.236536, // Tremolo Strings
  45: 4.022167, // Pizzicato Strings
  46: -0.932260, // Harp
  47: 2.303952, // Timpani
  48: -5.177718, // Strings
  49: -4.018127, // String Ensemble 2
  50: -2.541187, // Synth Strings 1
  51: -2.541187, // Synth Strings 2
  52: -5.087581, // Choir Aahs
  53: -4.986484, // Voice Oohs
  54: -5.026548, // Synth Voice
  55: 5.938061, // Orchestra Hit
  56: 1.895048, // Trumpet
  57: -4.780394, // Trombone
  58: -4.780394, // Tuba
  59: -4.780394, // Muted Trumpet
  60: -5.408569, // French Horn
  61: -4.395996, // Brass Section
  62: -5.702591, // Synth Brass 1
  63: -0.594414, // Synth Brass 2
  64: -6.604776, // Soprano Sax
  65: -6.604776, // Alto Sax
  66: -6.604776, // Tenor Sax
  67: -6.604776, // Baritone Sax
  68: -3.429298, // Oboe
  69: -3.429298, // English Horn
  70: -3.429298, // Bassoon
  71: -2.580363, // Clarinet
  72: -3.429298, // Piccolo
  73: -4.487361, // Flute
  74: -5.496721, // Recorder
  75: -5.496721, // Pan Flute
  76: -5.496721, // Blown Bottle
  77: -3.429298, // Shakuhachi
  78: -4.986484, // Whistle
  79: -5.496721, // Ocarina
  80: -7.677721, // Square Lead
  81: -5.608210, // Sawtooth Lead
  82: -7.677721, // Calliope Lead
  83: -5.702591, // Chiff Lead
  84: -1.770582, // Charang Lead
  85: -5.408569, // Voice Lead
  86: -4.866462, // Fifths Lead
  87: -5.003365, // Bass and Lead
  88: -2.305179, // New Age Pad
  89: -2.908566, // Warm Pad
  90: -3.671226, // Polysynth Pad
  91: -2.908566, // Choir Pad
  92: -0.838670, // Bowed Pad
  93: -3.577613, // Metallic Pad
  94: -3.577613, // Halo Pad
  95: -2.236536, // Sweep Pad
  96: 5.135525, // Rain FX
  97: -3.110992, // Soundtrack FX
  98: -3.779709, // Crystal FX
  99: -4.018127, // Atmosphere FX
  100: -4.192903, // Brightness FX
  101: 0.154874, // Goblins FX
  102: -2.908566, // Echoes FX
  103: -3.087783, // Sci-Fi FX
  104: -4.265167, // Sitar
  105: -4.265167, // Banjo
  106: -5.041355, // Shamisen
  107: -3.472307, // Koto
  108: -3.630095, // Kalimba
  109: -7.677721, // Bagpipe
  110: -4.859421, // Fiddle
  111: -4.395996, // Shanai
  112: -1.130090, // Tinkle Bell
  113: -0.095128, // Agogo
  114: 1.012745, // Steel Drums
  115: 9.861213, // Woodblock
  116: 2.303213, // Taiko Drum
  117: 2.295818, // Melodic Tom
  118: 2.294462, // Synth Drum
  119: 4.186784, // Reverse Cymbal
  120: 11.286079, // Guitar Fret Noise
  121: 11.498750, // Breath Noise
  122: 1.904424, // Seashore
  123: -1.804450, // Bird Tweet
  124: -12.000000, // Telephone Ring
  125: -12.000000, // Helicopter
  126: 1.896695, // Applause
  127: 5.163040, // Gunshot
};

export const CHIPTUNE_DRUM_OFFSETS_DB: Readonly<Record<number, number>> = {
  35: -0.501710, // Bass kick
  36: -0.412613, // Kick
  37: 3.421948, // Side stick
  38: 6.175651, // Snare
  39: 6.345893, // Clap
  40: -0.913790, // Electric snare
  41: 6.344347, // Floor tom
  42: 1.874576, // Closed hi-hat
  43: 5.444462, // Low floor tom
  44: -0.293974, // Pedal hi-hat
  45: 4.862952, // Low tom
  46: 0.165112, // Open hi-hat
  47: 4.465748, // Low-mid tom
  48: 4.318805, // High-mid tom
  49: 2.883775, // Crash cymbal
  50: 4.080146, // High tom
  51: 0.746839, // Ride cymbal
  52: 2.854053, // Chinese cymbal
  53: -1.585968, // Ride
  54: -1.149664, // Tambourine
  55: -1.182443, // Splash cymbal
  56: 7.266282, // Cowbell
  57: 2.066733, // Crash cymbal 2
  58: -0.035580, // Vibraslap
  59: 2.590885, // Ride cymbal 2
  60: 3.660561, // High bongo
  61: 3.596404, // Low bongo
  62: 3.547281, // Muted conga
  63: 3.512188, // High conga
  64: 3.422020, // Low conga
  65: 3.401856, // High timbale
  66: 3.382752, // Low timbale
  67: 7.155992, // High agogo
  68: 7.101842, // Low agogo
  69: 5.855318, // Cabasa
  70: -2.852104, // Maracas
  71: 1.259144, // Short whistle
  72: 1.259144, // Long whistle
  73: 7.451590, // Short guiro
  74: 1.851626, // Long guiro
  75: 6.536370, // Claves
  76: 6.447960, // High wood block
  77: 6.350063, // Low wood block
  78: 3.539168, // Muted cuica
  79: 3.099054, // Open cuica
  80: 4.903968, // Muted triangle
  81: -4.598341, // Open triangle
  82: 7.770039, // Shaker
};

const gains = (offsets: Readonly<Record<number, number>>) => Array.from({ length: 128 },
  (_, key) => 10 ** ((offsets[key] ?? 0) / 20));

export const CHIPTUNE_LOUDNESS_PROFILE: LoudnessProfile = {
  melodic: gains(CHIPTUNE_MELODIC_OFFSETS_DB),
  drums: gains(CHIPTUNE_DRUM_OFFSETS_DB),
};
