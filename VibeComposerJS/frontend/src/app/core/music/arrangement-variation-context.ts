import type { ArrangementSection, SectionType } from '../project/arrangement.model';
import type { CompositionTrack } from '../project/track.model';
import type { VariationGesture, VariationKind } from './arrangement-variation.model';
import { resolveArrangementIntent, type ArrangementIntent } from './arrangement-intent';

const EPSILON = 1e-9;

export const VARIATION_GESTURE_LABELS: Record<VariationGesture, string> = {
  establish: 'Establish', thin: 'Thin the texture', 'build-pulse': 'Build pulse',
  'reinforce-foundation': 'Reinforce the foundation', 'release-tension': 'Release tension', sustain: 'Maintain the character', resolve: 'Resolve',
};

export interface VariationContext {
  readonly intent: ArrangementIntent;
  readonly gesture: VariationGesture;
  readonly reason: string;
  readonly family: string;
  readonly occurrence: number;
  readonly presentCount: number;
  readonly durationBeats: number;
  readonly returnLift: boolean;
  readonly preDrop: boolean;
}

function family(type: SectionType): string {
  if (type.startsWith('CHORUS') || type === 'CLIMAX' || type === 'HALF_CHORUS') return 'hook';
  if (type.startsWith('VERSE')) return 'verse';
  if (type.startsWith('BUILDUP')) return 'build';
  if (type === 'CHILL' || type === 'BREAKDOWN') return 'recovery';
  return type;
}

/** Additional planning context never changes the orchestration intent or saved choices. */
export function resolveVariationContext(tracks: readonly CompositionTrack[], sections: readonly ArrangementSection[], index: number): VariationContext {
  const section = sections[index];
  if (!section) throw new RangeError('Invalid variation section.');
  const intent = resolveArrangementIntent(sections, index);
  const previous = index > 0 ? resolveArrangementIntent(sections, index - 1) : undefined;
  const next = index + 1 < sections.length ? resolveArrangementIntent(sections, index + 1) : undefined;
  const currentFamily = family(section.type);
  const occurrence = sections.slice(0, index).filter(s => family(s.type) === currentFamily).length;
  const presentCount = tracks.filter(track => section.trackPresence[track.id]).length;
  const durations = section.customDurations ?? [4];
  const durationBeats = Array.from({ length: section.measures }, (_, slot) => durations[slot % durations.length])
    .reduce((sum, value) => sum + value, 0) * (section.context?.beatDurationMultiplier ?? 1);
  let gesture: VariationGesture, reason: string;
  if (!next && intent.energy <= .55) {
    gesture = 'resolve'; reason = 'Settle into the ending';
  } else if (previous && previous.energy - intent.energy >= .2 - EPSILON) {
    gesture = 'thin'; reason = 'Recover after the preceding peak';
  } else if (previous && previous.tension - intent.tension > .3 + EPSILON && intent.energy >= .6) {
    gesture = 'release-tension'; reason = 'Ease rhythmic pressure while reinforcing weight';
  } else if (intent.tension >= .65) {
    gesture = 'build-pulse'; reason = 'Give anticipation a clear rhythmic driver';
  } else if (next && intent.energy >= .35 && (next.tension - intent.tension >= .3 - EPSILON || next.energy - intent.energy >= .25 - EPSILON)) {
    gesture = 'build-pulse'; reason = 'Prepare for the approaching rise';
  } else if (!previous) {
    gesture = 'establish'; reason = 'Introduce the material before developing it';
  } else if (intent.energy >= .65) {
    gesture = 'reinforce-foundation'; reason = 'Support the hook without accelerating every layer';
  } else {
    gesture = 'sustain'; reason = 'Keep the current character recognizable';
  }
  const priorHookEnergy = Math.max(0, ...sections.slice(0, index).filter(s => family(s.type) === 'hook')
    .map(s => resolveArrangementIntent([s], 0).energy));
  const firstHook = sections.find(s => family(s.type) === 'hook');
  const firstHookEnergy = firstHook ? resolveArrangementIntent([firstHook], 0).energy : 0;
  const returnLift = currentFamily === 'hook' && occurrence > 0 && durationBeats > 4 && intent.energy >= .8
    && intent.energy - firstHookEnergy >= .05 - EPSILON && intent.energy >= priorHookEnergy - EPSILON
    && (gesture === 'reinforce-foundation' || gesture === 'release-tension');
  const preDrop = gesture === 'build-pulse' && durationBeats > 4 && presentCount > 1 && !!next
    && next.energy >= .65 && next.energy >= intent.energy - EPSILON && intent.tension - next.tension >= .3 - EPSILON;
  return { intent, gesture, reason, family: currentFamily, occurrence, presentCount, durationBeats, returnLift, preDrop };
}

export const isActivityVariation = (kind: VariationKind): boolean => ['melody-speed', 'arp-activity', 'arp-density', 'drum-activity', 'drum-density', 'drum-ghosts'].includes(kind);
export const isRegisterVariation = (kind: VariationKind): boolean => kind === 'melody-register' || kind === 'arp-register';

/** Undefined deliberately leaves a responsibility unchanged. Solo material keeps its pulse. */
export function variationAmountForContext(context: VariationContext, kind: VariationKind): -1 | 1 | undefined {
  const { gesture, intent, presentCount } = context;
  if (kind === 'pre-drop-cut') return context.preDrop ? -1 : undefined;
  if (isRegisterVariation(kind)) return context.returnLift ? 1 : undefined;
  if (kind === 'melody-range') return presentCount <= 1 ? undefined : gesture === 'reinforce-foundation' ? 1 : gesture === 'thin' || gesture === 'resolve' ? -1 : undefined;
  if (kind === 'chord-transition') return gesture === 'build-pulse' ? 1 : gesture === 'release-tension' || gesture === 'resolve' ? -1 : undefined;
  if (kind === 'arp-contour') return gesture === 'build-pulse' ? 1 : gesture === 'resolve' ? -1 : undefined;
  if (kind === 'bass-weight' || kind === 'chord-space') {
    if (gesture === 'build-pulse' || gesture === 'sustain') return undefined;
    return intent.energy >= .65 && gesture !== 'thin' && gesture !== 'resolve' ? 1 : -1;
  }
  if (kind === 'melody-direction') {
    if (gesture === 'build-pulse') return 1;
    if (gesture === 'resolve') return -1;
    return undefined;
  }
  if (gesture === 'build-pulse') return 1;
  if (presentCount <= 1 || gesture === 'sustain' || gesture === 'reinforce-foundation') return undefined;
  return gesture === 'thin' || gesture === 'resolve' || gesture === 'release-tension' || intent.energy < .65 ? -1 : undefined;
}

export function variationPriority(context: VariationContext, kind: VariationKind): number {
  if (kind === 'arp-contour') return 2;
  if (kind === 'melody-range') return 2;
  if (kind === 'chord-transition') return context.gesture === 'release-tension' ? 3 : 2;
  const activity = isActivityVariation(kind), foundation = kind === 'bass-weight' || kind === 'chord-space';
  switch (context.gesture) {
    case 'build-pulse': return ['arp-activity', 'arp-density', 'drum-activity', 'drum-density'].includes(kind) ? 4
      : kind === 'drum-ghosts' ? 3 : kind === 'melody-direction' ? 2 : activity ? 1 : 0;
    case 'release-tension': return kind === 'bass-weight' ? 4 : activity ? 3 : foundation ? 2 : 0;
    case 'reinforce-foundation': return foundation ? 4 : 0;
    case 'thin': return activity ? 3 : foundation ? 2 : 0;
    case 'resolve': return kind === 'melody-direction' ? 4 : activity ? 3 : 1;
    default: return foundation ? 2 : 1;
  }
}
