import type { ArrangementSection, SectionType } from '../project/arrangement.model';
import type { SectionTypeChanceSettings } from '../project/section-type-chances';
import type { CompositionTrack } from '../project/track.model';

export interface ArrangementIntent {
  /** Orchestration density and anticipation are independent, normalized 0–1. */
  readonly energy: number;
  readonly tension: number;
  readonly motion: 'establish' | 'rise' | 'sustain' | 'release' | 'resolve';
}

const PROFILES: Record<SectionType, readonly [number, number]> = {
  INTRO: [.2, .1], VERSE1: [.4, .2], VERSE2: [.45, .25], VERSE3: [.55, .35],
  BUILDUP1: [.6, .7], BUILDUP2: [.7, .95],
  CHORUS1: [.7, .25], CHORUS2: [.8, .3], CHORUS3: [.9, .35], CLIMAX: [1, .4],
  BREAKDOWN: [.25, .15], CHILL: [.35, .2], HALF_CHORUS: [.65, .2], OUTRO: [.15, 0],
};

/** Derived from the current ordering, so moving/changing sections cannot leave stale intent.
 * Future variations can consume this without interpreting section names themselves.
 */
export function resolveArrangementIntent(sections: readonly Pick<ArrangementSection, 'type'>[], index: number): ArrangementIntent {
  const [energy, tension] = PROFILES[sections[index].type];
  const previous = index > 0 ? PROFILES[sections[index - 1].type] : undefined;
  const next = sections[index + 1] ? PROFILES[sections[index + 1].type] : undefined;
  const motion = !previous ? 'establish' : !next ? 'resolve'
    : previous[1] - tension > .3 ? 'release'
    : energy > previous[0] || tension > previous[1] ? 'rise' : 'sustain';
  return { energy, tension, motion };
}

/** One shared ranking per reroll retains track identity across adjacent sections.
 * Preferences are selection weights, not independent coin flips. Zero is a hard exclusion.
 * Presence stays explicit; intent never changes notes, patches or manual ownership.
 */
export function generateContextualPresence(
  tracks: readonly CompositionTrack[],
  sections: readonly (Pick<ArrangementSection, 'type'> & Partial<Pick<ArrangementSection, 'trackPresence'>>)[],
  preferences: SectionTypeChanceSettings,
  random: () => number,
  targetIndex?: number,
): Readonly<Record<string, boolean>>[] {
  const identities = new Map(tracks.map(track => [track.id, .25 + random() * 1.5]));
  let previous: Readonly<Record<string, boolean>> = {};
  return sections.map((section, index) => {
    if (targetIndex !== undefined && index !== targetIndex) {
      previous = section.trackPresence ?? {};
      return previous;
    }
    const intent = resolveArrangementIntent(sections, index);
    const weights = preferences[section.type];
    const eligible = tracks.filter(track => weights[track.role] > 0);
    const presence: Record<string, boolean> = Object.fromEntries(tracks.map(track => [track.id, false]));
    const budget = Math.min(eligible.length, Math.max(1, Math.ceil(tracks.length * intent.energy)));
    const score = (track: CompositionTrack): number => {
      // Keep the rhythm foundation at releases; anticipation favors pulse over bass weight.
      const foundation = intent.energy >= .65 && (track.role === 'bass' || track.role === 'drums') ? 2 : 0;
      const anticipation = intent.tension >= .7 && track.role === 'drums' ? 3 : 0;
      const anchor = track.role === 'chords' || track.role === 'melody' ? .35 : 0;
      return foundation + anticipation + anchor + weights[track.role] / 100
        + identities.get(track.id)! + (previous[track.id] ? .6 : 0);
    };
    const ranked = [...eligible].sort((a, b) => score(b) - score(a) || a.id.localeCompare(b.id));
    const selectRole = (roles: readonly string[]) => {
      const candidate = ranked.find(track => roles.includes(track.role));
      if (candidate) presence[candidate.id] = true;
    };
    // Anchor sparse passages in pitched material whenever available.
    selectRole(['melody', 'chords', 'arpeggio', 'bass']);
    if (intent.energy >= .65) {
      selectRole(['bass']);
      selectRole(['drums']);
    } else if (intent.tension >= .7) selectRole(['drums']);
    for (const track of ranked) {
      if (Object.values(presence).filter(Boolean).length >= budget) break;
      presence[track.id] = true;
    }
    // A rise is additive wherever the new section's preferences permit it. A fall
    // removes layers from the preceding texture before introducing replacements.
    const previousEnergy = index > 0 ? resolveArrangementIntent(sections, index - 1).energy : 0;
    if (index > 0 && intent.energy >= previousEnergy && intent.motion !== 'release') {
      for (const track of eligible) if (previous[track.id]) presence[track.id] = true;
    }
    previous = presence;
    return presence;
  });
}
