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
  5: -0.651150, // Electric Piano 2
  6: -3.384769, // Harpsichord
  7: -3.478969, // Clavinet
  8: -5.040154, // Celesta
  9: -5.046729, // Glockenspiel
  10: -5.043004, // Music Box
  11: 1.012745, // Vibraphone
  12: -5.044654, // Marimba
  13: 1.012745, // Xylophone
  14: 1.022960, // Tubular Bells
  15: -4.266495, // Dulcimer
  16: -4.034145, // Drawbar Organ
  17: -2.766644, // Percussive Organ
  18: -2.760391, // Rock Organ
  19: -4.033088, // Church Organ
  20: -3.818534, // Reed Organ
  21: -6.970786, // Accordion
  22: -6.967543, // Harmonica
  23: -6.973552, // Tango Accordion
  24: 4.022167, // Nylon Guitar
  25: 4.022167, // Steel Guitar
  26: -4.648850, // Jazz Guitar
  27: -3.967939, // Clean Guitar
  28: -2.559317, // Muted Guitar
  29: -4.524109, // Overdriven Guitar
  30: -4.533795, // Distortion Guitar
  31: -3.892623, // Guitar Harmonics
  32: -2.626091, // Acoustic Bass
  33: -2.119488, // Fingered Bass
  34: -2.025083, // Picked Bass
  35: -4.398419, // Fretless Bass
  36: -2.361706, // Slap Bass 1
  37: -2.447562, // Slap Bass 2
  38: 4.839034, // Synth Bass 1
  39: -0.594414, // Synth Bass 2
  40: -5.599323, // Violin
  41: -6.163588, // Viola
  42: -4.860562, // Cello
  43: -4.857716, // Contrabass
  44: -2.239853, // Tremolo Strings
  45: 4.022167, // Pizzicato Strings
  46: -0.934177, // Harp
  47: 2.303952, // Timpani
  48: -5.177201, // Strings
  49: -4.021160, // String Ensemble 2
  50: -2.628898, // Synth Strings 1
  51: -2.636286, // Synth Strings 2
  52: -5.089248, // Choir Aahs
  53: -4.986555, // Voice Oohs
  54: -5.028424, // Synth Voice
  55: 5.938061, // Orchestra Hit
  56: 1.895048, // Trumpet
  57: -4.781829, // Trombone
  58: -4.779749, // Tuba
  59: -4.788184, // Muted Trumpet
  60: -5.409463, // French Horn
  61: -4.392918, // Brass Section
  62: -5.709260, // Synth Brass 1
  63: -0.594414, // Synth Brass 2
  64: -6.607614, // Soprano Sax
  65: -6.612316, // Alto Sax
  66: -6.605477, // Tenor Sax
  67: -6.613748, // Baritone Sax
  68: -3.430456, // Oboe
  69: -3.425490, // English Horn
  70: -3.429615, // Bassoon
  71: -2.580363, // Clarinet
  72: -3.434623, // Piccolo
  73: -4.487361, // Flute
  74: -5.496664, // Recorder
  75: -5.499229, // Pan Flute
  76: -5.493376, // Blown Bottle
  77: -3.430560, // Shakuhachi
  78: -4.987253, // Whistle
  79: -5.495848, // Ocarina
  80: -7.683412, // Square Lead
  81: -5.612307, // Sawtooth Lead
  82: -7.676264, // Calliope Lead
  83: -5.709260, // Chiff Lead
  84: -1.770582, // Charang Lead
  85: -5.407654, // Voice Lead
  86: -4.869572, // Fifths Lead
  87: -5.000725, // Bass and Lead
  88: -2.082406, // New Age Pad
  89: -2.908514, // Warm Pad
  90: -3.666610, // Polysynth Pad
  91: -2.909732, // Choir Pad
  92: -0.836521, // Bowed Pad
  93: -3.582662, // Metallic Pad
  94: -3.579714, // Halo Pad
  95: -2.236177, // Sweep Pad
  96: 5.135525, // Rain FX
  97: -3.106310, // Soundtrack FX
  98: -3.783662, // Crystal FX
  99: -4.018581, // Atmosphere FX
  100: -4.191856, // Brightness FX
  101: 0.154874, // Goblins FX
  102: -2.909461, // Echoes FX
  103: -3.084768, // Sci-Fi FX
  104: -4.273915, // Sitar
  105: -4.267966, // Banjo
  106: -5.041156, // Shamisen
  107: -3.390682, // Koto
  108: -3.629155, // Kalimba
  109: -7.682536, // Bagpipe
  110: -4.857605, // Fiddle
  111: -4.395027, // Shanai
  112: -1.132275, // Tinkle Bell
  113: -0.094734, // Agogo
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
