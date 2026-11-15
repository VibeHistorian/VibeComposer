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
  5: -0.651405, // Electric Piano 2
  6: -3.474548, // Harpsichord
  7: -3.474548, // Clavinet
  8: -5.042472, // Celesta
  9: -5.042472, // Glockenspiel
  10: -5.042472, // Music Box
  11: 1.012745, // Vibraphone
  12: -5.042472, // Marimba
  13: 1.012745, // Xylophone
  14: 1.022960, // Tubular Bells
  15: -4.267966, // Dulcimer
  16: -4.037617, // Drawbar Organ
  17: -2.759162, // Percussive Organ
  18: -2.759162, // Rock Organ
  19: -4.037617, // Church Organ
  20: -3.818534, // Reed Organ
  21: -6.970786, // Accordion
  22: -6.970786, // Harmonica
  23: -6.970786, // Tango Accordion
  24: 4.022167, // Nylon Guitar
  25: 4.022167, // Steel Guitar
  26: -4.648946, // Jazz Guitar
  27: -3.970509, // Clean Guitar
  28: -2.557342, // Muted Guitar
  29: -4.528013, // Overdriven Guitar
  30: -4.528013, // Distortion Guitar
  31: -3.891490, // Guitar Harmonics
  32: -2.627793, // Acoustic Bass
  33: -2.029893, // Fingered Bass
  34: -2.029893, // Picked Bass
  35: -4.394177, // Fretless Bass
  36: -2.362187, // Slap Bass 1
  37: -2.362187, // Slap Bass 2
  38: 4.839034, // Synth Bass 1
  39: -0.594414, // Synth Bass 2
  40: -5.602228, // Violin
  41: -6.164916, // Viola
  42: -4.860562, // Cello
  43: -4.860562, // Contrabass
  44: -2.237684, // Tremolo Strings
  45: 4.022167, // Pizzicato Strings
  46: -0.932496, // Harp
  47: 2.303952, // Timpani
  48: -5.178904, // Strings
  49: -4.019693, // String Ensemble 2
  50: -2.542565, // Synth Strings 1
  51: -2.542565, // Synth Strings 2
  52: -5.088806, // Choir Aahs
  53: -4.986763, // Voice Oohs
  54: -5.026834, // Synth Voice
  55: 5.938061, // Orchestra Hit
  56: 1.895048, // Trumpet
  57: -4.782702, // Trombone
  58: -4.782702, // Tuba
  59: -4.782702, // Muted Trumpet
  60: -5.408865, // French Horn
  61: -4.398968, // Brass Section
  62: -5.703852, // Synth Brass 1
  63: -0.594414, // Synth Brass 2
  64: -6.606295, // Soprano Sax
  65: -6.606295, // Alto Sax
  66: -6.606295, // Tenor Sax
  67: -6.606295, // Baritone Sax
  68: -3.429940, // Oboe
  69: -3.429940, // English Horn
  70: -3.429940, // Bassoon
  71: -2.580363, // Clarinet
  72: -3.429940, // Piccolo
  73: -4.487361, // Flute
  74: -5.497008, // Recorder
  75: -5.497008, // Pan Flute
  76: -5.497008, // Blown Bottle
  77: -3.429940, // Shakuhachi
  78: -4.986763, // Whistle
  79: -5.497008, // Ocarina
  80: -7.678907, // Square Lead
  81: -5.609802, // Sawtooth Lead
  82: -7.678907, // Calliope Lead
  83: -5.703852, // Chiff Lead
  84: -1.770582, // Charang Lead
  85: -5.408865, // Voice Lead
  86: -4.867573, // Fifths Lead
  87: -5.004711, // Bass and Lead
  88: -2.309205, // New Age Pad
  89: -2.908862, // Warm Pad
  90: -3.672667, // Polysynth Pad
  91: -2.908862, // Choir Pad
  92: -0.839332, // Bowed Pad
  93: -3.579099, // Metallic Pad
  94: -3.579099, // Halo Pad
  95: -2.237684, // Sweep Pad
  96: 5.135525, // Rain FX
  97: -3.112298, // Soundtrack FX
  98: -3.780868, // Crystal FX
  99: -4.019693, // Atmosphere FX
  100: -4.194143, // Brightness FX
  101: 0.154874, // Goblins FX
  102: -2.908862, // Echoes FX
  103: -3.090667, // Sci-Fi FX
  104: -4.267966, // Sitar
  105: -4.267966, // Banjo
  106: -5.042472, // Shamisen
  107: -3.474548, // Koto
  108: -3.631094, // Kalimba
  109: -7.678907, // Bagpipe
  110: -4.860562, // Fiddle
  111: -4.398968, // Shanai
  112: -1.131089, // Tinkle Bell
  113: -0.095927, // Agogo
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
  57: 2.066734, // Crash cymbal 2
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
