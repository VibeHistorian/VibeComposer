import type { MelodySettings, BassSettings, ChordSettings, ArpeggioSettings, DrumSettings } from './project.model';
import { decodeCommonPartSettings, decodePartPatch, validVelocityRange } from '../music/part-settings';
import { BASS_RHYTHMS, RHYTHM_PATTERNS } from '../music/rhythm-patterns';
import { ARPEGGIO_PATTERNS } from '../music/arpeggio-pattern';
import { isDrumPitch } from '../music/drum-instruments';

const CHORD_VOICINGS = ['close', 'open'] as const;
const ARPEGGIO_RATES = ['eighth', 'sixteenth'] as const;

export function decodeMelody(value: unknown): MelodySettings | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)
      || (value as MelodySettings).algorithm !== 'block') return undefined;
  const { algorithm, ...fields } = value as MelodySettings;
  const decoded = decodePartPatch('melody', fields);
  return decoded && validVelocityRange(decoded, 'melody') ? { algorithm, ...decoded } as MelodySettings : undefined;
}

export function decodeBass(value: unknown): BassSettings | undefined {
  const common = decodeCommonPartSettings('bass', value);
  if (!common) return undefined;
  if (!value || typeof value !== 'object') {
    return undefined;
  }
  const bass = value as Partial<BassSettings>;
  if (!BASS_RHYTHMS.includes(bass.rhythm as typeof BASS_RHYTHMS[number])
      || !Number.isInteger(bass.noteVariation) || (bass.noteVariation ?? -1) < 0 || (bass.noteVariation ?? 101) > 100
      || (bass.octaveInterval !== undefined && typeof bass.octaveInterval !== 'boolean')) {
    return undefined;
  }
  return { ...common, rhythm: bass.rhythm!, noteVariation: bass.noteVariation!, octaveInterval: bass.octaveInterval ?? false };
}

export function decodeChords(value: unknown): ChordSettings | undefined {
  const common = decodeCommonPartSettings('chords', value);
  if (!common) return undefined;
  if (!value || typeof value !== 'object') {
    return undefined;
  }
  const chords = value as Partial<ChordSettings>;
  if (!RHYTHM_PATTERNS.includes(chords.rhythm as typeof RHYTHM_PATTERNS[number])
      || !CHORD_VOICINGS.includes(chords.voicing as typeof CHORD_VOICINGS[number])
      || !Number.isInteger(chords.noteLengthPercent)
      || (chords.noteLengthPercent ?? 0) < 25 || (chords.noteLengthPercent ?? 201) > 200) {
    return undefined;
  }
  return {
    ...common,
    rhythm: chords.rhythm!,
    voicing: chords.voicing!,
    noteLengthPercent: chords.noteLengthPercent!,
  };
}

export function decodeArpeggio(value: unknown): ArpeggioSettings | undefined {
  const common = decodeCommonPartSettings('arpeggio', value);
  if (!common) return undefined;
  if (!value || typeof value !== 'object') {
    return undefined;
  }
  const arpeggio = value as Partial<ArpeggioSettings>;
  if (!ARPEGGIO_PATTERNS.includes(arpeggio.pattern as typeof ARPEGGIO_PATTERNS[number])
      || !ARPEGGIO_RATES.includes(arpeggio.rate as typeof ARPEGGIO_RATES[number])
      || (arpeggio.octaves !== 1 && arpeggio.octaves !== 2)) {
    return undefined;
  }
  return { ...common, pattern: arpeggio.pattern!, rate: arpeggio.rate!, octaves: arpeggio.octaves! };
}

export function decodeDrums(value: unknown): DrumSettings | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const { pitch, ...fields } = value as DrumSettings;
  const decoded = decodePartPatch('drums', fields);
  return isDrumPitch(pitch) && decoded && fields.rhythm && fields.swingPercent !== undefined
    && validVelocityRange(decoded, 'drums') ? { pitch, ...decoded } as DrumSettings : undefined;
}
