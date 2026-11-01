import { VARIATION_DEFINITIONS, validMelodySpeedDelta, validVariationParameters, variationStrength, validVelocityDelta } from '../music/arrangement-variation-catalogue';
import type { VariationKind, VariationGenerationOptions } from '../music/arrangement-variation.model';
export interface VariationRerollPreferences extends VariationGenerationOptions {
  readonly allowedKinds: readonly VariationKind[];
  readonly melodySpeedDelta: number;
  readonly strengths: Partial<Record<VariationKind, number>>;
  readonly velocityFallDelta: number;
}
export const VARIATION_ROWS = Object.entries(VARIATION_DEFINITIONS).map(([kind, definition]) => ({ kind: kind as VariationKind, label: definition.label }));
export function defaultVariationRerolls(): VariationRerollPreferences {
  return { allowedKinds: VARIATION_ROWS.map(row => row.kind), melodySpeedDelta: 15, velocityFallDelta: 20,
    strengths: Object.fromEntries(VARIATION_ROWS.flatMap(row => {
      const spec = variationStrength(row.kind);
      return spec && row.kind !== 'melody-speed' ? [[row.kind, spec.defaultValue]] : [];
    })) };
}
export function decodeVariationRerolls(value: unknown): VariationRerollPreferences | null {
  if (value === undefined) return defaultVariationRerolls();
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const raw = value as VariationRerollPreferences, defaults = defaultVariationRerolls();
  if (Object.keys(raw).some(key => !['allowedKinds','melodySpeedDelta','strengths','velocityFallDelta'].includes(key)) || !Array.isArray(raw.allowedKinds)
    || new Set(raw.allowedKinds).size !== raw.allowedKinds.length || raw.allowedKinds.some(kind => typeof kind !== 'string' || !Object.hasOwn(VARIATION_DEFINITIONS, kind))
    || !validMelodySpeedDelta(raw.melodySpeedDelta)
    || raw.velocityFallDelta !== undefined && !validVelocityDelta(raw.velocityFallDelta)) return null;
  if (raw.strengths !== undefined) {
    if (!raw.strengths || typeof raw.strengths !== 'object' || Array.isArray(raw.strengths)) return null;
    for (const [kind, strength] of Object.entries(raw.strengths)) {
      if (!Object.hasOwn(defaults.strengths, kind)) return null;
      const spec = variationStrength(kind as VariationKind)!;
      if (!validVariationParameters(kind as VariationKind, { [spec.key]: strength })) return null;
    }
  }
  return { allowedKinds: VARIATION_ROWS.filter(row => raw.allowedKinds.includes(row.kind)).map(row => row.kind),
    melodySpeedDelta: raw.melodySpeedDelta, velocityFallDelta: raw.velocityFallDelta ?? defaults.velocityFallDelta, strengths: { ...defaults.strengths, ...raw.strengths } };
}
