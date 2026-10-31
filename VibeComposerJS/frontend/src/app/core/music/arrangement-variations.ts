import type { ArrangementSection } from '../project/arrangement.model';
import type { CompositionTrack } from '../project/track.model';
import type { PartSettingsPatch } from './part-settings.model';
import type { ArrangementVariation, SectionVariations, VariationKind, VariationPolicy } from './arrangement-variation.model';
import { resolveArrangementIntent } from './arrangement-intent';
import { JavaRandom } from './java-random';

export const DEFAULT_VARIATION_POLICY: VariationPolicy = { chance: 100, maxChanges: 2 };
const KINDS: Record<CompositionTrack['role'], readonly VariationKind[]> = {
  melody: ['melody-speed', 'melody-direction'], arpeggio: ['arp-activity'], bass: ['bass-weight'],
  chords: ['chord-space'], drums: ['drum-activity'],
};
const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value));

export function validVariationPolicy(value: unknown): value is VariationPolicy {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const p = value as VariationPolicy;
  return Object.keys(p).every(key => key === 'chance' || key === 'maxChanges')
    && Number.isInteger(p.chance) && p.chance >= 0 && p.chance <= 100
    && Number.isInteger(p.maxChanges) && p.maxChanges >= 0 && p.maxChanges <= 4;
}

export function validSectionVariations(value: unknown, tracks: readonly CompositionTrack[]): value is SectionVariations {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const v = value as SectionVariations;
  if (Object.keys(v).some(key => !['seed', 'policy', 'choices'].includes(key))
    || typeof v.seed !== 'string' || !/^-?\d{1,20}$/.test(v.seed)
    || BigInt.asIntN(64, BigInt(v.seed)) !== BigInt(v.seed)
    || !validVariationPolicy(v.policy) || !Array.isArray(v.choices) || v.choices.length > 4) return false;
  const targets = new Set<string>();
  return v.choices.every(choice => {
    if (!choice || typeof choice !== 'object' || Array.isArray(choice)) return false;
    const track = tracks.find(t => t.id === choice.trackId);
    if (!track || !KINDS[track.role].includes(choice.kind) || targets.has(choice.trackId)
      || Object.keys(choice).some(key => !['trackId', 'kind', 'amount', 'enabled'].includes(key))
      || (choice.amount !== -1 && choice.amount !== 1) || typeof choice.enabled !== 'boolean') return false;
    targets.add(choice.trackId);
    return true;
  });
}

/** Small, role-specific transformations. Eligibility is rechecked after later base edits.
 * Custom and dependency-driven rhythms retain their timing and pitch vocabulary.
 */
export function variationPatch(track: CompositionTrack, choice: ArrangementVariation): PartSettingsPatch {
  if (!choice.enabled || choice.trackId !== track.id || !KINDS[track.role].includes(choice.kind)) return {};
  const s = track.generatorSettings, a = choice.amount;
  switch (choice.kind) {
    case 'melody-speed': return track.role === 'melody' && !s.customDurations?.length && track.generatorSettings.rhythm !== 'durations'
      ? { speed: clamp((track.generatorSettings.speed ?? 50) + a * 15, -100, 100) } : {};
    case 'melody-direction': return { targetDirection: a > 0 ? 'ASC' : 'DESC' };
    case 'arp-activity': {
      if (track.role !== 'arpeggio' || (track.generatorSettings.rhythm ?? 'full') !== 'full'
        || s.customDurations?.length || s.customPattern || s.useCustomVelocities) return {};
      const hits = s.hitsPerPattern ?? (track.generatorSettings.rate === 'sixteenth' ? 16 : 8);
      const steps = [1, 2, 4, 8, 16, 32];
      return { hitsPerPattern: a > 0 ? steps.find(n => n > hits) ?? hits : [...steps].reverse().find(n => n < hits) ?? hits };
    }
    case 'bass-weight': return { octaveInterval: a > 0 };
    case 'chord-space': return { voicing: a > 0 ? 'open' : 'close' };
    case 'drum-activity': return track.role === 'drums' && [42, 44, 46, 51, 53].includes(track.generatorSettings.pitch)
      && track.generatorSettings.rhythm === 'full' && !s.customPattern && !s.useCustomVelocities
      ? { pauseChance: clamp((s.pauseChance ?? 0) + a * -15, 0, 100) } : {};
  }
}

/** Stable identity draws: reordering unrelated tracks cannot consume a target's stream. */
function identitySeed(seed: bigint, id: string): bigint {
  let hash = 0n;
  for (const char of id) hash = BigInt.asIntN(64, hash * 31n + BigInt(char.charCodeAt(0)));
  return BigInt.asIntN(64, seed ^ hash ^ 0x564152494154n);
}

/** One choice per track and a shared budget coordinate activity instead of independent rolls.
 * Tension favors pulse; energy favors foundation/space; falling tension releases activity.
 */
export function planSectionVariations(tracks: readonly CompositionTrack[], sections: readonly ArrangementSection[], index: number,
  seed: bigint, policy: VariationPolicy = DEFAULT_VARIATION_POLICY): SectionVariations {
  if (!sections[index] || !validVariationPolicy(policy)) throw new RangeError('Invalid variation context or policy.');
  const section = sections[index], intent = resolveArrangementIntent(sections, index);
  const amount = intent.motion === 'release' ? -1 : intent.tension >= .7 || intent.energy >= .65 ? 1 : -1;
  const candidates = tracks.flatMap(track => {
    if (!section.trackPresence[track.id] || track.rerollLocked || section.trackPhrases?.[track.id] !== undefined
      || track.editedPhrase !== undefined && !section.generatedTrackIds?.includes(track.id)) return [];
    const random = new JavaRandom(identitySeed(seed, `${section.id}/${track.id}`));
    const effective = { ...track, generatorSettings: { ...track.generatorSettings,
      ...section.rolePartOverrides?.[track.role], ...section.trackPartOverrides?.[track.id] } } as CompositionTrack;
    const defaults: Record<string, unknown> = { speed: 50, targetDirection: 'ASC', octaveInterval: false, pauseChance: 0,
      hitsPerPattern: track.role === 'arpeggio' && track.generatorSettings.rate === 'sixteenth' ? 16 : 8 };
    const eligible = KINDS[track.role].map(kind => ({ trackId: track.id, kind,
      amount: kind === 'bass-weight' || kind === 'chord-space' ? intent.energy >= .65 ? 1 : -1 : amount,
      enabled: true } as ArrangementVariation))
      .filter(choice => {
        const patch = variationPatch(effective, choice);
        return Object.entries(patch).some(([key, value]) => !Object.hasOwn(section.rolePartOverrides?.[track.role] ?? {}, key)
          && !Object.hasOwn(section.trackPartOverrides?.[track.id] ?? {}, key)
          && ((track.generatorSettings as unknown as Record<string, unknown>)[key] ?? defaults[key]) !== value);
      });
    if (!eligible.length || random.nextInt(100) >= policy.chance) return [];
    const choice = eligible[random.nextInt(eligible.length)];
    const pulse = track.role === 'arpeggio' || track.role === 'drums' || choice.kind === 'melody-speed';
    const priority = random.nextDouble() + (intent.tension >= .7 && pulse ? 2 : 0)
      + (intent.energy >= .65 && intent.tension < .7 && (track.role === 'bass' || track.role === 'chords') ? 2 : 0);
    return [{ choice, priority }];
  });
  candidates.sort((a, b) => b.priority - a.priority || a.choice.trackId.localeCompare(b.choice.trackId));
  return { seed: BigInt.asIntN(64, seed).toString(), policy: { ...policy }, choices: candidates.slice(0, policy.maxChanges).map(c => c.choice) };
}

export function resolveVariationPatch(track: CompositionTrack, section: ArrangementSection): PartSettingsPatch {
  if (!section.variations || !section.trackPresence?.[track.id]) return {};
  const choice = section.variations?.choices.find(c => c.trackId === track.id);
  const effective = { ...track, generatorSettings: { ...track.generatorSettings,
    ...section.rolePartOverrides?.[track.role], ...section.trackPartOverrides?.[track.id] } } as CompositionTrack;
  return choice ? variationPatch(effective, choice) : {};
}

export function pruneVariations(variations: SectionVariations | undefined, removed: ReadonlySet<string>): SectionVariations | undefined {
  return variations && { ...variations, choices: variations.choices.filter(c => !removed.has(c.trackId)) };
}
