import lufs from '@audio/loudness-lufs';
import type { InstrumentEngine, InstrumentVoice } from './instrument-engine';
import type { Sf2Bank } from './sf2-resolver';
import type { LoudnessProfile } from './normalized-instrument-engine';
import { TinySynthEngine } from './tiny-synth-engine';

// Bump when probe, renderer dependencies, target, or gain policy changes.
const CACHE_KEY = 'vibecomposer.instrument-loudness.v1';
const RATE = 24000;
const TARGET_LUFS = -24;
const MAX_GAIN = 10 ** (12 / 20);
const MIN_GAIN = 10 ** (-12 / 20);
const PEAK_CEILING = 10 ** (-3 / 20);
const banks = new WeakMap<Sf2Bank, Promise<LoudnessProfile>>();

export function calibrationGain(level: number | null, peak: number): number {
  if (level === null || !Number.isFinite(level) || !Number.isFinite(peak) || peak <= 0) return 1;
  return Math.min(MAX_GAIN, Math.max(MIN_GAIN, 10 ** ((TARGET_LUFS - level) / 20)), PEAK_CEILING / peak);
}

function validProfile(value: unknown): value is LoudnessProfile {
  const p = value as LoudnessProfile | null;
  return !!p && [p.melodic, p.drums].every(a => Array.isArray(a) && a.length === 128
    && a.every(g => typeof g === 'number' && Number.isFinite(g) && g > 0 && g <= MAX_GAIN));
}
function readCache(): Record<string, LoudnessProfile> {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(CACHE_KEY) ?? '{}');
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    return Object.fromEntries(Object.entries(parsed).filter(([, value]) => validProfile(value)));
  } catch { return {}; }
}

/** Content-addressed persistent cache plus in-flight deduplication. Failure is retryable. */
export function calibrateSoundfont(bank: Sf2Bank, progress?: (message: string) => void): Promise<LoudnessProfile> {
  const existing = banks.get(bank);
  if (existing) return existing;
  const pending = (async () => {
    progress?.('Preparing instrument loudness calibration…');
    const key = await bank.contentHash();
    const cached = readCache()[key];
    if (cached) return cached;
    const profile = await measureInstrumentLoudness(bank, progress);
    // Keep at most eight small profiles; storage denial never prevents playback.
    try {
      const entries = Object.entries(readCache()).filter(([id]) => id !== key);
      localStorage.setItem(CACHE_KEY, JSON.stringify(Object.fromEntries([...entries.slice(-7), [key, profile]])));
    } catch { /* Memory cache still works. */ }
    return profile;
  })();
  banks.set(bank, pending);
  void pending.catch(() => banks.delete(bank));
  return pending;
}

/** Raw measurement, also used by the explicit TinySynth precomputation script. */
export async function measureInstrumentLoudness(bank?: Sf2Bank, progress?: (message: string) => void,
  chiptune = false): Promise<LoudnessProfile> {
  const patches = chiptune ? (await import('./chiptune-patches')).CHIPTUNE_PATCHES : undefined;
  const melodic = Array<number>(128).fill(1), drums = Array<number>(128).fill(1);
  const programs = bank ? [...new Set(bank.presets.filter(p => p.bank === 0 && p.regions.length).map(p => p.program))]
    .filter(p => p >= 0 && p < 128) : Array.from({ length: 128 }, (_, i) => i);
  const kit = bank?.presets.find(p => p.bank === 128 && p.program === 0);
  const notes = bank ? Array.from({ length: 128 }, (_, i) => i).filter(note => kit?.regions.some(r =>
    note >= r.keyRange![0] && note <= r.keyRange![1] && 100 >= r.velRange![0] && 100 <= r.velRange![1]))
    : Array.from({ length: 47 }, (_, i) => i + 35);
  // Both renderers map absent shaker 82 to maracas 70.
  if ((!bank || kit) && !notes.includes(82) && notes.includes(70)) notes.push(82);
  const probes = [...programs.map(program => ({ program, percussion: false, midi: 60 })),
    ...notes.map(midi => ({ program: 0, percussion: true, midi }))];
  const Sf2Engine = bank ? (await import('./sf2-instrument-engine')).Sf2InstrumentEngine : undefined;
  for (let index = 0; index < probes.length; index++) {
    const { program, percussion, midi } = probes[index];
    progress?.(`Calibrating instrument loudness: ${index + 1}/${probes.length}`);
    await new Promise<void>(resolve => setTimeout(resolve, 0));
    const context = new OfflineAudioContext(2, RATE * (percussion ? 2.5 : 6), RATE);
    const engine: InstrumentEngine = bank && Sf2Engine ? new Sf2Engine(context, bank) : new TinySynthEngine(context, patches);
    const voices: InstrumentVoice[] = [];
    try {
      await engine.prepare();
      const pitches = percussion ? [midi, midi, midi, midi] : [48, 60, 72];
      pitches.forEach((pitch, i) => voices.push(...engine.schedule(context.destination, program, percussion,
        pitch, 100, 0.05 + i * (percussion ? 0.5 : 2), percussion ? 0.2 : 1.2)));
      await engine.flush();
      const buffer = await context.startRendering();
      const channels = [buffer.getChannelData(0), buffer.getChannelData(1)];
      let peak = 0;
      for (const channel of channels) for (const value of channel) peak = Math.max(peak, Math.abs(value));
      (percussion ? drums : melodic)[percussion ? midi : program] = calibrationGain(lufs(channels, { fs: RATE }), peak);
    } finally {
      for (const voice of voices) voice.dispose();
      engine.dispose();
    }
  }
  return { melodic, drums };
}
