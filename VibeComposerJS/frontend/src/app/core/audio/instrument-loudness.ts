import type { Sf2Bank } from './sf2-resolver';
import type { LoudnessProfile } from './normalized-instrument-engine';
import type { InstrumentEngineKind } from './instrument-engine';

/** TinySynth always uses the editable bundled table, never a browser cache. */
export async function calibrateInstruments(bank?: Sf2Bank, progress?: (message: string) => void,
  kind: InstrumentEngineKind = 'synth'): Promise<LoudnessProfile> {
  if (kind === 'chiptune') return (await import('./chiptune-loudness')).CHIPTUNE_LOUDNESS_PROFILE;
  if (!bank) return (await import('./tiny-synth-loudness')).TINY_SYNTH_LOUDNESS_PROFILE;
  return (await import('./instrument-calibration')).calibrateSoundfont(bank, progress);
}
