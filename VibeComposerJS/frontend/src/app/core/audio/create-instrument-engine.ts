import type { InstrumentEngine, InstrumentEngineKind } from './instrument-engine';
import type { Sf2Bank } from './sf2-resolver';
import { TinySynthEngine } from './tiny-synth-engine';
export async function createInstrumentEngine(context: BaseAudioContext, kind: InstrumentEngineKind, bank?: Sf2Bank,
  normalize = false, progress?: (message: string) => void): Promise<InstrumentEngine> {
  if (kind !== 'synth' && !bank) throw new Error('The selected soundfont is no longer loaded.');
  // Complete calibration before creating the playback/export graph. All callers
  // receive the same fixed correction table, never a partially calibrated bank.
  const calibration = normalize ? await import('./instrument-loudness') : undefined;
  const profile = calibration ? await calibration.calibrateInstruments(kind === 'synth' ? undefined : bank, progress) : undefined;
  const engine = kind !== 'synth'
    ? new (await import('./sf2-instrument-engine')).Sf2InstrumentEngine(context, bank!) : new TinySynthEngine(context);
  try {
    await engine.prepare();
    if (!profile) return engine;
    const { NormalizedInstrumentEngine } = await import('./normalized-instrument-engine');
    return new NormalizedInstrumentEngine(engine, profile);
  }
  catch (error) { engine.dispose(); throw error; }
}
