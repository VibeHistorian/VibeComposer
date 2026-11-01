import type { ArrangementSection } from './arrangement.model';
import type { CompositionProject } from './project.model';
import type { CompositionTrack } from './track.model';
import type { ArrangementVariation, VariationGenerationOptions } from '../music/arrangement-variation.model';
import { VARIATION_DEFINITIONS, VARIATION_KINDS_BY_ROLE, variationParameters } from '../music/arrangement-variation-catalogue';
import { inspectArrangementVariation } from '../music/arrangement-variation-inspection';
import { maximumSectionVariations } from '../music/arrangement-variations';

/** Explicit additions ignore automatic allowlists, chances and contextual recommendations. */
export function variationAdditions(project: CompositionProject, section: ArrangementSection,
  tracks: readonly CompositionTrack[], defaults: VariationGenerationOptions = {}):
  readonly { value: string; label: string; choice: ArrangementVariation }[] {
  const saved = section.variations?.choices ?? [];
  if (saved.length >= maximumSectionVariations(project.tracks.length)) return [];
  return tracks.filter(track => !saved.some(choice => choice.trackId === track.id)).flatMap(track =>
    VARIATION_KINDS_BY_ROLE[track.role].flatMap(kind => VARIATION_DEFINITIONS[kind].directions.flatMap(amount => {
      const parameters = variationParameters(kind, amount, defaults);
      const choice: ArrangementVariation = { trackId: track.id, kind, amount, enabled: true, ...(parameters ? { parameters } : {}) };
      if (inspectArrangementVariation(track, section, choice, project.melodyGlobal).status !== 'active') return [];
      return [{ value: JSON.stringify([track.id, kind, amount]),
        label: `${track.name} · ${VARIATION_DEFINITIONS[kind].label} · ${amount > 0 ? 'Increase' : 'Decrease'}`, choice }];
    })));
}
