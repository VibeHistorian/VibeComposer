import type { CompositionProject } from './project.model';
import type { ArrangementSection } from './arrangement.model';
import type { CompositionTrack } from './track.model';
import type { ArrangedPart } from '../music/part-role';
import type { SectionVariations, VariationPolicy } from '../music/arrangement-variation.model';
import { DEFAULT_VARIATION_POLICY, planSectionVariations } from '../music/arrangement-variations';

export interface VariationScope { readonly sectionId?: string; readonly role?: ArrangedPart; readonly trackId?: string; }
export type VariationDraft = Readonly<Record<string, SectionVariations | undefined>>;
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
    const previous = draft[section.id], selectedPolicy = policy ?? previous?.policy ?? DEFAULT_VARIATION_POLICY;
    const others = scoped ? previous?.choices.filter(choice => !ids.has(choice.trackId)) ?? [] : [];
    const available = Math.max(0, selectedPolicy.maxChanges - others.length);
    const planned = planSectionVariations(tracks, project.arrangement, project.arrangement.indexOf(section), seed,
      { ...selectedPolicy, maxChanges: available });
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
