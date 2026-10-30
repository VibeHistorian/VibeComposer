import type { InstrumentEngine, InstrumentEngineKind } from './instrument-engine';
import type { Sf2Bank } from './sf2-resolver';
import { TinySynthEngine } from './tiny-synth-engine';
export async function createInstrumentEngine(context: BaseAudioContext, kind: InstrumentEngineKind, bank?: Sf2Bank): Promise<InstrumentEngine> {
  if (kind !== 'synth' && !bank) throw new Error('The selected soundfont is no longer loaded.');
  const engine = kind !== 'synth'
    ? new (await import('./sf2-instrument-engine')).Sf2InstrumentEngine(context, bank!) : new TinySynthEngine(context);
  try { await engine.prepare(); return engine; }
  catch (error) { engine.dispose(); throw error; }
}
