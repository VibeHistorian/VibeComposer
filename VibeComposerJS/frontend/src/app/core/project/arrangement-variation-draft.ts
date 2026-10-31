import type { CompositionProject } from './project.model';
import type { ArrangementSection } from './arrangement.model';
import type { CompositionTrack } from './track.model';
import type { ArrangedPart } from '../music/part-role';
import type { SectionVariations, VariationPolicy } from '../music/arrangement-variation.model';
import { DEFAULT_VARIATION_POLICY, planSectionVariations } from '../music/arrangement-variations';
import type { SectionTypeChanceSettings } from './section-type-chances';
import { generateContextualPresence } from '../music/arrangement-intent';
import { JavaRandom } from '../music/java-random';

export interface VariationScope { readonly sectionId?: string; readonly role?: ArrangedPart; readonly trackId?: string; }
export type VariationDraft = Readonly<Record<string, SectionVariations | undefined>>;
export type VariationPresenceDraft = Readonly<Record<string, Readonly<Record<string, boolean>>>>;
export interface VariationDraftOptions {
  readonly presence?: VariationPresenceDraft;
  readonly policy?: VariationPolicy;
}

/** Presence is staged separately, with its own stream, before planning against the candidate. */
export function rerollVariationPresence(project: CompositionProject, chances: SectionTypeChanceSettings, seed: bigint): VariationPresenceDraft {
  const random = new JavaRandom(BigInt.asIntN(64, seed ^ 0x5650524553454en));
  const presence = generateContextualPresence(project.tracks, project.arrangement, chances, () => random.nextDouble());
  return Object.fromEntries(project.arrangement.map((section, index) => [section.id, presence[index]]));
}

export function projectWithVariationPresence(project: CompositionProject, presence?: VariationPresenceDraft): CompositionProject {
  return !presence ? project : { ...project, arrangement: project.arrangement.map(section => ({ ...section,
    trackPresence: presence[section.id] ?? section.trackPresence })) };
}
export const variationScopeTracks = (project: CompositionProject, scope: VariationScope): readonly CompositionTrack[] => project.tracks
  .filter(track => (!scope.trackId || track.id === scope.trackId) && (!scope.role || track.role === scope.role));
export const variationScopeSections = (project: CompositionProject, scope: VariationScope): readonly ArrangementSection[] => project.arrangement
  .filter(section => !scope.sectionId || section.id === scope.sectionId);

export function initialVariationDraft(project: CompositionProject, scope: VariationScope): VariationDraft {
  return Object.fromEntries(variationScopeSections(project, scope).map(section => [section.id,
    section.variations ? structuredClone(section.variations) : undefined]));
}

/** Replan only selected targets. Unrelated choices, order and presence remain intact. */
export function rerollVariationDraft(project: CompositionProject, scope: VariationScope, draft: VariationDraft, seed: bigint,
  policy?: VariationPolicy): VariationDraft {
  const tracks = variationScopeTracks(project, scope), ids = new Set(tracks.map(track => track.id));
  const scoped = !!scope.trackId || !!scope.role;
  return Object.fromEntries(variationScopeSections(project, scope).map(section => {
    const previous = draft[section.id], selectedPolicy = policy ?? previous?.policy ?? project.arrangementVariationPolicy ?? DEFAULT_VARIATION_POLICY;
    const others = scoped ? previous?.choices.filter(choice => !ids.has(choice.trackId)) ?? [] : [];
    const available = Math.max(0, selectedPolicy.maxChanges - others.length);
    const planned = planSectionVariations(tracks, project.arrangement, project.arrangement.indexOf(section), seed,
      { ...selectedPolicy, maxChanges: available }, { allTracks: project.tracks, retainedChoices: others, melodyGlobal: project.melodyGlobal });
    return [section.id, { ...planned, policy: { ...selectedPolicy }, choices: [...others, ...planned.choices] }];
  }));
}

export function resetVariationDraft(project: CompositionProject, scope: VariationScope, draft: VariationDraft): VariationDraft {
  const ids = new Set(variationScopeTracks(project, scope).map(track => track.id));
  return Object.fromEntries(variationScopeSections(project, scope).map(section => {
    const previous = draft[section.id];
    return [section.id, (scope.trackId || scope.role) && previous
      ? { ...previous, choices: previous.choices.filter(choice => !ids.has(choice.trackId)) } : undefined];
  }));
}
