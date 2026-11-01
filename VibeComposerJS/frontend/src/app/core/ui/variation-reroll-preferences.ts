import { VARIATION_DEFINITIONS, validMelodySpeedDelta } from '../music/arrangement-variation-catalogue';
import type { VariationKind } from '../music/arrangement-variation.model';
export interface VariationRerollPreferences { readonly allowedKinds: readonly VariationKind[]; readonly melodySpeedDelta: number; }
export const VARIATION_ROWS = Object.entries(VARIATION_DEFINITIONS).map(([kind, definition]) => ({ kind: kind as VariationKind, label: definition.label }));
export function defaultVariationRerolls(): VariationRerollPreferences { return { allowedKinds: VARIATION_ROWS.map(row => row.kind), melodySpeedDelta: 15 }; }
export function decodeVariationRerolls(value: unknown): VariationRerollPreferences | null {
  if (value === undefined) return defaultVariationRerolls();
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const raw = value as VariationRerollPreferences;
  if (Object.keys(raw).some(key => key !== 'allowedKinds' && key !== 'melodySpeedDelta') || !Array.isArray(raw.allowedKinds)
    || new Set(raw.allowedKinds).size !== raw.allowedKinds.length || raw.allowedKinds.some(kind => typeof kind !== 'string' || !Object.hasOwn(VARIATION_DEFINITIONS, kind))
    || !validMelodySpeedDelta(raw.melodySpeedDelta)) return null;
  return { allowedKinds: VARIATION_ROWS.filter(row => raw.allowedKinds.includes(row.kind)).map(row => row.kind), melodySpeedDelta: raw.melodySpeedDelta };
}
