import type { ArpeggioSettings } from '../project/project.model';
import { getDiatonicChords, getPitchClass, chordPitches, type ProgressionChord, type ScaleMode } from './harmony';
import { JavaRandom } from './java-random';
import { decodePartPatch, velocityBounds } from './part-settings';
import { partFillMask } from './chord-span-fill';
import { effectivePartSeed, spannedPattern, spannedVelocities, expandedVoices } from './part-processing';
import { partRhythmMask } from './rhythm-pattern';
import { swingNotes } from './phrase-swing';
import { ARPEGGIO_PATTERNS, arpeggioPattern, chordNote, closestMajorPitch, javaShuffle } from './arpeggio-pattern';
import { melodyRhythm, type MelodyContext } from './melody-context';
import { soundingHarmony, type GenerationHarmony } from './harmony-voicing';

export interface ArpeggioNoteEvent {
  readonly midi: number;
  readonly startBeat: number;
  readonly durationBeats: number;
  readonly velocity: number;
  readonly chordIndex: number;
}

/** Supported ArpPhraseGenerator path with explicit section melody context. */
export function generateArpeggio(seed: bigint | number, key: string, scale: ScaleMode,
  progression: readonly number[], settings: ArpeggioSettings,
  customChords?: readonly (ProgressionChord | null)[], melody?: MelodyContext, harmony?: GenerationHarmony): ArpeggioNoteEvent[] {
  if (typeof seed === 'number' && !Number.isSafeInteger(seed)) throw new RangeError('Use bigint for 64-bit seeds.');
  if (progression.length < 1 || progression.length > 32 || !ARPEGGIO_PATTERNS.includes(settings.pattern)
    || !['eighth', 'sixteenth'].includes(settings.rate) || ![1, 2].includes(settings.octaves)
    || !decodePartPatch('arpeggio', settings)) throw new RangeError('Arpeggio settings are outside the supported range.');
  if (settings.generationEnabled === false) return [];
  const follows = settings.rhythm === 'melody';
  const rhythmSettings = { ...settings, rhythm: settings.rhythm ?? 'full' as const,
    hitsPerPattern: follows ? progression.length * 16 : settings.hitsPerPattern ?? (settings.rate === 'eighth' ? 8 : 16),
    chordSpan: follows ? progression.length : settings.chordSpan ?? 1,
    patternRepeat: follows ? 1 : settings.patternRepeat ?? 1 };
  const span = rhythmSettings.chordSpan, hits = rhythmSettings.hitsPerPattern, repeat = rhythmSettings.patternRepeat;
  const signedSeed = effectivePartSeed(seed, settings);
  const random = (offset: number) => new JavaRandom(BigInt.asIntN(32, signedSeed + BigInt(offset)));
  const pauses = follows ? melodyRhythm(melody, progression.length) : partRhythmMask({ ...rhythmSettings, patternFlip: false });
  const pauseRandom = random(30_004);
  for (let index = 0; index < pauses.length; index++) if (pauseRandom.nextInt(100) < (settings.pauseChance ?? 0)) pauses[index] = 0;
  const octaves = javaShuffle(Array.from({ length: hits * 2 }, (_, index) => index % 2 * 12), random(30_003)).slice(0, hits);
  const expand = (values: readonly number[]) => Array.from({ length: repeat }, () =>
    values.flatMap(value => [value, ...Array(span - 1).fill(0)])).flat();
  const octaveMap = expand(octaves);
  const randomMap = settings.pattern === 'random'
    ? expand(arpeggioPattern('random', hits, 3, 1, settings.arpPatternRotate ?? 0, undefined, random(30_002))) : [];
  const slice = (values: readonly number[], chord: number) => {
    const size = values.length / span;
    return values.slice(Math.round(chord % span * size), Math.round((chord % span + 1) * size));
  };
  const velocities = random(30_001), exceptions = random(30_002);
  const [minimum, maximum] = velocityBounds(settings);
  const fill = partFillMask(progression.length, settings), diatonic = getDiatonicChords(key, scale);
  const timeline: Array<{ midi: number; rhythm: number; duration: number; velocity: number; chordIndex: number }> = [];
  const voiced = harmony?.soundingVoicings ? harmony.soundingVoicings.map(chord => chord.map(pitch => pitch - 12))
    : harmony ? soundingHarmony(harmony.voicings, key, scale, -12) : undefined;
  progression.forEach((degree, chordIndex) => {
    const chord = diatonic.find(candidate => candidate.degree === degree);
    if (!chord) throw new RangeError(`Invalid chord degree ${degree}.`);
    const root = getPitchClass(chord.root)!;
    const pitches = expandedVoices(Array.from({ length: settings.octaves }, (_, octave) => voiced?.[chordIndex]
      ? voiced[chordIndex].map(pitch => pitch + octave * 12) : customChords?.[chordIndex]
      ? chordPitches(customChords[chordIndex]!, 48 + octave * 12)
      : [48 + root + octave * 12, 48 + root + (chord.quality === 'major' ? 4 : 3) + octave * 12,
        48 + root + (chord.quality === 'diminished' ? 6 : 7) + octave * 12]).flat(), settings);
    const pitchMap = settings.pattern === 'random' ? randomMap
      : expand(arpeggioPattern(settings.pattern, hits, pitches.length, 1,
        settings.arpPatternRotate ?? 0, settings.arpPatternCustom));
    const pitchPattern = slice(pitchMap, chordIndex), octavePattern = slice(octaveMap, chordIndex);
    // Java melody rhythm bypasses span/repeat slicing and per-pattern pauses.
    const pattern = follows ? (melody?.pitches[chordIndex] ?? Array<number>(16).fill(0))
      .map(value => settings.patternFlip ? value > 0 ? 0 : 1 : value)
      : spannedPattern(rhythmSettings, chordIndex, true, pauses);
    const custom = follows && hits > 32 ? undefined : spannedVelocities(rhythmSettings, chordIndex, true);
    const rate = follows ? 0.25 : 4 / (hits * repeat);
    const contour = settings.arpContour ?? [], interval = Math.max(Math.floor(hits * repeat / contour.length), 1);
    const melodyPitches = melody?.pitches[chordIndex];
    let lastMelodyIndex = 0;
    for (let step = 0, time = 0; time + 1e-9 < 4; step++, time += rate) {
      const pulse = step % pitchPattern.length, index = pitchPattern[pulse];
      let pitch = chordNote(index, pitches);
      const spannedPulse = chordIndex % span * (follows ? 16 : hits * repeat) + step;
      if (contour.length && spannedPulse % interval === 0) {
        pitch = 24 + chordNote(contour[Math.floor(spannedPulse / interval) % contour.length], [60, 62, 64, 65, 67, 69, 71]);
        if (settings.arpContourChordMode) pitch = closestMajorPitch(pitch + (customChords?.[chordIndex]?.root ?? root));
      }
      if (settings.useOctaveAdjustments && pitch >= 0) pitch += (index < 2 ? -12 : index < 6 ? 0 : 12) + octavePattern[pulse];
      if (settings.correctMelodyNotes && melody?.available !== false && melodyPitches?.length && pitch >= 0) {
        const melodyPitch = melodyPitches[lastMelodyIndex];
        lastMelodyIndex = Math.round((time + rate) / (4 / melodyPitches.length));
        if (melodyPitch !== undefined) {
          const distance = Math.abs(melodyPitch % 12 - pitch % 12);
          if (Math.min(distance, 12 - distance) === 1) pitch = pitch - pitch % 12 + melodyPitch % 12
            + (pitch % 12 >= 6 && melodyPitch % 12 < 6 ? 12 : 0);
        }
      }
      const velocity = custom ? custom[step % custom.length] : velocities.nextInt(maximum - minimum + 1) + minimum;
      const active = !!fill[chordIndex] && pattern[step % pattern.length] > 0 && velocity > 0 && pitch >= 0;
      const split = exceptions.nextInt(100) < (settings.exceptionChance ?? 0) && active;
      const duration = Math.min(rate, 4 - time) / (split ? 2 : 1);
      timeline.push({ midi: active ? pitch : -1, rhythm: duration, duration: duration * span, velocity, chordIndex });
      if (split) {
        const next = chordNote(pitchPattern[(pulse + 1) % pitchPattern.length], pitches);
        const second = next < 0 ? pitch : closestMajorPitch(Math.trunc((pitch + next) / 2));
        timeline.push({ midi: second, rhythm: duration, duration: duration * span, velocity: Math.max(0, velocity - 15), chordIndex });
      }
    }
  });
  swingNotes(timeline, hits % 2 === 0 ? settings.swingPercent ?? 50 : 50);
  let time = 0;
  return timeline.flatMap(note => {
    const startBeat = time; time += note.rhythm;
    return note.midi < 0 || note.velocity <= 0 ? [] : [{ midi: note.midi, startBeat,
      durationBeats: note.duration, velocity: note.velocity, chordIndex: note.chordIndex }];
  });
}
