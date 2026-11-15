import type { Sf2Bank } from './sf2-resolver';
import type { LoudnessProfile } from './normalized-instrument-engine';

/** TinySynth always uses the editable bundled table, never a browser cache. */
export async function calibrateInstruments(bank?: Sf2Bank, progress?: (message: string) => void): Promise<LoudnessProfile> {
  if (!bank) return (await import('./tiny-synth-loudness')).TINY_SYNTH_LOUDNESS_PROFILE;
  return (await import('./instrument-calibration')).calibrateSoundfont(bank, progress);
}
