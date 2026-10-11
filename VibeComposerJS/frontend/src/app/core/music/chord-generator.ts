import type { ChordSettings } from '../project/project.model';
import { getDiatonicChords, getPitchClass, chordPitches, type ProgressionChord } from './harmony';
import type { ScaleMode } from './harmony';
import { JavaRandom } from './java-random';
import { decodePartPatch, velocityBounds } from './part-settings';
import { partFillMask } from './chord-span-fill';
import { effectivePartSeed, spannedPattern, spannedVelocities, expandedVoices } from './part-processing';
import { swingNotes } from './phrase-swing';
import { strumChord } from './chord-strum';
import { soundingHarmony, type GenerationHarmony } from './harmony-voicing';


export interface ChordHitEvent {
  readonly pitches: readonly number[];
  readonly symbol: string;
  readonly startBeat: number;
  readonly durationBeats: number;
  readonly velocity: number;
  readonly chordIndex: number;
  readonly pitchOffsets?: readonly number[];
  readonly pitchDurations?: readonly number[];
}

const BEATS_PER_CHORD = 4;

/** Generate seeded chord hits from diatonic progression degrees. */
export function generateChordPart(
  seed: bigint | number,
  key: string,
  scale: ScaleMode,
  progression: readonly number[],
  settings: ChordSettings,
  customChords?: readonly (ProgressionChord | null)[],
  harmony?: GenerationHarmony,
): ChordHitEvent[] {
  if (typeof seed === 'number' && !Number.isSafeInteger(seed)) {
    throw new RangeError('Numeric seeds must be safe integers; use bigint for 64-bit seeds.');
  }
  if (progression.length < 1 || progression.length > 32
      || !Number.isInteger(settings.noteLengthPercent)
      || settings.noteLengthPercent < 25 || settings.noteLengthPercent > 200
      || (settings.voicing !== 'close' && settings.voicing !== 'open') || !decodePartPatch('chords', settings)) {
    throw new RangeError('Chord generation settings are outside the supported range.');
  }

  if (settings.generationEnabled === false) return [];
  const diatonicChords = getDiatonicChords(key, scale);
  const voiced = harmony?.soundingVoicings ?? (harmony ? soundingHarmony(harmony.voicings, key, scale) : undefined);
  const chordDefinitions = progression.map((degree, index) => {
    const chord = diatonicChords.find((candidate) => candidate.degree === degree);
    if (!chord) {
      throw new RangeError(`Chord degree ${degree} is not available in ${key} ${scale}.`);
    }
    const rootPitchClass = getPitchClass(chord.root);
    if (rootPitchClass === undefined) {
      throw new RangeError(`Unknown chord root: ${chord.root}.`);
    }

    const root = 60 + rootPitchClass;
    const third = chord.quality === 'major' ? 4 : 3;
    const fifth = chord.quality === 'diminished' ? 6 : 7;
    const pitches = settings.voicing === 'open'
      ? [root, root + fifth, root + 12 + third]
      : [root, root + third, root + fifth];
    const effectiveVoicing = voiced?.[index] ? [...voiced[index]] : undefined;
    if (effectiveVoicing && settings.voicing === 'open' && effectiveVoicing.length === 3) {
      effectiveVoicing[1] += 12; effectiveVoicing.sort((a,b) => a-b);
    }
    return { symbol: chord.symbol, pitches: expandedVoices(effectiveVoicing ?? (customChords?.[index]
      ? chordPitches(customChords[index]!, 60, settings.voicing === 'open') : pitches), settings) };
  });

  const signedSeed = effectivePartSeed(seed, settings);
  // ChordPart offset plus the primary part's order offset, matching the Java phrase consumer.
  const partSeed = BigInt.asIntN(32, signedSeed + 20_001n);
  const events: ChordHitEvent[] = [];
  const [velocityMin, velocityMax] = velocityBounds(settings);
  const fill = partFillMask(progression.length, settings);
  const pauseRandom = new JavaRandom(BigInt.asIntN(32, partSeed + 50n));
  const transitionRandom = new JavaRandom(partSeed);
  const timeline: Array<ChordHitEvent & { rhythm: number; duration: number; active: boolean; transitionPitches?: number[] }> = [];
  const joinMode = settings.patternJoinMode ?? 'NOJOIN';
  const stretchedByNote = joinMode === 'JOIN' ? 1 : 0;
  let skipNotes = 0;

  for (let chordIndex = 0; chordIndex < chordDefinitions.length; chordIndex++) {
    if (!fill[chordIndex]) { skipNotes = 0; timeline.push({ pitches: [], symbol: '', startBeat: chordIndex * 4, durationBeats: 4, velocity: 0, chordIndex, rhythm: 4, duration: 4, active: false }); continue; }
    const chord = chordDefinitions[chordIndex];
    const transition = transitionRandom.nextInt(100) < (settings.transitionChance ?? 0);
    const transitionChord = chordDefinitions[transitionRandom.nextInt(100) < (settings.transitionChance ?? 0)
      ? (chordIndex + 1) % chordDefinitions.length : chordIndex];
    const transitionPitches = [...transitionChord.pitches];
    const pattern = spannedPattern(settings, chordIndex);
    const velocityPattern = spannedVelocities(settings, chordIndex);
    const stepDuration = BEATS_PER_CHORD / pattern.length;
    const velocityRandom = new JavaRandom(BigInt.asIntN(32, partSeed + BigInt(chordIndex)));
    const span = settings.chordSpan ?? 1;
    const nextPattern = joinMode !== 'NOJOIN' && chordIndex % span < span - 1
      ? spannedPattern({ ...settings, patternFlip: false }, chordIndex + 1) : undefined;
    let nextP = -1;

    for (let step = 0; step < pattern.length; step++) {
      const velocity = velocityPattern ? velocityPattern[step % velocityPattern.length] : velocityRandom.nextInt(velocityMax - velocityMin + 1) + velocityMin;
      const paused = pauseRandom.nextInt(100) < (settings.pauseChance ?? 0);
      const active = pattern[step] > 0 && !(step <= nextP && stretchedByNote === 1) && skipNotes === 0;
      if (skipNotes > 0) skipNotes--;
      let durationMultiplier = 1;
      const joinApplicable = pattern[step] > 0 && step >= nextP;
      if (joinApplicable) {
        nextP = step + 1;
        while (nextP < pattern.length) {
          if (stepDuration * (step + durationMultiplier) + 0.01 > BEATS_PER_CHORD) break;
          if (Math.sign(pattern[nextP]) !== stretchedByNote && pattern[nextP] !== -1) break;
          durationMultiplier++; nextP++;
        }
      }
      if (nextP >= pattern.length && nextPattern) {
        skipNotes = 0;
        while (skipNotes < nextPattern.length && (nextPattern[skipNotes] === stretchedByNote || nextPattern[skipNotes] === -1)) skipNotes++;
        durationMultiplier += skipNotes;
      }
      const ratio = Math.min(((settings.strum ?? 0) > 750 ? 1.15 : 5) * (BEATS_PER_CHORD - step * stepDuration) / stepDuration,
        durationMultiplier, settings.noteLengthPercent / 100 * (joinApplicable && joinMode !== 'NOJOIN' ? durationMultiplier : 1));
      timeline.push({ rhythm: stepDuration, duration: stepDuration * ratio, active: active && !paused,
        ...(active && !paused && transition && step * stepDuration >= BEATS_PER_CHORD * (settings.transitionSplit ?? 625) / 1000
          ? { transitionPitches } : {}),
        pitches: transition && step * stepDuration >= BEATS_PER_CHORD * (settings.transitionSplit ?? 625) / 1000
          ? transitionChord.pitches : chord.pitches,
        symbol: transition && step * stepDuration >= BEATS_PER_CHORD * (settings.transitionSplit ?? 625) / 1000
          ? transitionChord.symbol : chord.symbol,
        startBeat: chordIndex * BEATS_PER_CHORD + step * stepDuration,
        durationBeats: stepDuration * settings.noteLengthPercent / 100,
        velocity,
        chordIndex,
      });
    }
  }

  // Java stores chord tones backwards: only the final (lowest) tone advances the rhythm.
  // Swing therefore adjusts that tone's duration, independently of the other voices.
  const flamRandoms = new Map<number, JavaRandom>();
  const rendered = timeline.flatMap(hit => {
    if (!flamRandoms.has(hit.chordIndex)) flamRandoms.set(hit.chordIndex,
      new JavaRandom(BigInt.asIntN(32, partSeed + 30n + BigInt(hit.chordIndex % 4))));
    const tones = hit.symbol === '' ? [{ midi: -1, offset: 0 }]
      : strumChord(hit.active ? hit.transitionPitches ?? hit.pitches : [-1], flamRandoms.get(hit.chordIndex)!,
        settings.strumType ?? 'ARP_U', settings.strum ?? 0, settings.strumPauseChance ?? 0);
    // Java's transition array is shared by subsequent transition hits in this chord.
    if (hit.transitionPitches) tones.forEach((tone, index) => hit.transitionPitches![tones.length - index - 1] = tone.midi);
    return tones.map((tone, index) => ({ ...tone, hit,
      rhythm: index === tones.length - 1 ? hit.rhythm : 0, duration: hit.duration }));
  });
  swingNotes(rendered, (settings.hitsPerPattern ?? 8) % 2 === 0 ? settings.swingPercent ?? 50 : 50);
  let time = 0;
  const grouped = new Map<typeof timeline[number], { start: number; tones: typeof rendered }>();
  for (const note of rendered) {
    if (!grouped.has(note.hit)) grouped.set(note.hit, { start: time, tones: [] });
    if (note.midi >= 0 && note.hit.velocity > 0) grouped.get(note.hit)!.tones.push(note);
    time += note.rhythm;
  }
  for (const [hit, group] of grouped) {
    if (!group.tones.length) continue;
    const tones = group.tones.reverse();
    const duration = tones[0].duration;
    events.push({ pitches: tones.map(tone => tone.midi), symbol: hit.symbol, startBeat: group.start,
      durationBeats: duration, velocity: hit.velocity, chordIndex: hit.chordIndex,
      ...(tones.some(tone => tone.offset !== 0) ? { pitchOffsets: tones.map(tone => tone.offset) } : {}),
      ...(tones.some(tone => tone.duration !== duration) ? { pitchDurations: tones.map(tone => tone.duration) } : {}) });
  }
  return events;
}
