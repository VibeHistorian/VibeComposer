import { maximumSectionVariations } from '../music/arrangement-variations';
import type { VariationPolicy } from '../music/arrangement-variation.model';
import type { CompositionProject } from './project.model';

/** Track-removal commands resize capacity in the same historical transaction. */
export function fitVariationCapacity(project: CompositionProject): CompositionProject {
  const maximum = maximumSectionVariations(project.tracks.length);
  const fit = (policy: VariationPolicy): VariationPolicy => policy.maxChanges <= maximum
    ? policy : { ...policy, maxChanges: maximum };
  const fitChoices = (choices: NonNullable<CompositionProject['arrangement'][number]['variations']>['choices']) => {
    if (choices.length <= maximum) return choices;
    const kept = new Set([...choices.filter(c => c.origin === 'authored'), ...choices.filter(c => c.origin !== 'authored')]
      .slice(0, maximum).map(c => c.trackId));
    return choices.filter(c => kept.has(c.trackId));
  };
  return { ...project,
    ...(project.arrangementVariationPolicy ? { arrangementVariationPolicy: fit(project.arrangementVariationPolicy) } : {}),
    arrangement: project.arrangement.map(section => !section.variations ? section : { ...section, variations: {
      ...section.variations, policy: fit(section.variations.policy), choices: fitChoices(section.variations.choices),
    } }),
  };
}
