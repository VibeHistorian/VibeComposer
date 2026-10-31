import { maximumSectionVariations } from '../music/arrangement-variations';
import type { VariationPolicy } from '../music/arrangement-variation.model';
import type { CompositionProject } from './project.model';

/** Track-removal commands resize capacity in the same historical transaction. */
export function fitVariationCapacity(project: CompositionProject): CompositionProject {
  const maximum = maximumSectionVariations(project.tracks.length);
  const fit = (policy: VariationPolicy): VariationPolicy => policy.maxChanges <= maximum
    ? policy : { ...policy, maxChanges: maximum };
  return { ...project,
    ...(project.arrangementVariationPolicy ? { arrangementVariationPolicy: fit(project.arrangementVariationPolicy) } : {}),
    arrangement: project.arrangement.map(section => !section.variations ? section : { ...section, variations: {
      ...section.variations, policy: fit(section.variations.policy), choices: section.variations.choices.slice(0, maximum),
    } }),
  };
}
