import type { ArrangementSection } from '../project/arrangement.model';
import type { CompositionTrack } from '../project/track.model';
import type { MelodyGlobalSettings, PartSettingsPatch } from './part-settings.model';
import type { ArrangementVariation, SectionVariations, VariationKind, VariationPolicy } from './arrangement-variation.model';
import { resolveVariationContext, variationAmountForContext, variationPriority, isActivityVariation, isRegisterVariation, VARIATION_GESTURE_LABELS } from './arrangement-variation-context';
import { JavaRandom } from './java-random';
import { VELOCITY_ENVELOPE_KEYS, velocityEnvelopeEligibility } from './arrangement-velocity-envelope';
import { arpEndFillEligibility } from './arrangement-end-fill';
import { PRE_DROP_CUT_KEYS, preDropCutEligibility } from './arrangement-boundary-events';
import { rhythmVariationPatch, rhythmVariationProtectedKeys } from './arrangement-rhythm-variations';

export const DEFAULT_VARIATION_POLICY: VariationPolicy = { chance: 100, maxChanges: 2 };
const KINDS: Record<CompositionTrack['role'], readonly VariationKind[]> = {
  melody: ['melody-speed', 'melody-direction', 'melody-register', 'melody-range'], arpeggio: ['arp-activity', 'arp-density', 'arp-contour', 'arp-register', 'pre-drop-cut', 'arp-end-fill', 'accompaniment-velocity-ramp'], bass: ['bass-weight'],
  chords: ['chord-space', 'chord-transition', 'pre-drop-cut', 'accompaniment-velocity-ramp'], drums: ['drum-activity', 'drum-density', 'drum-ghosts'],
};
const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value));

/** These operations must remain atomic, including controls that define their interpretation. */
export function variationProtectedKeys(kind: VariationKind): readonly string[] {
  if (kind === 'accompaniment-velocity-ramp') return VELOCITY_ENVELOPE_KEYS;
  if (kind === 'pre-drop-cut' || kind === 'arp-end-fill') return PRE_DROP_CUT_KEYS;
  if (kind === 'arp-contour') return ['arpContour', 'arpContourChordMode'];
  if (kind === 'melody-register' || kind === 'melody-range') return ['transpose', 'rangeMin', 'rangeMax'];
  if (kind === 'arp-register') return ['transpose', 'arpContour', 'arpPatternCustom', 'useOctaveAdjustments', 'stretchEnabled'];
  return rhythmVariationProtectedKeys(kind);
}

export function variationBlockedKeys(section: ArrangementSection, track: CompositionTrack, kind: VariationKind): readonly string[] {
  return variationProtectedKeys(kind).filter(key => Object.hasOwn(section.rolePartOverrides?.[track.role] ?? {}, key)
    || Object.hasOwn(section.trackPartOverrides?.[track.id] ?? {}, key));
}

export const maximumSectionVariations = (trackCount: number): number => Math.max(6, Math.ceil(trackCount / 2));

export function validVariationPolicy(value: unknown, trackCount = 0): value is VariationPolicy {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const p = value as VariationPolicy;
  return Object.keys(p).every(key => key === 'chance' || key === 'maxChanges')
    && Number.isInteger(p.chance) && p.chance >= 0 && p.chance <= 100
    && Number.isInteger(p.maxChanges) && p.maxChanges >= 0 && p.maxChanges <= maximumSectionVariations(trackCount);
}

export function validSectionVariations(value: unknown, tracks: readonly CompositionTrack[]): value is SectionVariations {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const v = value as SectionVariations;
  if (Object.keys(v).some(key => !['seed', 'policy', 'choices', 'gesture'].includes(key))
    || v.gesture !== undefined && (typeof v.gesture !== 'string' || !Object.hasOwn(VARIATION_GESTURE_LABELS, v.gesture))
    || typeof v.seed !== 'string' || !/^-?\d{1,20}$/.test(v.seed)
    || BigInt.asIntN(64, BigInt(v.seed)) !== BigInt(v.seed)
    || !validVariationPolicy(v.policy, tracks.length) || !Array.isArray(v.choices) || v.choices.length > maximumSectionVariations(tracks.length)) return false;
  const targets = new Set<string>();
  return v.choices.every(choice => {
    if (!choice || typeof choice !== 'object' || Array.isArray(choice)) return false;
    const track = tracks.find(t => t.id === choice.trackId);
    if (!track || !KINDS[track.role].includes(choice.kind) || targets.has(choice.trackId)
      || Object.keys(choice).some(key => !['trackId', 'kind', 'amount', 'enabled'].includes(key))
      || choice.kind === 'arp-end-fill' && choice.amount !== 1
      || choice.kind === 'pre-drop-cut' && choice.amount !== -1
      || (choice.amount !== -1 && choice.amount !== 1) || typeof choice.enabled !== 'boolean') return false;
    targets.add(choice.trackId);
    return true;
  });
}

/** Small, role-specific transformations. Eligibility is rechecked after later base edits.
 * Custom and dependency-driven rhythms retain their timing and pitch vocabulary.
 */
export function variationPatch(track: CompositionTrack, choice: ArrangementVariation, globals: MelodyGlobalSettings = {}): PartSettingsPatch {
  if (!choice.enabled || choice.trackId !== track.id || !KINDS[track.role].includes(choice.kind)) return {};
  const s = track.generatorSettings, a = choice.amount;
  switch (choice.kind) {
    case 'accompaniment-velocity-ramp': case 'arp-end-fill': case 'pre-drop-cut': return {}; // Timed event; no static settings patch.
    case 'melody-range': {
      if (track.role !== 'melody' || globals.contextualMelody === false) return {};
      const low = track.generatorSettings.rangeMin ?? 48, high = track.generatorSettings.rangeMax ?? 72, transpose = s.transpose ?? 0;
      // Expand/contract around the authored centre, without silently moving a boundary.
      const rangeMin = low - a * 2, rangeMax = high + a * 2;
      return rangeMax - rangeMin >= 12 && rangeMax - rangeMin <= 36
        && rangeMin >= 36 && rangeMax <= 84 && rangeMin + transpose >= 24 && rangeMax + transpose <= 96
        ? { rangeMin, rangeMax } : {};
    }
    case 'arp-density': case 'chord-transition': case 'drum-density': case 'drum-ghosts': return rhythmVariationPatch(track, choice);
    case 'melody-speed': return track.role === 'melody' && !s.customDurations?.length && track.generatorSettings.rhythm !== 'durations'
      ? { speed: clamp((track.generatorSettings.speed ?? 50) + a * 15, -100, 100) } : {};
    case 'melody-direction': return { targetDirection: a > 0 ? 'ASC' : 'DESC' };
    case 'melody-register': {
      if (track.role !== 'melody') return {};
      const transpose = (s.transpose ?? 0) + a * 12;
      // Leave headroom for the tonic; never clamp an octave into a non-octave shift.
      return Math.abs(transpose) <= 24 && (track.generatorSettings.rangeMin ?? 48) + transpose >= 24
        && (track.generatorSettings.rangeMax ?? 72) + transpose <= 96
        && !(track.generatorSettings.chordNoteChoices ?? []).some(target => Math.abs(target) > 7) ? { transpose } : {};
    }
    case 'arp-register': {
      if (track.role !== 'arpeggio' || track.generatorSettings.octaves !== 1 || track.generatorSettings.pattern === 'custom'
        || track.generatorSettings.arpContour?.length || track.generatorSettings.arpPatternCustom?.length
        || track.generatorSettings.useOctaveAdjustments || s.stretchEnabled) return {};
      const transpose = (s.transpose ?? 0) + a * 12;
      return Math.abs(transpose) <= 24 ? { transpose } : {};
    }
    case 'arp-contour': {
      if (track.role !== 'arpeggio' || (track.generatorSettings.rhythm ?? 'full') !== 'full'
        || track.generatorSettings.pattern === 'custom' || track.generatorSettings.arpContour?.length
        || track.generatorSettings.arpPatternCustom?.length || track.generatorSettings.useOctaveAdjustments
        || s.customPattern || s.customDurations?.length || (s.chordSpan ?? 1) !== 1) return {};
      return { arpContour: a > 0 ? [0, 1, 2, 0] : [2, 1, 0, 0], arpContourChordMode: true };
    }
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

function eligibleChoice(track: CompositionTrack, section: ArrangementSection, choice: ArrangementVariation, globals: MelodyGlobalSettings = {}): boolean {
  if (!section.trackPresence[track.id] || track.rerollLocked || section.trackPhrases?.[track.id] !== undefined
    || track.editedPhrase !== undefined && !section.generatedTrackIds?.includes(track.id) || variationBlockedKeys(section, track, choice.kind).length) return false;
  const effective = { ...track, generatorSettings: { ...track.generatorSettings,
    ...section.rolePartOverrides?.[track.role], ...section.trackPartOverrides?.[track.id] } } as CompositionTrack;
  const defaults: Record<string, unknown> = { rangeMin: 48, rangeMax: 72, speed: 50, targetDirection: 'ASC', octaveInterval: false, pauseChance: 0,
    transpose: 0, arpContour: [], arpContourChordMode: false, euclideanPulses: 4, transitionChance: 0, ghosts: false,
    hitsPerPattern: track.role === 'arpeggio' && track.generatorSettings.rate === 'sixteenth' ? 16 : 8 };
  const patch = variationPatch(effective, choice, globals);
  return Object.entries(patch).every(([key]) => !Object.hasOwn(section.rolePartOverrides?.[track.role] ?? {}, key)
    && !Object.hasOwn(section.trackPartOverrides?.[track.id] ?? {}, key)) && Object.entries(patch).some(([key, value]) => {
      const previous = (track.generatorSettings as unknown as Record<string, unknown>)[key] ?? defaults[key];
      return Array.isArray(value) && Array.isArray(previous) ? value.length !== previous.length || value.some((v, i) => v !== previous[i]) : value !== previous;
    });
}

function registerChoice(track: CompositionTrack): ArrangementVariation | undefined {
  const kind = track.role === 'melody' ? 'melody-register' : track.role === 'arpeggio' ? 'arp-register' : undefined;
  return kind ? { trackId: track.id, kind, amount: 1, enabled: true } : undefined;
}

/** Stable identity draws: reordering unrelated tracks cannot consume a target's stream. */
function identitySeed(seed: bigint, id: string): bigint {
  let hash = 0n;
  for (const char of id) hash = BigInt.asIntN(64, hash * 31n + BigInt(char.charCodeAt(0)));
  return BigInt.asIntN(64, seed ^ hash ^ 0x564152494154n);
}

export interface VariationPlanningScope {
  readonly melodyGlobal?: MelodyGlobalSettings;
  readonly allTracks?: readonly CompositionTrack[];
  readonly retainedChoices?: readonly ArrangementVariation[];
}

/** Shared family rankings retain identity across returns; gestures allocate responsibilities. */
export function planSectionVariations(tracks: readonly CompositionTrack[], sections: readonly ArrangementSection[], index: number,
  seed: bigint, policy: VariationPolicy = DEFAULT_VARIATION_POLICY, scope: VariationPlanningScope = {}): SectionVariations {
  if (!sections[index] || !validVariationPolicy(policy, (scope.allTracks ?? tracks).length)) throw new RangeError('Invalid variation context or policy.');
  const section = sections[index], context = resolveVariationContext(scope.allTracks ?? tracks, sections, index);
  const retained = scope.retainedChoices ?? [];
  const expandedBudget = policy.maxChanges + retained.length > 4;
  const retainedLift = retained.some(choice => isRegisterVariation(choice.kind));
  const reserveLift = context.family === 'hook' && context.durationBeats > 4 && policy.maxChanges + retained.length >= 2 && !retainedLift
    && sections.some((future, i) => resolveVariationContext(scope.allTracks ?? tracks, sections, i).returnLift
      && tracks.some(track => { const choice = registerChoice(track); return !!choice && eligibleChoice(track, future, choice); }));
  // Dedicated stream; no static-adapter or phrase draws are consumed by event selection.
  const cut = context.preDrop && policy.maxChanges + retained.length >= 3 && policy.maxChanges > 0
    && !retained.some(c => c.kind === 'pre-drop-cut' || c.kind === 'arp-end-fill') ? tracks.flatMap(track => {
      if (!section.trackPresence[track.id] || track.rerollLocked || preDropCutEligibility(track, section) !== undefined) return [];
      const random = new JavaRandom(identitySeed(seed, `boundary-cut/${context.family}/${track.id}`));
      return random.nextInt(100) < policy.chance ? [{ trackId: track.id, kind: 'pre-drop-cut' as const, amount: -1 as const,
        enabled: true, priority: random.nextDouble() + (track.role === 'chords' ? 1 : 0) }] : [];
    }).sort((a, b) => b.priority - a.priority || a.trackId.localeCompare(b.trackId))[0] : undefined;
  const fill = context.endFill && policy.maxChanges + retained.length >= 3 && policy.maxChanges > 0
    && !retained.some(c => c.kind === 'arp-end-fill' || c.kind === 'pre-drop-cut' || c.enabled && section.trackPresence[c.trackId] && isActivityVariation(c.kind))
    ? tracks.flatMap(track => {
      if (!section.trackPresence[track.id] || track.rerollLocked || arpEndFillEligibility(track, section) !== undefined) return [];
      const random = new JavaRandom(identitySeed(seed, `boundary-fill/${context.family}/${track.id}`));
      return random.nextInt(100) < policy.chance ? [{ trackId: track.id, kind: 'arp-end-fill' as const, amount: 1 as const,
        enabled: true, priority: random.nextDouble() }] : [];
    }).sort((a, b) => b.priority - a.priority || a.trackId.localeCompare(b.trackId))[0] : undefined;
  const boundary = cut ?? fill;
  const candidates = tracks.flatMap(track => {
    if (!section.trackPresence[track.id] || track.rerollLocked || section.trackPhrases?.[track.id] !== undefined
      || track.editedPhrase !== undefined && !section.generatedTrackIds?.includes(track.id)) return [];
    // Do not key this stream by occurrence or section ID: repeated families share anchors.
    const random = new JavaRandom(identitySeed(seed, `${context.family}/${track.id}`));
    const eligible = KINDS[track.role].flatMap(kind => {
      if (kind === 'accompaniment-velocity-ramp' || kind === 'arp-end-fill' || kind === 'pre-drop-cut' || isRegisterVariation(kind)) return [];
      const amount = variationAmountForContext(context, kind);
      return amount === undefined ? [] : [{ trackId: track.id, kind, amount, enabled: true } as ArrangementVariation];
    }).filter(choice => eligibleChoice(track, section, choice, scope.melodyGlobal));
    if (!eligible.length || random.nextInt(100) >= policy.chance) return [];
    const best = Math.max(...eligible.map(choice => variationPriority(context, choice.kind)));
    const preferred = eligible.filter(choice => variationPriority(context, choice.kind) === best);
    const choice = preferred[random.nextInt(preferred.length)];
    const priority = random.nextDouble() + variationPriority(context, choice.kind) + (reserveLift && choice.kind === 'bass-weight' ? 1 : 0);
    return [{ choice, priority, role: track.role }];
  });
  candidates.sort((a, b) => b.priority - a.priority || a.choice.trackId.localeCompare(b.choice.trackId));
  const budget = Math.min(policy.maxChanges, context.durationBeats <= 4 ? Math.max(0, 1 - (scope.retainedChoices?.length ?? 0)) : policy.maxChanges);
  const rampAmount = variationAmountForContext(context, 'accompaniment-velocity-ramp');
  const ramp = rampAmount !== undefined && policy.maxChanges + retained.length >= 4 && budget > (boundary ? 1 : 0)
    && !retained.some(c => c.kind === 'accompaniment-velocity-ramp') ? tracks.flatMap(track => {
      if (track.id === boundary?.trackId || !section.trackPresence[track.id] || track.rerollLocked
        || velocityEnvelopeEligibility(track, section, rampAmount) !== undefined
        || candidates.some(c => c.choice.trackId === track.id && isActivityVariation(c.choice.kind))
        || !expandedBudget && boundary && tracks.find(t => t.id === boundary.trackId)?.role === track.role) return [];
      const random = new JavaRandom(identitySeed(seed, `velocity-envelope/${context.family}/${track.id}`));
      return random.nextInt(100) < policy.chance ? [{ trackId: track.id, kind: 'accompaniment-velocity-ramp' as const,
        amount: rampAmount, enabled: true, priority: random.nextDouble() + (track.role === 'chords' ? 1 : 0) }] : [];
    }).sort((a, b) => b.priority - a.priority || a.trackId.localeCompare(b.trackId))[0] : undefined;
  const anchorBudget = Math.max(0, budget - (reserveLift ? 1 : 0) - (boundary ? 1 : 0) - (ramp ? 1 : 0));
  const roles = new Set<CompositionTrack['role']>(!expandedBudget ? [boundary, ramp].flatMap(c => c ? [tracks.find(t => t.id === c.trackId)!.role] : []) : []);
  let activityDriver = !!fill || (scope.retainedChoices ?? []).some(choice => choice.enabled && section.trackPresence[choice.trackId] && isActivityVariation(choice.kind));
  const choices: ArrangementVariation[] = [];
  // Give each role a first opportunity, then use larger budgets for supporting tracks.
  for (const diverse of expandedBudget ? [true, false] : [true]) for (const candidate of candidates) {
    if (choices.length >= anchorBudget) break;
    if (candidate.choice.trackId === boundary?.trackId || candidate.choice.trackId === ramp?.trackId || choices.some(choice => choice.trackId === candidate.choice.trackId)) continue;
    if (diverse && roles.has(candidate.role)) continue;
    const activity = isActivityVariation(candidate.choice.kind);
    if (context.gesture === 'build-pulse' && activity && activityDriver) continue;
    choices.push(candidate.choice); roles.add(candidate.role);
    if (activity) activityDriver = true;
  }
  if (boundary && choices.length < budget) {
    const { priority, ...choice } = boundary;
    choices.push(choice);
  }
  if (ramp && choices.length < budget) {
    const { priority, ...choice } = ramp;
    choices.push(choice);
  }
  if (context.returnLift && reserveLift && choices.length < budget) {
    const lifts = tracks.flatMap(track => {
      const choice = registerChoice(track);
      if (!choice || (!expandedBudget && roles.has(track.role)) || choices.some(c => c.trackId === track.id)
        || !eligibleChoice(track, section, choice)) return [];
      const random = new JavaRandom(identitySeed(seed, `hook-lift/${track.id}`));
      return random.nextInt(100) < policy.chance ? [{ choice, priority: random.nextDouble() }] : [];
    }).sort((a, b) => b.priority - a.priority || a.choice.trackId.localeCompare(b.choice.trackId));
    if (lifts[0]) choices.push(lifts[0].choice);
  }
  return { seed: BigInt.asIntN(64, seed).toString(), policy: { ...policy }, gesture: context.gesture, choices };
}

export function resolveVariationPatch(track: CompositionTrack, section: ArrangementSection, globals: MelodyGlobalSettings = {}): PartSettingsPatch {
  if (!section.variations || !section.trackPresence?.[track.id]) return {};
  const choice = section.variations?.choices.find(c => c.trackId === track.id);
  if (choice && variationBlockedKeys(section, track, choice.kind).length) return {};
  const effective = { ...track, generatorSettings: { ...track.generatorSettings,
    ...section.rolePartOverrides?.[track.role], ...section.trackPartOverrides?.[track.id] } } as CompositionTrack;
  return choice ? variationPatch(effective, choice, globals) : {};
}

export function pruneVariations(variations: SectionVariations | undefined, removed: ReadonlySet<string>): SectionVariations | undefined {
  return variations && { ...variations, choices: variations.choices.filter(c => !removed.has(c.trackId)) };
}
