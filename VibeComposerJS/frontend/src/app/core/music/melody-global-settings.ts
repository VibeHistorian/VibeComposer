import type { MelodyGlobalSettings } from '../project/project.model';
import { DEFAULT_BLOCK_TYPE_WEIGHTS } from './melody-catalogue';

export const DEFAULT_MELODY_GLOBAL = Object.freeze({
  singleNoteExceptions: false, maxDirectionChanges: 2, fillPausesPerChord: false,
  patternEffect: 2, arpySurprises: false, patternFlip: false, emphasizeKey: false,
  targetNoteVariation: 3, targetDirection: 'ASC' as const,
  useDirectionsFromProgression: false, forceFirstOutline: false,
  newBlocksChance: 25, blockTypeWeights: DEFAULT_BLOCK_TYPE_WEIGHTS, blockChangePreference: Object.freeze([] as number[]),
});

export function decodeMelodyGlobal(value: unknown): MelodyGlobalSettings | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const decoded: Record<string, boolean | number | string | readonly number[]> = {};
  for (const [key, field] of Object.entries(value)) {
    if (key === 'maxDirectionChanges') {
      if (!Number.isInteger(field) || field < 1 || field > 6) return undefined;
    } else if (key === 'patternEffect') {
      if (!Number.isInteger(field) || field < 0 || field > 2) return undefined;
    } else if (key === 'targetNoteVariation') {
      if (!Number.isInteger(field) || field < 1 || field > 6) return undefined;
    } else if (key === 'targetDirection') {
      if (!['ANY', 'ASC', 'DESC'].includes(field as string)) return undefined;
    } else if (key === 'newBlocksChance') {
      if (!Number.isInteger(field) || field < 0 || field > 100) return undefined;
    } else if (key === 'blockTypeWeights') {
      if (!Array.isArray(field) || field.length !== 7 || Array.from(field).some(weight => !Number.isInteger(weight) || weight < 0 || weight > 100)) return undefined;
      decoded[key] = [...field]; continue;
    } else if (key === 'blockChangePreference') {
      if (!Array.isArray(field) || !(field.length === 0 || field.length === 8 && new Set(field).size === 8
        && Array.from(field).every(change => Number.isInteger(change) && change >= 0 && change <= 7))) return undefined;
      decoded[key] = [...field]; continue;
    } else if (!['singleNoteExceptions', 'fillPausesPerChord', 'arpySurprises', 'patternFlip', 'emphasizeKey',
      'useDirectionsFromProgression', 'forceFirstOutline'].includes(key) || typeof field !== 'boolean') return undefined;
    decoded[key] = field;
  }
  return decoded;
}
