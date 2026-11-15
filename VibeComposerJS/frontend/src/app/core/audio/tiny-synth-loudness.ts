import type { LoudnessProfile } from './normalized-instrument-engine';

/** TinySynth 1.1.3, quality 1. Editable offsets in dB; programs are ZERO-BASED.
 * Positive values boost, negative values attenuate. Drums use MIDI note numbers.
 * Measured with @audio/loudness-lufs: -24 LUFS, +/-12 dB, -3 dBFS sample-peak ceiling.
 * Probe details live in instrument-calibration.ts. Noise RNG seed: 0x51f15e.
 * Regenerate: pnpm run calibrate:tinysynth (OVERWRITES manual edits).
 * Runtime always uses this file; old browser calibration caches are ignored. */
export const TINY_SYNTH_MELODIC_OFFSETS_DB: Readonly<Record<number, number>> = {
  0: -4.161412, // Acoustic Grand Piano
  1: -3.105747, // Bright Acoustic Piano
  2: -5.856686, // Electric Grand Piano
  3: 0.950854, // Honky-tonk Piano
  4: -3.914917, // Electric Piano 1
  5: -5.221599, // Electric Piano 2
  6: -5.441173, // Harpsichord
  7: -5.683643, // Clavinet
  8: -4.825006, // Celesta
  9: -7.134232, // Glockenspiel
  10: 1.197067, // Music Box
  11: 0.960500, // Vibraphone
  12: 0.138567, // Marimba
  13: 0.207110, // Xylophone
  14: -1.117928, // Tubular Bells
  15: 3.267626, // Dulcimer
  16: -4.185954, // Drawbar Organ
  17: -4.372614, // Percussive Organ
  18: -2.446265, // Rock Organ
  19: -5.519556, // Church Organ
  20: -5.445784, // Reed Organ
  21: -3.246517, // Accordion
  22: -0.908725, // Harmonica
  23: -2.729713, // Tango Accordion
  24: -2.102746, // Nylon Guitar
  25: -6.382918, // Steel Guitar
  26: -2.415689, // Jazz Guitar
  27: -5.110064, // Clean Guitar
  28: -1.099544, // Muted Guitar
  29: -5.250079, // Overdriven Guitar
  30: -4.626072, // Distortion Guitar
  31: -3.580008, // Guitar Harmonics
  32: -5.826851, // Acoustic Bass
  33: -5.826851, // Fingered Bass
  34: -7.663110, // Picked Bass
  35: -5.826851, // Fretless Bass
  36: -2.657430, // Slap Bass 1
  37: -2.657430, // Slap Bass 2
  38: -5.519602, // Synth Bass 1
  39: -5.519602, // Synth Bass 2
  40: -7.141038, // Violin
  41: -7.141038, // Viola
  42: -7.161756, // Cello
  43: -7.161756, // Contrabass
  44: -11.230233, // Tremolo Strings
  45: 0.346369, // Pizzicato Strings
  46: -3.207669, // Harp
  47: -4.629644, // Timpani
  48: -6.031346, // Strings
  49: -6.045567, // String Ensemble 2
  50: -3.293547, // Synth Strings 1
  51: -4.276580, // Synth Strings 2
  52: -6.071385, // Choir Aahs
  53: -8.928490, // Voice Oohs
  54: -5.657543, // Synth Voice
  55: -0.640116, // Orchestra Hit
  56: -4.873031, // Trumpet
  57: -4.482320, // Trombone
  58: -4.159992, // Tuba
  59: -3.402671, // Muted Trumpet
  60: -4.482320, // French Horn
  61: -4.947315, // Brass Section
  62: -4.706311, // Synth Brass 1
  63: -4.482320, // Synth Brass 2
  64: -4.198715, // Soprano Sax
  65: -4.198715, // Alto Sax
  66: -3.525943, // Tenor Sax
  67: -3.525943, // Baritone Sax
  68: -8.111717, // Oboe
  69: -5.537732, // English Horn
  70: -3.498330, // Bassoon
  71: -5.021200, // Clarinet
  72: -9.426235, // Piccolo
  73: -10.605786, // Flute
  74: -12.000000, // Recorder
  75: -5.028431, // Pan Flute
  76: -5.247911, // Blown Bottle
  77: -8.292052, // Shakuhachi
  78: -10.225680, // Whistle
  79: -12.000000, // Ocarina
  80: -6.891909, // Square Lead
  81: -3.594731, // Sawtooth Lead
  82: -9.292748, // Calliope Lead
  83: -5.368550, // Chiff Lead
  84: -3.168894, // Charang Lead
  85: -6.634824, // Voice Lead
  86: -3.794666, // Fifths Lead
  87: -4.398583, // Bass and Lead
  88: -0.364749, // New Age Pad
  89: -4.753495, // Warm Pad
  90: -4.770720, // Polysynth Pad
  91: -3.006444, // Choir Pad
  92: -5.090397, // Bowed Pad
  93: -5.242040, // Metallic Pad
  94: -5.380213, // Halo Pad
  95: -5.396406, // Sweep Pad
  96: -4.583158, // Rain FX
  97: -5.964913, // Soundtrack FX
  98: -6.798452, // Crystal FX
  99: -4.355633, // Atmosphere FX
  100: -5.382680, // Brightness FX
  101: -6.820841, // Goblins FX
  102: -9.319615, // Echoes FX
  103: -2.982072, // Sci-Fi FX
  104: 1.042617, // Sitar
  105: -2.173918, // Banjo
  106: -4.525159, // Shamisen
  107: -4.827524, // Koto
  108: -2.999539, // Kalimba
  109: -6.835410, // Bagpipe
  110: -8.517355, // Fiddle
  111: -8.543025, // Shanai
  112: -3.149491, // Tinkle Bell
  113: -8.923301, // Agogo
  114: -8.019340, // Steel Drums
  115: -7.526736, // Woodblock
  116: -4.986270, // Taiko Drum
  117: -6.663865, // Melodic Tom
  118: -5.540098, // Synth Drum
  119: -1.866124, // Reverse Cymbal
  120: -7.446182, // Guitar Fret Noise
  121: 5.627229, // Breath Noise
  122: -4.472088, // Seashore
  123: -6.726220, // Bird Tweet
  124: -10.181495, // Telephone Ring
  125: -4.958801, // Helicopter
  126: -12.000000, // Applause
  127: -12.000000, // Gunshot
};

export const TINY_SYNTH_DRUM_OFFSETS_DB: Readonly<Record<number, number>> = {
  35: -5.477194, // Bass kick
  36: -5.777711, // Kick
  37: 3.092439, // Side stick
  38: -0.797016, // Snare
  39: -2.855581, // Clap
  40: -5.983401, // Electric snare
  41: -7.115573, // Floor tom
  42: 0.883174, // Closed hi-hat
  43: -7.179226, // Low floor tom
  44: 0.371756, // Pedal hi-hat
  45: -7.581548, // Low tom
  46: -1.428434, // Open hi-hat
  47: -7.618382, // Low-mid tom
  48: -7.672580, // High-mid tom
  49: -12.889628, // Crash cymbal
  50: -7.847337, // High tom
  51: 0.726113, // Ride cymbal
  52: -1.533718, // Chinese cymbal
  53: 1.653501, // Ride
  54: -1.149664, // Tambourine
  55: -13.154643, // Splash cymbal
  56: -0.316625, // Cowbell
  57: -11.469274, // Crash cymbal 2
  58: 0.908421, // Vibraslap
  59: -1.959977, // Ride cymbal 2
  60: -1.650462, // High bongo
  61: -1.399723, // Low bongo
  62: 1.351751, // Muted conga
  63: -1.838521, // High conga
  64: -1.770391, // Low conga
  65: 0.627469, // High timbale
  66: 0.721839, // Low timbale
  67: -2.858978, // High agogo
  68: -2.769184, // Low agogo
  69: 7.999035, // Cabasa
  70: 8.067184, // Maracas
  71: -1.278074, // Short whistle
  72: 0.983713, // Long whistle
  73: 4.379799, // Short guiro
  74: 4.285373, // Long guiro
  75: 3.960927, // Claves
  76: 0.913869, // High wood block
  77: 1.037894, // Low wood block
  78: 3.539168, // Muted cuica
  79: 2.335908, // Open cuica
  80: 1.783039, // Muted triangle
  81: -5.010875, // Open triangle
  82: 8.201424, // Shaker
};

const gains = (offsets: Readonly<Record<number, number>>) => Array.from({ length: 128 },
  (_, key) => 10 ** ((offsets[key] ?? 0) / 20));

export const TINY_SYNTH_LOUDNESS_PROFILE: LoudnessProfile = {
  melodic: gains(TINY_SYNTH_MELODIC_OFFSETS_DB),
  drums: gains(TINY_SYNTH_DRUM_OFFSETS_DB),
};
