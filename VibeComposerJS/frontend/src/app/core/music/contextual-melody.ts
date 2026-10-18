import type { MelodyGlobalSettings, MelodySettings, PhraseNote } from '../project/project.model';
import { chordPitches, getPitchClass, progressionChords, scaleNotes, type ProgressionChord, type ScaleMode } from './harmony';
import type { GenerationHarmony } from './harmony-voicing';
import { JavaRandom } from './java-random';
import { ContextualCatalogue } from './contextual-catalogue';
import { DEFAULT_MELODY_GLOBAL } from './melody-global-settings';
import { partFillMask } from './chord-span-fill';
import { velocityBounds } from './part-settings';
import { mixContextualSeed } from './contextual-harmony';
import { convertScalePitch } from './scales';

// TODO: Translate supplied custom melody durations when the app implements them.
// TODO: Move Blocks (the seven catalogue percentages), Phrase Bars, Beats per Bar,
// Pattern Effect and MIDI/Chord Target Mode to Settings > Melody.
// TODO: Move New Blocks%, Note Target Mode, Form and Purpose from global to track scope.
const BEAT = 960;
const mod = (n: number, d = 12) => (n % d + d) % d;
type Purpose = 'STATEMENT' | 'CONTINUATION' | 'QUESTION' | 'ANSWER' | 'CLOSING';
interface Phrase { start: number; end: number; motif: number; purpose: Purpose }
interface Slot { start: number; duration: number; rest: boolean; phrase: Phrase; arrival: boolean; appoggiatura: boolean }
type Role = 'CHORD' | 'EXTENSION' | 'PASSING' | 'CHROMATIC' | 'ANTICIPATION' | 'APPOGGIATURA' | 'SUSPENSION';
interface Planned { slot: Slot; pitch: number; role: Role }
interface State { path: Planned[]; score: number; last: number; previous: number; first: number; phraseFirst: number;
  phraseStart: number; run: number; serial: number }

/** Contextual planning operates on realized harmony before track/key transposition.
 * Rhythm is phrase based; a bounded beam resolves every ornament and respects range,
 * leap and ending constraints. It bypasses the old melody quota/repair passes.
 */
export function generateContextualMelody(seed: bigint | number, key: string, scale: ScaleMode,
  degrees: readonly number[], settings: MelodySettings, offset: number, global: MelodyGlobalSettings,
  custom?: readonly (ProgressionChord | null)[], harmony?: GenerationHarmony & { durations?: readonly number[] }): {
    notes: PhraseNote[]; pattern: Pick<PhraseNote, 'midi' | 'startBeat'>[]; skeleton: { midi: number; rhythm: number }[][];
  } {
  const options = { ...DEFAULT_MELODY_GLOBAL, ...global }, tonic = getPitchClass(key)!;
  if (options.rangeMin > options.rangeMax) throw new RangeError('Melody Low MIDI must not exceed Melody High MIDI.');
  const notesInScale = scaleNotes(scale), scaleClasses = new Set(notesInScale.map(p => mod(p)));
  const chords = harmony?.soundingVoicings ?? progressionChords(key, scale, degrees, custom).map(c => chordPitches(c, 60));
  const definitions = progressionChords(key, scale, degrees, custom);
  const events = chords.map((pitches, index) => ({ classes: new Set(pitches.map(p => mod(p - tonic))), root: mod(definitions[index].root - tonic),
    duration: Math.round((harmony?.durations?.[index] ?? 4) * BEAT), start: 0, end: 0 }));
  let total = 0;
  for (const event of events) { event.start = total; total += event.duration; event.end = total; }
  const at = (tick: number) => events.find(event => tick >= event.start && tick < event.end)!;
  const baseSeed = BigInt(settings.patternSeed || Number(BigInt.asIntN(32, BigInt(seed)))) + BigInt(offset);
  const motifs = settings.melodyPatternOffsets ?? [1,2,1,3], targets = settings.chordNoteChoices ?? [0,2,2,4];
  const span = options.phraseBars * options.beatsPerBar * BEAT, phrases: Phrase[] = [];
  for (let start = 0; start < total; start += span) {
    const index = phrases.length, end = Math.min(total, start + span);
    let purpose: Purpose = options.form === 'QUESTION_ANSWER' ? index % 2 === 0 ? 'QUESTION' : 'ANSWER'
      : options.form === 'SENTENCE' && index % 3 === 2 ? 'CONTINUATION' : index % 2 === 0 ? 'STATEMENT' : 'CONTINUATION';
    if (end === total && options.ending === 'CLOSED') purpose = 'CLOSING';
    phrases.push({ start, end, purpose, motif: motifs[index % motifs.length] });
  }
  const fill = partFillMask(degrees.length, settings), leap = Math.min(24, 2 + (settings.maxBlockChange ?? 7) + (settings.blockJump ?? 1));
  const overlapping = (slot: Slot) => events.filter(event => event.start < slot.start + slot.duration && event.end > slot.start);
  const makeRhythm = (variant: number): Slot[] => {
    const slots: Slot[] = [];
    for (const phrase of phrases) {
      const random = new JavaRandom(baseSeed ^ 0x72687974686dn ^ 0x9e3779b97f4a7c15n * BigInt(Math.abs(phrase.motif)) ^ BigInt(variant));
      const arrivals = [phrase.start];
      if (phrase.end - phrase.start > 2 * BEAT || phrase.purpose === 'CLOSING' && phrase.end - phrase.start > BEAT) arrivals.push(phrase.end - BEAT);
      let index = 0;
      for (let cursor = phrase.start; cursor < phrase.end; index++) {
        const event = at(cursor), arrival = arrivals.includes(cursor);
        let available = Math.min(phrase.end, ...arrivals.filter(t => t > cursor)) - cursor;
        if (cursor % BEAT !== 0) available = Math.min(available, BEAT - cursor % BEAT);
        const nextEvent = event.end < total ? at(event.end) : undefined;
        const change = nextEvent && (event.classes.size !== nextEvent.classes.size || [...event.classes].some(pc => !nextEvent.classes.has(pc)));
        const approach = change && event.end - cursor <= BEAT;
        if (approach) available = Math.min(available,event.end - cursor);
        let preferred = (35 - (settings.speed ?? 50)) / 50;
        if (cursor % BEAT !== 0) preferred -= 0.25;
        if (phrase.purpose === 'CONTINUATION') preferred -= 0.2;
        if (approach) {
          preferred -= 0.45;
          if (event.classes.has(mod(event.root + 4)) && event.classes.has(mod(event.root + 10)) && mod(nextEvent.root - event.root) === 5) preferred -= 0.25;
        }
        if (arrival) preferred += 0.45;
        const durations = [240,480,960,1920], weights = durations.map((duration,i) => duration > available ? 0 : Math.exp(-0.5 * ((i - 2 - preferred) / 0.9) ** 2));
        const peak = Math.max(...weights);
        weights.forEach((weight,i) => { if (durations[i] <= available) weights[i] = weight + peak * 0.002; });
        let roll = random.nextDouble() * weights.reduce((sum,w) => sum+w,0);
        let duration = Math.min(240,available);
        for (let i = 0; i < weights.length; i++) if (weights[i] && (roll -= weights[i]) < 0) { duration = durations[i]; break; }
        if (phrase.purpose === 'CLOSING' && phrase.end - cursor <= BEAT) duration = phrase.end - cursor;
        const rest = index > 0 && cursor + duration < phrase.end && random.nextInt(100) < (settings.pauseChance ?? 0) + (phrase.purpose === 'QUESTION' ? 8 : 0);
        const appoggiatura = !rest && phrase.purpose === 'CONTINUATION' && cursor % BEAT === 0 && random.nextInt(100) < 12;
        const chordIndex = events.indexOf(event);
        const muted = rest || !fill[chordIndex] || arrival && cursor + duration < total
          && new JavaRandom(baseSeed ^ BigInt(cursor) ^ 0x7374617274n).nextInt(100) >= (settings.startNoteChance ?? 100);
        const slot = { start: cursor, duration, rest: muted, phrase, arrival, appoggiatura };
        if (!muted && duration >= BEAT && new JavaRandom(baseSeed ^ BigInt(cursor) ^ 0x73706c6974n).nextInt(100) < (settings.splitChance ?? 0)) {
          slots.push({ ...slot, duration: duration / 2 }, { ...slot, start: cursor + duration / 2, duration: duration / 2, arrival: false, appoggiatura: false });
        } else slots.push(slot);
        cursor += duration;
      }
    }
    return slots;
  };
  const contour = (phrase: Phrase) => options.targetDirection === 'ASC' ? 0 : options.targetDirection === 'DESC' ? 1
    : (Number(mixContextualSeed(baseSeed ^ BigInt(Math.abs(phrase.motif)) * 0x9e3779b97f4a7c15n ^ 0x636f6e746f7572n) & 3n) ^ (phrase.motif < 0 ? 1 : 0));
  const stableRole = (event: typeof events[number], pitch: number): Role | undefined => {
    if (event.classes.has(mod(pitch))) return 'CHORD';
    const interval = mod(pitch - event.root);
    if (options.style === 'JAZZ' && options.tension > 0 && scaleClasses.has(mod(pitch))
      && (interval === 2 || interval === 9 || interval === 11 && event.classes.has(mod(event.root + 4)) && !event.classes.has(mod(event.root + 10))
        || interval === 10 && event.classes.has(mod(event.root + 3)))) return 'EXTENSION';
    return undefined;
  };
  const pending = (role: Role) => !['CHORD', 'EXTENSION'].includes(role);
  let result: Planned[] | undefined;
  for (let variant = 0; variant < 3 && !result; variant++) {
    const rhythm = makeRhythm(variant === 2 ? 0 : variant);
    const slots = variant === 2 ? rhythm.flatMap(slot => {
      const split: Slot[] = [];
      for (let cursor = slot.start; cursor < slot.start + slot.duration;) {
        const end = Math.min(slot.start + slot.duration, at(cursor).end);
        split.push({ ...slot, start: cursor, duration: end - cursor, arrival: cursor === slot.start && slot.arrival }); cursor = end;
      }
      return split;
    }) : rhythm;
    const finalIndex = slots.reduce((last,slot,i) => slot.rest ? last : i, -1);
    const catalogue = new ContextualCatalogue(baseSeed, options.newBlocksChance);
    const soundsRemaining = slots.map((_,i) => slots.slice(i + 1).filter(slot => !slot.rest).length);
    const loopArrivals: number[] = [];
    if (options.ending === 'LOOP' && finalIndex >= 0) {
      for (let pitch = options.rangeMin; pitch <= options.rangeMax; pitch++) if (overlapping(slots[finalIndex]).every(chord => stableRole(chord,pitch))) loopArrivals.push(pitch);
    }
    let serial = 0;
    let beam: State[] = [{ path: [], score: 0, last: -1, previous: -1, first: -1, phraseFirst: -1, phraseStart: -1, run: 0, serial: serial++ }];
    for (let index = 0; index < slots.length && beam.length; index++) {
      const slot = slots[index], event = at(slot.start), next: State[] = [];
      const accented = slot.start % BEAT === 0 && (slot.start / BEAT % options.beatsPerBar === 0
        || options.beatsPerBar % 2 === 0 && slot.start / BEAT % options.beatsPerBar === options.beatsPerBar / 2) || slot.duration >= BEAT;
      const random = new JavaRandom(baseSeed ^ BigInt(slot.start) * 0x9e3779b97f4a7c15n), preferences = Array.from({ length: 128 }, () => random.nextDouble() * 0.25);
      for (const state of beam) {
        const prior = state.path.at(-1);
        if (slot.rest) {
          if (!prior || prior.pitch < 0 || !pending(prior.role)) next.push({ ...state,
            phraseFirst: state.phraseStart === slot.phrase.start ? state.phraseFirst : -1, phraseStart: slot.phrase.start, run: 0,
            path: [...state.path, { slot, pitch: -1, role: 'CHORD' }], serial: serial++ });
          continue;
        }
        for (let pitch = options.rangeMin; pitch <= options.rangeMax; pitch++) {
          let role = stableRole(event, pitch);
          const overlaps = overlapping(slot);
          if (!overlaps.every(chord => stableRole(chord,pitch))) role = undefined;
          else if (overlaps.some(chord => !chord.classes.has(mod(pitch)))) role = 'EXTENSION';
          if (!role) {
            if (options.tension === 0 || index === finalIndex) continue;
            if (overlaps.length === 2 && event.classes.has(mod(pitch)) && !overlaps[1].classes.has(mod(pitch))
              && event.end - slot.start >= 240 && slot.start + slot.duration - event.end <= BEAT) role = 'SUSPENSION';
            else if (!accented && slot.duration <= 480 && slot.start + slot.duration === event.end && event.end < total
              && at(event.end).classes.has(mod(pitch)) && new JavaRandom(baseSeed ^ BigInt(slot.start) ^ 0x6c656164n).nextInt(100) < (settings.leadChordsChance ?? 0)) role = 'ANTICIPATION';
            else if (slot.appoggiatura && slot.duration <= BEAT && state.last >= 0 && Math.abs(pitch - state.last) > 2 && scaleClasses.has(mod(pitch))) role = 'APPOGGIATURA';
            else if (!accented && state.last >= 0 && prior?.role === 'CHORD' && Math.abs(pitch - state.last) <= 2 && slot.start + slot.duration < event.end) {
              role = scaleClasses.has(mod(pitch)) ? 'PASSING' : options.chromaticApproaches ? 'CHROMATIC' : undefined;
            }
            if (!role) continue;
          }
          const movement = state.last < 0 ? 0 : pitch - state.last;
          if (state.last >= 0 && Math.abs(movement) > leap) continue;
          if (options.ending === 'LOOP' && finalIndex >= 0) {
            const first = state.first < 0 ? pitch : state.first;
            const distances = loopArrivals.filter(target => Math.abs(target - first) <= leap).map(target => Math.abs(target - pitch));
            if (Math.min(...distances) > soundsRemaining[index] * leap) continue;
          }
          if (prior && prior.pitch >= 0 && pending(prior.role) && (pending(role)
            || (prior.role === 'ANTICIPATION' ? movement !== 0 : prior.role === 'SUSPENSION' ? movement >= 0 || movement < -2
              : movement === 0 || Math.abs(movement) > (prior.role === 'CHROMATIC' ? 1 : 2)))) continue;
          if (index === finalIndex && options.ending === 'CLOSED' && event.classes.has(0) && mod(pitch) !== 0) continue;
          if (index === finalIndex && options.ending === 'LOOP' && state.first >= 0 && Math.abs(pitch - state.first) > leap) continue;
          const kind = contour(slot.phrase), position = (slot.start - slot.phrase.start) / (slot.phrase.end - slot.phrase.start);
          const width = Math.min(5,(options.rangeMax - options.rangeMin) / 4), center = (options.rangeMin + options.rangeMax) / 2;
          const origin = state.phraseStart === slot.phrase.start && state.phraseFirst >= 0 ? state.phraseFirst
            : center + (kind === 0 ? -width : kind === 1 ? width : kind === 2 ? -width / 2 : width / 2);
          const arc = kind <= 1 ? position * 2 * width * (kind === 0 ? 1 : -1) : Math.sin(position * Math.PI) * width * (kind === 2 ? 1 : -1);
          const direction = kind === 0 ? 1 : kind === 1 ? -1 : (position < 0.5 ? 1 : -1) * (kind === 2 ? 1 : -1);
          let score = state.score + preferences[pitch] + slot.duration / BEAT * (pending(role) ? -(1 - options.tension / 100) : accented ? 2 : 0.5)
            - Math.abs(pitch - origin - arc) * 0.18;
          if (state.last >= 0) {
            score -= Math.abs(movement) * 0.3;
            score += direction * Math.sign(movement) * Math.min(3,Math.abs(movement)) * (options.targetDirection === 'ANY' ? 0.35 : 0.6);
            score += movement === 0 ? -0.9 - Math.min(4,Math.max(0,state.run - 1)) * 0.3 : Math.abs(movement) <= 2 ? 0.5 : 0;
            if (state.previous >= 0 && Math.abs(state.last - state.previous) > 4) score += Math.sign(movement) === -Math.sign(state.last - state.previous) && Math.abs(movement) <= 2 ? 1.5 : -1.5;
            if (prior && prior.slot.start < event.start && options.style !== 'MODAL') {
              const before = at(prior.slot.start);
              if (mod(event.root - before.root) === 5 && before.classes.has(mod(before.root + 4))) {
                if (mod(state.last - before.root) === 4) score += movement === 1 ? 5 : -3;
                if (before.classes.has(mod(before.root + 10)) && mod(state.last - before.root) === 10) score += movement < 0 && movement >= -2 ? 4 : -3;
              }
            }
            // Java applies the history penalty only when this note introduces a reversal.
            if (state.previous >= 0 && movement !== 0 && state.last !== state.previous
                && Math.sign(movement) !== Math.sign(state.last - state.previous)) {
              let reversals = 0, dir = Math.sign(movement);
              for (let i = state.path.length - 1; i > 0 && state.path[i].slot.start >= slot.phrase.start; i--) {
                const a = state.path[i].pitch, b = state.path[i - 1].pitch;
                if (a < 0 || b < 0 || a === b) continue;
                const d = Math.sign(a - b); if (dir && d !== dir) reversals++; dir = d;
              }
              score -= Math.max(0,reversals - options.maxDirectionChanges) * 0.8;
            }
            for (let i = state.path.length - 1; i > 0; i--) {
              const earlier = state.path[i], previousPitch = state.path.slice(0,i).reverse().find(note => note.pitch >= 0)?.pitch;
              if (earlier.slot.phrase.motif === slot.phrase.motif && earlier.slot.start % (4 * BEAT) === slot.start % (4 * BEAT) && previousPitch !== undefined) {
                score += Math.sign(movement) === Math.sign(earlier.pitch - previousPitch) ? 0.8 : -0.4; break;
              }
            }
            if (prior && prior.pitch >= 0 && prior.slot.phrase.start === slot.phrase.start) {
              const earlier = state.path.at(-2), triple = earlier && earlier.pitch >= 0 && earlier.slot.phrase.start === slot.phrase.start;
              const gesture = triple ? [earlier.pitch, prior.pitch, pitch] : [prior.pitch, pitch];
              const beginning = triple ? earlier : prior;
              const sounds = state.path.filter(n => n.pitch >= 0 && n.slot.phrase.start === slot.phrase.start).length;
              score += catalogue.affinity(gesture, at(beginning.slot.start).classes, scaleClasses,
                options.blockTypeWeights, slot.phrase.motif, Math.max(0, sounds - gesture.length + 1), direction);
            }
          }
          if (slot.arrival) {
            const choice = targets[events.indexOf(event) % targets.length], pc = mod(notesInScale[mod(choice,7)]);
            score -= Math.min(mod(pitch - pc),mod(pc - pitch)) * 0.6;
          }
          if (index === finalIndex && options.ending === 'LOOP' && state.first >= 0) score -= Math.abs(pitch - state.first) * 0.75;
          next.push({ path: [...state.path, { slot, pitch, role }], score, last: pitch, previous: state.last,
            first: state.first < 0 ? pitch : state.first, phraseFirst: state.phraseStart === slot.phrase.start && state.phraseFirst >= 0 ? state.phraseFirst : pitch,
            phraseStart: slot.phrase.start, run: state.phraseStart === slot.phrase.start && prior?.pitch === pitch ? state.run + 1 : 1, serial: serial++ });
        }
      }
      next.sort((a,b) => b.score - a.score || a.serial - b.serial); beam = next.slice(0,32);
    }
    result = beam[0]?.path;
  }
  if (!result) throw new RangeError('No contextual melody satisfies the range, leap and ending constraints.');
  const [low,high] = velocityBounds(settings,[80,105]), pattern: Pick<PhraseNote, 'midi' | 'startBeat'>[] = [];
  const skeleton: { midi: number; rhythm: number }[][] = events.map(() => []);
  const notes = result.flatMap((note,index) => {
    const slot = note.slot, event = at(slot.start);
    pattern.push({ midi: note.pitch < 0 ? -1 : note.pitch + tonic + (settings.transpose ?? 0), startBeat: slot.start / BEAT });
    skeleton[events.indexOf(event)].push({ midi: note.pitch < 0 ? -1 : convertScalePitch(note.pitch,notesInScale,scaleNotes('major')), rhythm: slot.duration / BEAT });
    if (note.pitch < 0) return [];
    const performance = new JavaRandom(baseSeed ^ BigInt(slot.start) * 0xd1b54a32d192ed03n);
    let velocity = low + performance.nextInt(high - low + 1);
    if (slot.arrival && performance.nextInt(100) < (settings.accents ?? 100)) velocity = Math.min(high,velocity + Math.max(1,Math.trunc((high-low)/4)));
    const rawStart = slot.start / BEAT, swing = (settings.swingPercent ?? 50) / 50 - 1;
    // Bound swing and articulation to the validated harmonic event.
    const startBeat = Math.max(event.start / BEAT, Math.min((event.end - 1) / BEAT, rawStart + (slot.start % BEAT === BEAT / 2 ? swing * 0.5 : 0)));
    const duration = slot.duration / BEAT * 0.95 * (0.9 + performance.nextDouble() * 0.1) * (settings.noteLengthMultiplier ?? 100) / 100;
    return [{ id: `melody-${index}`, midi: Math.max(0,Math.min(127,note.pitch + tonic + (settings.transpose ?? 0))), startBeat,
      durationBeats: Math.max(1 / BEAT,Math.min(duration,event.end / BEAT - startBeat)), velocity }];
  });
  // The app's phrase contract uses four beats per chord; arrangement mapping then
  // restores each actual duration. Plan on actual beats, invert that mapping once here.
  const gridBeat = (beat: number) => {
    if (beat >= total / BEAT) return events.length * 4;
    const event = at(Math.max(0,beat * BEAT));
    return events.indexOf(event) * 4 + (beat * BEAT - event.start) / event.duration * 4;
  };
  return { notes: notes.map(note => ({ ...note, startBeat: gridBeat(note.startBeat),
    durationBeats: gridBeat(note.startBeat + note.durationBeats) - gridBeat(note.startBeat) })),
    pattern: pattern.map(note => ({ ...note, startBeat: gridBeat(note.startBeat) })),
    skeleton: skeleton.map((notes,index) => notes.map(note => ({ ...note, rhythm: note.rhythm * 4 * BEAT / events[index].duration }))) };
}
