import type { MelodyGlobalSettings } from '../project/project.model';

export const DEFAULT_MELODY_GLOBAL = Object.freeze({
  singleNoteExceptions: false, maxDirectionChanges: 2, fillPausesPerChord: false,
  patternEffect: 2, arpySurprises: false,
});

export function decodeMelodyGlobal(value: unknown): MelodyGlobalSettings | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const decoded: Record<string, boolean | number> = {};
  for (const [key, field] of Object.entries(value)) {
    if (key === 'maxDirectionChanges') {
      if (!Number.isInteger(field) || field < 1 || field > 6) return undefined;
    } else if (key === 'patternEffect') {
      if (!Number.isInteger(field) || field < 0 || field > 2) return undefined;
    } else if (!['singleNoteExceptions', 'fillPausesPerChord', 'arpySurprises'].includes(key) || typeof field !== 'boolean') return undefined;
    decoded[key] = field;
  }
  return decoded;
}
