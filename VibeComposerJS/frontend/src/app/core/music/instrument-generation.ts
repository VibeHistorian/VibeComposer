import { ARRANGED_PARTS, type ArrangedPart, type CompositionTrack, type DrumSettings,
  type InstrumentGenerationPolicy, type InstrumentPool } from '../project/project.model';
import { JavaRandom } from './java-random';
import { DRUM_INSTRUMENTS } from './drum-instruments';

/** Ordered production InstUtils catalogues. ALL excludes Java's effects/noise programs 120–127. */
export const INSTRUMENT_POOLS: Readonly<Record<Exclude<InstrumentPool, 'AUTO'>, readonly number[]>> = {
  ALL: Array.from({ length: 120 }, (_, index) => index),
  MELODY: [0,1,3,4,5,6,7,8,10,11,12,14,16,17,18,20,21,23,24,25,26,28,29,32,33,34,36,37,38,41,42,43,44,45,46,48,49,50,52,53,54,56,57,58,59,60,63,64,69,70,71,72,73,74,75,76,77,78,79,80,81,82,85,87,88,89,90,91,92,93,94,96,98,99,100,103,105,106,107,108],
  BASS: [0,1,4,53,54,73,74,75,82,85,91,102],
  CHORD: [4,16,17,32,44,48,49,52,70],
  PLUCK: [1,3,5,8,10,11,12,13,15,25,27,32,33,34,35,36,45,46,88],
  LONG_PAD: [16,17,20,21,22,23,40,41,42,43,44,48,49,51,56,57,58,60,61,64,65,67,68,69,70,71,72,74,76,91,92,93,94,95],
  DRUM: DRUM_INSTRUMENTS.map(instrument => instrument.midi),
};
export const DEFAULT_INSTRUMENT_POLICIES: Readonly<Record<ArrangedPart, InstrumentGenerationPolicy>> = {
  melody: { randomize: true, pool: 'MELODY' }, bass: { randomize: true, pool: 'BASS' },
  chords: { randomize: true, pool: 'AUTO', sustainChance: 50 },
  arpeggio: { randomize: true, pool: 'PLUCK', sameInstrument: false },
  drums: { randomize: true, pool: 'DRUM' },
};
export function instrumentPoolOptions(role: ArrangedPart): readonly InstrumentPool[] {
  return role === 'drums' ? ['DRUM'] : [...(role === 'chords' ? ['AUTO' as const] : []),
    'ALL', 'MELODY', 'BASS', 'CHORD', 'PLUCK', 'LONG_PAD'];
}
export function decodeInstrumentPolicy(role: ArrangedPart, value: unknown): InstrumentGenerationPolicy | undefined {
  if (!ARRANGED_PARTS.includes(role) || !value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const policy = value as InstrumentGenerationPolicy;
  const keys = Object.keys(DEFAULT_INSTRUMENT_POLICIES[role]);
  if (Object.keys(value).length !== keys.length || Object.keys(value).some(key => !keys.includes(key))
    || typeof policy.randomize !== 'boolean' || !instrumentPoolOptions(role).includes(policy.pool)
    || (role === 'arpeggio' && typeof policy.sameInstrument !== 'boolean')
    || (role === 'chords' && (!Number.isInteger(policy.sustainChance) || policy.sustainChance! < 0 || policy.sustainChance! > 100))) return undefined;
  return { ...policy };
}
export function validInstrumentPolicies(value: unknown): boolean {
  return value === undefined || !!value && typeof value === 'object' && !Array.isArray(value)
    && Object.entries(value).every(([role, policy]) => !!decodeInstrumentPolicy(role as ArrangedPart, policy));
}

/** Separate deterministic stream: instrument permission never shifts parameter reroll draws.
 * Java uses unseeded Random here; pool membership/order is ported, not full GUI draw parity.
 * Shared Arp instruments apply to eligible targets only; locked/non-target tracks never anchor them.
 */
export function rerollInstruments(seed: bigint, role: ArrangedPart, policy: InstrumentGenerationPolicy,
  tracks: readonly CompositionTrack[], force = false): CompositionTrack[] {
  if (!decodeInstrumentPolicy(role, policy) || tracks.some(track => track.role !== role)) throw new RangeError('Invalid instrument reroll.');
  if (!force && !policy.randomize) return [...tracks];
  const random = new JavaRandom(BigInt.asIntN(64, seed ^ 0x494e535452554d45n));
  let shared: number | undefined;
  return tracks.map(track => {
    if (track.rerollLocked || track.instrumentLocked) return track;
    const pool = policy.pool === 'AUTO' ? random.nextInt(100) < policy.sustainChance! ? 'CHORD' : 'PLUCK' : policy.pool;
    const candidates = INSTRUMENT_POOLS[pool];
    const instrument = shared ?? candidates[random.nextInt(candidates.length)];
    if (role === 'arpeggio' && policy.sameInstrument) shared = instrument;
    if (role === 'drums') return (track.generatorSettings as DrumSettings).pitch === instrument ? track
      : { ...track, generatorSettings: { ...track.generatorSettings, pitch: instrument } as DrumSettings } as CompositionTrack;
    return track.mix.program === instrument && track.instrumentPool === pool ? track
      : { ...track, instrumentPool: pool, mix: { ...track.mix, program: instrument } };
  });
}
