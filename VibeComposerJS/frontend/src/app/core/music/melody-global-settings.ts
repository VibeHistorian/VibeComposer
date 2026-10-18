import type { MelodyGlobalSettings } from '../project/project.model';
import { DEFAULT_BLOCK_TYPE_WEIGHTS } from './melody-catalogue';

export const DEFAULT_MELODY_GLOBAL = Object.freeze({
  contextualMelody: false, rangeMin: 48, rangeMax: 72, rangeRandomizeOnCompose: false,
  tension: 35, ending: 'LOOP' as const, form: 'STATEMENT_VARIATION' as const,
  style: 'TONAL' as const, chromaticApproaches: false, phraseBars: 2, beatsPerBar: 4,
  singleNoteExceptions: false, maxDirectionChanges: 2, fillPausesPerChord: false,
  patternEffect: 2, arpySurprises: false, patternFlip: false, emphasizeKey: false,
  tonicNoteTarget: 0, modeNoteTarget: 0, chordNoteTarget: 0, replaceAvoidNotes: 0, blockTargetMode: 2,
  targetNoteVariation: 3, targetDirection: 'ASC' as const,
  useDirectionsFromProgression: false, forceFirstOutline: false,
  newBlocksChance: 25, blockTypeWeights: DEFAULT_BLOCK_TYPE_WEIGHTS, blockChangePreference: Object.freeze([] as number[]),
});

export function decodeMelodyGlobal(value: unknown, partial = false): MelodyGlobalSettings | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const decoded: Record<string, boolean | number | string | readonly number[]> = {};
  for (const [key, field] of Object.entries(value)) {
    if (['contextualMelody', 'rangeRandomizeOnCompose', 'chromaticApproaches'].includes(key)) {
      if (typeof field !== 'boolean') return undefined;
    } else if (['rangeMin', 'rangeMax', 'tension', 'phraseBars', 'beatsPerBar'].includes(key)) {
      const max = key === 'tension' ? 100 : key === 'phraseBars' ? 16 : key === 'beatsPerBar' ? 12 : 127;
      if (!Number.isInteger(field) || field < (key === 'phraseBars' || key === 'beatsPerBar' ? 1 : 0) || field > max) return undefined;
    } else if (['ending', 'form', 'style'].includes(key)) {
      const choices = key === 'ending' ? ['OPEN', 'CLOSED', 'LOOP'] : key === 'style' ? ['TONAL', 'MODAL', 'JAZZ']
        : ['STATEMENT_VARIATION', 'QUESTION_ANSWER', 'SENTENCE', 'CONTINUOUS'];
      if (!choices.includes(field as string)) return undefined;
    } else if (key === 'maxDirectionChanges') {
      if (!Number.isInteger(field) || field < 1 || field > 6) return undefined;
    } else if (key === 'patternEffect' || key === 'blockTargetMode') {
      if (!Number.isInteger(field) || field < 0 || field > 2) return undefined;
    } else if (key === 'targetNoteVariation') {
      if (!Number.isInteger(field) || field < 1 || field > 6) return undefined;
    } else if (key === 'targetDirection') {
      if (!['ANY', 'ASC', 'DESC'].includes(field as string)) return undefined;
    } else if (['newBlocksChance', 'tonicNoteTarget', 'modeNoteTarget', 'chordNoteTarget'].includes(key)) {
      if (!Number.isInteger(field) || field < 0 || field > 100) return undefined;
    } else if (key === 'replaceAvoidNotes') {
      if (!Number.isInteger(field) || field < 0 || field > 2) return undefined;
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
  if ((!partial || decoded['rangeMin'] !== undefined && decoded['rangeMax'] !== undefined)
    && Number(decoded['rangeMin'] ?? DEFAULT_MELODY_GLOBAL.rangeMin) > Number(decoded['rangeMax'] ?? DEFAULT_MELODY_GLOBAL.rangeMax)) return undefined;
  return decoded;
}
