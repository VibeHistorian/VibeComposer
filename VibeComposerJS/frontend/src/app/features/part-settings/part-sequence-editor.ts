import { ARPEGGIO_PATTERNS, javaShuffle, MAX_ARPEGGIO_PATTERN_LENGTH } from '../../core/music/arpeggio-pattern';
import { arpeggioPreviews } from '../../core/music/arpeggio-preview';
import { BASE_KEY, scaleNotes } from '../../core/music/harmony';
import { JavaRandom } from '../../core/music/java-random';
import { DEFAULT_MELODY_GLOBAL } from '../../core/music/melody-global-settings';
import { chooseMelodyTargets, startingMelodyTarget } from '../../core/music/melody-targets';
import { PART_CONTROLS, resolvePartTrack, type PartControl } from '../../core/music/part-settings';
import type { MelodySettings } from '../../core/music/part-settings.model';
import { baseGenerationHarmony, sectionGenerationHarmony } from '../../core/music/project-harmony';
import { sectionProject } from '../../core/music/section-context';
import { chooseMelodyPattern } from '../../core/music/track-generation';
import type { PartSettingsScope } from '../../core/project/part-settings-scope';
import type { CompositionProject } from '../../core/project/project.model';
import type { NumericSequenceEditor, NumericSequenceRange } from './numeric-sequence-dialog.component';
import { partEditorContext } from './part-editor-context';

export type PartSequenceKey = 'pattern' | 'melodyPatternOffsets' | 'chordNoteChoices' | 'arpContour';

export function partSequencePatch(key: PartSequenceKey, values: readonly number[]): ReturnType<NumericSequenceEditor['patch']> {
  return key === 'pattern' ? { pattern: 'custom', arpPatternCustom: values, arpPatternRotate: 0 } : { [key]: values };
}

const CONTROLS = {
  pattern: PART_CONTROLS.arpeggio.find(control => control.key === 'arpPatternCustom')!,
  melodyPatternOffsets: PART_CONTROLS.melody.find(control => control.key === 'melodyPatternOffsets')!,
  chordNoteChoices: PART_CONTROLS.melody.find(control => control.key === 'chordNoteChoices')!,
  arpContour: PART_CONTROLS.arpeggio.find(control => control.key === 'arpContour')!,
} satisfies Record<PartSequenceKey, PartControl>;

export function partSequenceRange(key: PartSequenceKey): NumericSequenceRange {
  const control = CONTROLS[key];
  return { minimum: control.minimum!, maximum: control.maximum! };
}

export function partSequenceRows(project: CompositionProject, scope: PartSettingsScope, key: PartSequenceKey): NumericSequenceEditor['rows'] {
  if (key === 'pattern') return arpeggioPreviews(project, scope);
  const { tracks, rows, section } = partEditorContext(project, scope);
  if ('sectionId' in scope && !section) return [];
  return tracks.map((track, index) => ({ trackId: track.id, name: track.name, values: rows[index][key] as readonly number[] }));
}

function fixedPresets(presets: Readonly<Record<string, readonly number[]>>): NumericSequenceEditor['presets'] {
  return Object.entries(presets).map(([label, values]) => ({ value: label, label, load: () => values }));
}

export function partSequenceEditor(project: CompositionProject, scope: PartSettingsScope, key: PartSequenceKey): NumericSequenceEditor {
  const control = CONTROLS[key];
  const common = {
    ...partSequenceRange(key),
    rows: partSequenceRows(project, scope, key),
    minimumSteps: control.allowEmpty ? 0 : 1,
    maximumSteps: 32,
    description: control.description ?? '',
    patch: (values: readonly number[]) => partSequencePatch(key, values),
  };
  switch (key) {
    case 'pattern':
      return {
        ...common, title: 'Arpeggio pattern', presetLabel: 'Load arpeggio direction', saveLabel: 'Save pattern',
        maximumSteps: MAX_ARPEGGIO_PATTERN_LENGTH,
        description: 'Chord-tone indices start at 0 for the lowest chord tone. Negative values go below the chord. This cycle includes Rotate and comes before Span and Repeat.',
        saveHint: 'Saves as Custom and resets Rotate to zero because the values already include it.',
        presets: ARPEGGIO_PATTERNS.map(value => ({
          value, label: value,
          load: trackId => arpeggioPreviews(project, scope, value).find(row => row.trackId === trackId)!.values,
        })),
        randomize: (_, values, seed) => javaShuffle(Array.from({ length: values.length }, (_, index) => index % 8), new JavaRandom(seed)),
      };
    case 'melodyPatternOffsets':
      return {
        ...common, title: 'Melody Words',
        presets: fixedPresets({
          'Repeat': [1], 'Call and response': [1, 2, 1, 3], 'Alternating': [1, 2],
          'Mirror': [1, -1], 'Through-composed': [1, 2, 3, 4],
        }),
        randomize: (_, _values, seed) => chooseMelodyPattern(seed),
      };
    case 'arpContour':
      return {
        ...common, title: 'Arpeggio Contour', emptyLabel: 'Off',
        presets: fixedPresets({
          'Off': [], 'Rise': [0, 1, 2, 4], 'Fall': [4, 2, 1, 0],
          'Arch': [0, 2, 4, 2, 0], 'Dip': [0, -2, -4, -2, 0],
        }),
        randomize: (_, values, seed) => {
          const random = new JavaRandom(seed);
          let degree = 0;
          return Array.from({ length: values.length || 4 }, (_, index) => {
            if (index > 0) degree = Math.max(-7, Math.min(7, degree + [-2, -1, 1, 2][random.nextInt(4)]));
            return index === 0 && values.length === 1 ? random.nextInt(7) - 3 : degree;
          });
        },
      };
    case 'chordNoteChoices':
      return { ...common, title: 'Melody Targets', ...melodyTargetEditor(project, scope) };
  }
}

function melodyTargetEditor(project: CompositionProject, scope: PartSettingsScope):
  Pick<NumericSequenceEditor, 'description' | 'presets' | 'randomize'> {
  const { section, tracks } = partEditorContext(project, scope);
  const harmony = section ? sectionGenerationHarmony(project, section, 'melody') : baseGenerationHarmony(project);
  const { scale } = sectionProject(project, section);
  const { contextualMelody, blockTargetMode } = { ...DEFAULT_MELODY_GLOBAL, ...project.melodyGlobal };
  const notes = scaleNotes(scale);
  const toneIndex = (chord: readonly number[], intervals: readonly number[]) => Math.max(0,
    chord.findIndex(pitch => intervals.includes((pitch - chord[0]) % 12)));
  const makePreset = (label: string, intervals: (index: number) => readonly number[]) => ({
    value: label, label,
    load: () => harmony.soundingRoots!.map((chord, index) => {
      const tone = toneIndex(chord, intervals(index));
      if (contextualMelody) {
        const pitchClass = chord[tone] % 12;
        const distance = (pc: number) => Math.min(Math.abs(pc - pitchClass), 12 - Math.abs(pc - pitchClass));
        return notes.reduce((best, pc, degree) => distance(pc) < distance(notes[best]) ? degree : best, 0);
      }
      if (blockTargetMode === 0) return tone;
      const target = startingMelodyTarget(harmony.roots, [tone], index, 0);
      const reference = startingMelodyTarget(harmony.roots, [0], index, blockTargetMode);
      return target - reference;
    }),
  });
  return {
    description: (contextualMelody ? 'Targets favor scale degrees at chord arrivals. 0 is the tonic.'
      : blockTargetMode === 0 ? 'Targets are chord-tone indices. 0 is the root.'
      : blockTargetMode === 1 ? 'Targets are diatonic offsets from each chord root.'
      : 'Targets are diatonic offsets from middle C in the generator’s internal key.')
      + ' Presets and Randomize use the harmony in this scope. The values repeat across the progression.',
    presets: [
      makePreset('Roots', () => [0]),
      makePreset('Thirds', () => [3, 4]),
      makePreset('Fifths', () => [6, 7, 8]),
      makePreset('Roots and fifths', index => index % 2 === 0 ? [0] : [6, 7, 8]),
    ],
    randomize: (trackId, _, seed) => {
      if (!contextualMelody && blockTargetMode === 0) {
        const random = new JavaRandom(seed);
        return harmony.roots.map(chord => random.nextInt(chord.length));
      }
      const track = tracks.find(track => track.id === trackId)!;
      const settings = resolvePartTrack(track, section, project.melodyGlobal).generatorSettings as MelodySettings;
      return chooseMelodyTargets(seed, harmony.degrees, project.melodyGlobal, BASE_KEY, scale, harmony.customChords, settings);
    },
  };
}
