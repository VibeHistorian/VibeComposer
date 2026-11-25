import type { InstrumentEngineKind } from './instrument-engine';

export const INSTRUMENT_TONE_CONTROLS = [
  { key: 'attackMs', label: 'Attack', unit: ' ms', maximum: 40000 },
  { key: 'decayMs', label: 'Decay', unit: ' ms', maximum: 60000 },
  { key: 'sustainPercent', label: 'Sustain', unit: '%', maximum: 200 },
  { key: 'releaseMs', label: 'Release', unit: ' ms', maximum: 40000 },
  { key: 'vibratoCents', label: 'Vibrato', unit: ' cents', maximum: 100 },
  { key: 'tremoloPercent', label: 'Tremolo', unit: '%', maximum: 100 },
] as const;

export type InstrumentToneKey = typeof INSTRUMENT_TONE_CONTROLS[number]['key'];
export type InstrumentTone = Readonly<Partial<Record<InstrumentToneKey, number>>>;
export interface TrackInstrumentTone extends InstrumentTone {
  readonly soundbankName: string;
}

export const TONE_MODULATION_HZ = 5;

export function validInstrumentToneValue(key: InstrumentToneKey, value: unknown): value is number {
  const control = INSTRUMENT_TONE_CONTROLS.find(control => control.key === key);
  return control !== undefined && typeof value === 'number' && Number.isFinite(value)
    && value >= 0 && value <= control.maximum;
}

export function decodeTrackInstrumentTone(value: unknown): TrackInstrumentTone | null | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const { soundbankName, delayMs, ...overrides } = value as TrackInstrumentTone & { delayMs?: unknown };
  const entries = Object.entries(overrides);
  const legacyDelay = typeof delayMs === 'number' && delayMs >= 0 && delayMs <= 40000;
  if (typeof soundbankName !== 'string' || !soundbankName || (Object.hasOwn(value, 'delayMs') && !legacyDelay)
    || (!entries.length && !legacyDelay)
    || !entries.every(([key, amount]) => validInstrumentToneValue(key as InstrumentToneKey, amount))) return undefined;
  // A saved onset delay has no equivalent decay time.
  return entries.length ? { soundbankName, ...overrides } : null;
}

export function instrumentToneForBank(tone: TrackInstrumentTone | undefined, kind: InstrumentEngineKind | undefined,
  soundbankName: string | undefined): InstrumentTone | undefined {
  const bank = kind?.startsWith('sf2:') ? soundbankName : kind;
  return tone && tone.soundbankName === bank ? tone : undefined;
}
