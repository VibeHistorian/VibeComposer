import type { ArrangementSection } from '../project/arrangement.model';
import type { CompositionTrack } from '../project/track.model';
import type { ArrangementVariation, VariationKind } from './arrangement-variation.model';
import { variationBlockedKeys, variationPatch } from './arrangement-variations';
import { PART_CONTROLS, settingsValues } from './part-settings';

export type VariationStatus = 'active' | 'disabled' | 'absent' | 'manual' | 'incompatible' | 'overridden' | 'unchanged';
export const VARIATION_LABELS: Record<VariationKind, string> = {
  'melody-speed': 'Melody speed', 'melody-direction': 'Melody direction', 'melody-register': 'Melody octave register',
  'arp-activity': 'Arp activity', 'arp-contour': 'Arp chord-relative contour', 'arp-register': 'Arp octave register',
  'bass-weight': 'Bass octave weight', 'chord-space': 'Chord spacing', 'drum-activity': 'Cymbal activity',
};
export interface VariationInspection {
  readonly choice: ArrangementVariation;
  readonly label: string;
  readonly status: VariationStatus;
  readonly detail: string;
  readonly changes: readonly { readonly parameter: string; readonly label: string; readonly before: unknown; readonly after: unknown; readonly effective: unknown; readonly source: string }[];
}

/** Inspection shares the operation adapters and precedence used by musical resolution. */
export function inspectArrangementVariation(track: CompositionTrack, section: ArrangementSection, choice: ArrangementVariation): VariationInspection {
  const base = { choice, label: VARIATION_LABELS[choice.kind] };
  const result = (status: VariationStatus, detail: string, changes: VariationInspection['changes'] = []): VariationInspection => ({ ...base, status, detail, changes });
  if (!choice.enabled) return result('disabled', 'Disabled');
  if (!section.trackPresence[track.id]) return result('absent', 'Track is absent in this section');
  if (section.trackPhrases?.[track.id] !== undefined || track.editedPhrase !== undefined && !section.generatedTrackIds?.includes(track.id)) {
    return result('manual', 'Manual phrase owns these notes');
  }
  const rolePatch = section.rolePartOverrides?.[track.role] ?? {}, trackPatch = section.trackPartOverrides?.[track.id] ?? {};
  const effective = { ...track, generatorSettings: { ...track.generatorSettings, ...rolePatch, ...trackPatch } } as CompositionTrack;
  const values = settingsValues(effective.generatorSettings, track.role);
  const blocked = variationBlockedKeys(section, track, choice.kind);
  if (blocked.length) return result('overridden', 'Explicit section controls protect this operation', blocked.map(parameter => ({
    parameter, label: PART_CONTROLS[track.role].find(control => control.key === parameter)?.label ?? parameter,
    before: values[parameter], after: values[parameter], effective: values[parameter],
    source: Object.hasOwn(trackPatch, parameter) ? 'Section track override' : 'Section role override',
  })));
  const patch = variationPatch(effective, choice);
  if (!Object.keys(patch).length) return result('incompatible', 'Current settings, authored patterns or register bounds prevent this variation');
  const changes = Object.entries(patch).map(([parameter, after]) => {
    const source = Object.hasOwn(trackPatch, parameter) ? 'Section track override'
      : Object.hasOwn(rolePatch, parameter) ? 'Section role override' : 'Variation';
    return { parameter, label: PART_CONTROLS[track.role].find(control => control.key === parameter)?.label ?? parameter,
      before: values[parameter], after, effective: source === 'Variation' ? after : values[parameter], source };
  });
  if (changes.every(change => change.source !== 'Variation')) return result('overridden', 'Explicit section settings take precedence', changes);
  if (changes.every(change => change.source !== 'Variation' || change.before === change.after)) return result('unchanged', 'Already at the requested value or bound', changes);
  return result('active', track.phraseEdits?.length && !section.generatedTrackIds?.includes(track.id)
    ? 'Active in generated regions; manual edits are preserved' : 'Active', changes);
}
