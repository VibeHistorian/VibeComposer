import type { ArrangementSection } from './arrangement.model';
import type { CompositionProject } from './project.model';
import { projectDrumPitches } from './project-drum-kit';
import { drumPanIsAutomatic, type CompositionTrack } from './track.model';
import { MAX_DRUM_SOUNDS } from '../music/drum-instruments';
import { chooseDrumGroove, drumProfile, kitPattern, type DrumJob, type DrumPatternGuide } from '../music/drum-kit-pattern';
import { DRUM_REROLL_STYLES } from '../music/drum-reroll-styles';
import { DEFAULT_DRUM_KIT_POLICY, type DrumGenerationPolicy, type InstrumentGenerationPolicy } from '../music/generation-policy.model';
import { INSTRUMENT_POOLS } from '../music/instrument-generation';
import { JavaRandom } from '../music/java-random';
import { partPatchesEqual, resolvePartTrack, sectionTrackSettingsDifferences } from '../music/part-settings';
import type { DrumSettings, PartSettingsPatch } from '../music/part-settings.model';
import type { PhraseNote } from '../music/phrase-note';
import { resolvedTrackPhrase } from '../music/phrase';
import { sectionChordDurations, sectionDurationBeats } from '../music/section-context';

type DrumTrack = Extract<CompositionTrack, { role: 'drums' }>;
type Bands = [number, number, number];
interface Slice {
  readonly project: CompositionProject;
  readonly section: ArrangementSection;
  readonly millisecondsPerBeat: number;
  readonly end: number;
  readonly weight: number;
  readonly budget: number;
  readonly bins: Map<number, { bands: Bands; attacks: number }>;
  load: number;
  ring: number;
}
interface Layer {
  readonly track: DrumTrack;
  readonly job: DrumJob;
  readonly notes: readonly (readonly PhraseNote[])[];
}
export interface DrumKitRerollScope {
  readonly sectionId?: string;
  readonly instrumentsOnly?: boolean;
  readonly forceInstruments?: boolean;
}

const BIN_MS = 25;
const JOB_PRIORITY: Readonly<Record<DrumJob, number>> = { pulse: 12, answer: 9, time: 6, reply: 4, accent: 4 };
const JOB_LOAD: Readonly<Record<DrumJob, number>> = { pulse: 2.5, answer: 2, time: 5, reply: 2.5, accent: .9 };
const isDrum = (track: CompositionTrack): track is DrumTrack => track.role === 'drums';

/** Evaluate several co-presence contexts plus the recurring base kit. Bound work independently of song length. */
function kitSlices(project: CompositionProject, tracks: readonly DrumTrack[], targets: ReadonlySet<string>, density: number,
  section?: ArrangementSection): Slice[] {
  const relevant = project.arrangement.filter(item => tracks.some(track => targets.has(track.id) && item.trackPresence[track.id]));
  const ranked = relevant.map(item => ({ item, count: tracks.filter(track => item.trackPresence[track.id]).length }))
    .sort((a, b) => b.count - a.count);
  const representatives = [...new Set([ranked[0]?.item,
    ranked.find(({ item }) => item.rolePartOverrides?.drums || tracks.some(track => item.trackPartOverrides?.[track.id] || item.trackPhrases?.[track.id]))?.item,
    ranked.at(-1)?.item].filter(item => item !== undefined))];
  const base: ArrangementSection = { id: 'kit-reroll-base', type: 'VERSE1', measures: 4,
    trackPresence: Object.fromEntries(tracks.map(track => [track.id, true])) };
  return (section ? [section] : [...representatives, base]).map(item => {
    const window = item.measures <= 4 ? item : { ...item, measures: 4 };
    const millisecondsPerBeat = 60_000 / (item.context?.tempoBpm ?? project.tempoBpm);
    const count = tracks.filter(track => item.trackPresence[track.id]).length;
    return { project: { ...project, arrangement: [window] }, section: window, millisecondsPerBeat,
      end: sectionDurationBeats(project, window) * millisecondsPerBeat,
      weight: item === base && representatives.length ? .8 : 1,
      budget: (6 + 18 * density) * Math.min(1, count / 4) * window.measures, bins: new Map(), load: 0, ring: 0 };
  });
}

export function drumKitSectionPatch(project: CompositionProject, section: ArrangementSection, track: DrumTrack,
  settings: DrumSettings, policy: DrumGenerationPolicy): PartSettingsPatch {
  const patch = sectionTrackSettingsDifferences(track, section, settings, project.melodyGlobal);
  const explicitSwing = section.trackPartOverrides?.[track.id]?.swingPercent;
  // A track swing equal to its base can still override the section-wide swing.
  return policy.patterns && policy.rerollStyle && policy.rerollStyle !== 'any' && explicitSwing !== undefined
    ? { ...patch, swingPercent: explicitSwing } : patch;
}

function layerNotes(slice: Slice, track: DrumTrack, policy: DrumGenerationPolicy, original?: DrumTrack): readonly PhraseNote[] {
  if (!slice.section.trackPresence[track.id]) return [];
  if (!original) return resolvedTrackPhrase(slice.project, track);
  const patch = drumKitSectionPatch(slice.project, slice.section, original, track.generatorSettings, policy);
  const section = { ...slice.section, trackPartOverrides: { ...slice.section.trackPartOverrides, [track.id]: patch } };
  return resolvedTrackPhrase({ ...slice.project, arrangement: [section] }, original);
}

function attackWeight(note: PhraseNote): number { return Math.min(1, Math.max(.25, note.velocity / 82)); }

/** Approximate masking in mono. Tails survive through subsequent onsets and occupy their own bands. */
function measureLayer(slice: Slice, notes: readonly PhraseNote[], gain: number, layering: number, ring: number, add = false): number {
  let load = 0, ringing = 0, collision = 0, shared = 0;
  let previousRingEnd = -Infinity;
  for (const note of notes) {
    const profile = drumProfile(note.midi);
    const start = note.startBeat * slice.millisecondsPerBeat;
    const first = Math.floor(start / BIN_MS);
    const count = Math.max(1, Math.ceil(profile.decay / BIN_MS));
    const strength = note.velocity / 100 * gain;
    const attack = attackWeight(note);
    load += attack;
    if (profile.decay >= 300) {
      ringing += strength * profile.decay / slice.end;
      if (start < previousRingEnd) collision += .6 * strength * (previousRingEnd - start) / profile.decay;
      previousRingEnd = start + profile.decay;
    }
    if (!add) {
      const near = (slice.bins.get(first - 1)?.attacks ?? 0) + (slice.bins.get(first)?.attacks ?? 0) + (slice.bins.get(first + 1)?.attacks ?? 0);
      shared += attack * Math.min(1, near);
    }
    for (let step = 0; step < count; step++) {
      const bin = first + step, energy = strength * (1 - step / count) ** 1.5;
      let used = slice.bins.get(bin);
      if (add) {
        if (!used) { used = { bands: [0, 0, 0], attacks: 0 }; slice.bins.set(bin, used); }
        for (let band = 0; band < 3; band++) used.bands[band] += profile.bands[band] * energy;
        if (step === 0) used.attacks += strength;
      } else if (used) {
        const [low, mid, high] = profile.bands;
        const overlap = low * used.bands[0] * 4.5 + mid * used.bands[1] * 1.15 + high * used.bands[2] * .9;
        collision += energy * overlap * (step === 0 ? 1 : .18);
      }
    }
  }
  if (add) {
    slice.load += load;
    slice.ring += ringing;
    return 0;
  }
  const overflow = Math.max(0, slice.load + load - slice.budget) - Math.max(0, slice.load - slice.budget);
  const ringBudget = .07 + ring * .75;
  const tailOverflow = Math.max(0, slice.ring + ringing - ringBudget) - Math.max(0, slice.ring - ringBudget);
  return (shared * (layering * 2.8 - .8) - collision - overflow * 2.5) / slice.section.measures - tailOverflow * 6;
}

/** Score only accents that survived settings resolution, timing and manual replacements. */
function styleAccentFit(slice: Slice, notes: readonly PhraseNote[], guide: DrumPatternGuide, partners: readonly PhraseNote[]): number {
  const durations = sectionChordDurations(slice.project, slice.section);
  const stepBeats = 4 / guide.grid;
  const accents: number[] = [];
  let slotStart = 0;
  for (let slot = 0; slot < slice.section.measures; slot++) {
    const duration = durations[slot % durations.length];
    for (let pass = 0; pass < duration; pass += 4) {
      for (const accent of guide.accents) {
        const offset = pass + accent * stepBeats;
        if (offset < duration) accents.push(slotStart + offset);
      }
    }
    slotStart += duration;
  }
  const matches = new Map<number, number>();
  for (const note of notes) {
    if (note.startBeat < 0 || note.startBeat >= slotStart) continue;
    const time = accents.find(beat => Math.abs(note.startBeat - beat) <= Math.min(.15, stepBeats / 2));
    if (time === undefined) continue;

    const paired = partners.some(partner => Math.abs(partner.startBeat - note.startBeat) * slice.millisecondsPerBeat <= BIN_MS);
    const merit = Math.min(1, note.velocity / 75) * (paired ? 7.6 : 6);
    matches.set(time, Math.max(matches.get(time) ?? 0, merit));
  }
  return [...matches.values()].reduce((total, merit) => total + merit, 0) / Math.max(1, accents.length);
}

function placeKit(layers: readonly Layer[], eligible: ReadonlySet<string>, width: number, direction: number): DrumTrack[] {
  const activity = (layer: Layer) => layer.notes.reduce((sum, notes) => sum + notes.reduce((level, note) =>
    level + (note.velocity / 100) ** 2 * Math.sqrt(drumProfile(note.midi).decay / 100), 0), 0) * layer.track.mix.volumePercent / 100;
  const moving = layers.filter(layer => eligible.has(layer.track.id) && drumPanIsAutomatic(layer.track));
  const fixed = layers.filter(layer => !moving.includes(layer));
  let balance = fixed.reduce((sum, layer) => sum + activity(layer) * layer.track.mix.panPercent, 0);
  const hats = [42, 44, 46];
  const fixedHat = fixed.find(layer => hats.includes(layer.track.generatorSettings.pitch) && layer.track.mix.panPercent !== 0);
  let hatSide = Math.sign(fixedHat?.track.mix.panPercent ?? 0);
  const pans = new Map<string, number>();
  for (const layer of [...moving].sort((a, b) => activity(b) - activity(a))) {
    const pitch = layer.track.generatorSettings.pitch, profile = drumProfile(pitch);
    const limit = layer.job === 'pulse' ? 0 : layer.job === 'answer' ? Math.min(10, profile.panLimit) : profile.panLimit;
    const isHat = hats.includes(pitch);
    const side = (isHat && hatSide) || -Math.sign(balance) || direction;
    if (isHat) hatSide = side;
    const pan = Math.round(limit * width) * side || 0;
    pans.set(layer.track.id, pan);
    balance += pan * activity(layer);
  }
  return layers.map(({ track }) => {
    const panPercent = pans.get(track.id);
    return panPercent === undefined || (track.mix.panPercent === panPercent && track.drumPan === 'auto') ? track
      : { ...track, drumPan: 'auto', mix: { ...track.mix, panPercent } };
  });
}

function preserveDrumLocks(source: DrumTrack, generated: DrumSettings, regeneratePattern: boolean,
  arrangement: readonly ArrangementSection[]): DrumSettings {
  const original = source.generatorSettings as unknown as Record<string, unknown>;
  const changes = generated as unknown as Record<string, unknown>;
  const locks = new Set(source.parameterLocks);
  for (const key of locks) {
    if (original[key] === undefined) delete changes[key];
    else changes[key] = original[key];
  }
  if (!regeneratePattern) return generated;
  // A base bound may be paired with an explicit bound in any section.
  let maximumForMinimum = 127, minimumForMaximum = 1;
  for (const section of arrangement) {
    const { velocityMin, velocityMax } = { ...section.rolePartOverrides?.drums, ...section.trackPartOverrides?.[source.id] };
    if (velocityMin === undefined && velocityMax !== undefined) maximumForMinimum = Math.min(maximumForMinimum, velocityMax);
    if (velocityMax === undefined && velocityMin !== undefined) minimumForMaximum = Math.max(minimumForMaximum, velocityMin);
  }
  let minimum = Math.min(generated.velocityMin ?? 69, maximumForMinimum);
  let maximum = Math.max(generated.velocityMax ?? 89, minimumForMaximum);
  if (minimum > maximum) {
    if (locks.has('velocityMin')) maximum = minimum;
    else minimum = maximum;
  }
  return { ...generated,
    ...(minimum !== (generated.velocityMin ?? 69) ? { velocityMin: minimum } : {}),
    ...(maximum !== (generated.velocityMax ?? 89) ? { velocityMax: maximum } : {}),
    ...(generated.useCustomVelocities && generated.customVelocities && !locks.has('customVelocities') ? {
      customVelocities: generated.customVelocities.map(value => Math.min(maximum,
        locks.has('velocityMin') && value > 0 ? Math.max(minimum, value) : value)),
    } : {}),
  };
}

/** Plan only eligible tracks. Fixed voices and authored notes contribute to the same scoring windows. */
export function rerollDrumKit(project: CompositionProject, ids: readonly string[], seed: bigint, policy: DrumGenerationPolicy,
  instruments: InstrumentGenerationPolicy, scope: DrumKitRerollScope = {}): DrumTrack[] {
  const tracks = project.tracks.filter(isDrum);
  const section = project.arrangement.find(item => item.id === scope.sectionId);
  const hasGenerated = (track: DrumTrack) => section
    ? section.trackPhrases?.[track.id] === undefined && (track.editedPhrase === undefined || section.generatedTrackIds?.includes(track.id))
    : track.editedPhrase === undefined || project.arrangement.some(item => item.generatedTrackIds?.includes(track.id) && item.trackPhrases?.[track.id] === undefined);
  const eligible = new Set(ids.filter(id => tracks.some(track => track.id === id && !track.rerollLocked
    && (scope.instrumentsOnly || hasGenerated(track)))));
  if (!eligible.size) return tracks;
  const canPitch = (track: DrumTrack) => !section && !track.instrumentLocked && (instruments.randomize || scope.forceInstruments === true);
  const targets = tracks.filter(track => eligible.has(track.id)).sort((a, b) => Number(canPitch(a)) - Number(canPitch(b)));
  const density = (policy.density ?? DEFAULT_DRUM_KIT_POLICY.density) / 100;
  const layering = (policy.layering ?? DEFAULT_DRUM_KIT_POLICY.layering) / 100;
  const ring = (policy.ring ?? DEFAULT_DRUM_KIT_POLICY.ring) / 100;
  const experiment = (policy.experiment ?? DEFAULT_DRUM_KIT_POLICY.experiment) / 100;
  const style = policy.rerollStyle && policy.rerollStyle !== 'any' ? DRUM_REROLL_STYLES[policy.rerollStyle] : undefined;
  const rhythmStyle = policy.patterns && !scope.instrumentsOnly ? style : undefined;
  const styledJobs = Object.keys(style?.voices ?? {}) as DrumJob[];
  const random = new JavaRandom(BigInt.asIntN(64, seed ^ 0x4452554d4b495421n));
  const groove = chooseDrumGroove(random, experiment);
  const pool = INSTRUMENT_POOLS[instruments.pool as Exclude<InstrumentGenerationPolicy['pool'], 'AUTO'>];
  const slices = kitSlices(project, tracks, eligible, density, section);
  const fixed = tracks.filter(track => !eligible.has(track.id));
  const sharedSwing = [...fixed, ...targets.filter(track => track.parameterLocks?.includes('swingPercent'))]
    .map(track => resolvePartTrack(track, section, project.melodyGlobal).generatorSettings as DrumSettings)
    .find(settings => settings.swingPercent !== 50)?.swingPercent;
  const swing = rhythmStyle?.grid === 12 ? 50 : sharedSwing ?? (rhythmStyle
    ? Math.min(50 + policy.maxSwing, Math.max(50 - policy.maxSwing, rhythmStyle.swing))
    : 50 + random.nextInt(policy.maxSwing * 2 + 1) - policy.maxSwing);
  const offset = policy.randomOffset ? random.nextInt(101) - 50 : 0;
  const jobs = new Map<DrumJob, number>(), pitches = new Set<number>(), layers: Layer[] = [];
  const add = (layer: Layer) => {
    layers.push(layer);
    const audiblePitches = new Set(layer.notes.flatMap(notes => notes.map(note => note.midi)));
    for (const pitch of audiblePitches) pitches.add(pitch);
    if (audiblePitches.size) {
      const occupied = new Set([...audiblePitches].map(pitch => pitch === layer.track.generatorSettings.pitch ? layer.job : drumProfile(pitch).job));
      for (const job of occupied) jobs.set(job, (jobs.get(job) ?? 0) + 1);
    }
    layer.notes.forEach((notes, index) => measureLayer(slices[index], notes, layer.track.mix.volumePercent / 100, layering, ring, true));
  };
  for (const track of fixed) add({ track, job: drumProfile(track.generatorSettings.pitch).job,
    notes: slices.map(slice => layerNotes(slice, track, policy)) });
  const reserved = new Set(projectDrumPitches(project, targets.filter(canPitch).map(track => track.id)));
  for (const [index, original] of targets.entries()) {
    const source = resolvePartTrack(original, section, project.melodyGlobal) as DrumTrack;
    const keepLocalSwing = rhythmStyle && (section?.context?.swingPercent !== undefined
      || section?.rolePartOverrides?.drums?.swingPercent !== undefined || section?.trackPartOverrides?.[source.id]?.swingPercent !== undefined);
    const patternSwing = keepLocalSwing ? source.generatorSettings.swingPercent : swing;
    const pitchCanChange = canPitch(source);
    const preferredStyle = rhythmStyle ?? (pitchCanChange ? style : undefined);
    const occupied = new Set([...reserved, ...targets.slice(index + 1).map(track => track.generatorSettings.pitch)]);
    const allowed = pitchCanChange ? pool.filter(pitch => (occupied.size < MAX_DRUM_SOUNDS || occupied.has(pitch))
      && (!scope.instrumentsOnly || pitch !== source.generatorSettings.pitch)) : [];
    const options = (allowed.length ? allowed : [source.generatorSettings.pitch]).flatMap(pitch => {
      const profile = drumProfile(pitch);
      const compatibleJobs = new Set([profile.job, ...(profile.substitute ? [profile.substitute] : []),
        ...styledJobs.filter(job => preferredStyle?.voices[job]?.pitches?.includes(pitch))]);
      return [...compatibleJobs].map(job => {
        const used = jobs.get(job) ?? 0;
        const coverage = !used ? JOB_PRIORITY[job] : job === 'pulse' ? -11 : job === 'answer' || job === 'time' ? -2 - used : -used * .6;
        const color = profile.decay >= 300 ? (ring - .35) * 8 : 0;
        const duplicate = pitches.has(pitch) ? 5.5 : 0;
        const centered = profile.panLimit === 0 && (!drumPanIsAutomatic(source) || scope.instrumentsOnly) ? Math.abs(source.mix.panPercent) / 12 : 0;
        const voice = preferredStyle?.voices[job];
        const preference = voice?.pitches?.indexOf(pitch) ?? -1;
        const timbre = preference < 0 ? 0 : ((voice?.pitchBias ?? 4) - preference) / (used + 1);
        const merit = coverage + color - duplicate - centered - (job === profile.job ? 0 : job === 'pulse' ? 4 : 2) + timbre;
        return { pitch, job, merit, priority: merit + random.nextDouble() * (1.8 + experiment * 4) };
      });
    }).sort((a, b) => b.priority - a.priority);
    const shortlist = options.slice(0, 8);
    const trials = Array.from({ length: 16 }, (_, attempt) => {
      // Reserve the last four styled trials for ordinary patterns on the highest-ranked voices.
      const guided = rhythmStyle && attempt < 12;
      const optionIndex = rhythmStyle ? attempt % 12 : attempt;
      const { pitch, job, merit } = shortlist[optionIndex % shortlist.length];
      const scale = .52 + density * 1.08;
      const used = jobs.get(job) ?? 0;
      const ordinaryLoad = Math.max(.35, JOB_LOAD[job] * scale * (.7 + random.nextDouble() * .6) / (1 + used * .5));
      const voice = used === 0 ? rhythmStyle?.voices[job] : undefined;
      const guide = rhythmStyle && voice && {
        grid: rhythmStyle.grid, accents: voice.accents.slice(0, Math.ceil(voice.accents.length * Math.min(1, .5 + 1.5 * density))),
        ornaments: voice.ornaments ?? [],
      };
      const load = guide ? Math.min(guide.accents.length + guide.ornaments.length, Math.max(guide.accents.length, ordinaryLoad)) : ordinaryLoad;
      const accentSpan = random.nextDouble() < .24 + density * .65 ? 1 : tracks.length > 10 ? 4 : 2;
      // Keep a static option for timekeepers even with Follow Melody at 100%.
      const patternPolicy = attempt % 3 === 0 ? { ...policy, melodyChance: 0 } : policy;
      const settings = scope.instrumentsOnly ? { ...source.generatorSettings, pitch } : preserveDrumLocks(source,
        kitPattern(random, patternPolicy, { ...source.generatorSettings, pitch }, job, groove, patternSwing, offset, load, accentSpan,
          guided ? guide : undefined),
        policy.patterns, section ? [] : project.arrangement);
      const track: DrumTrack = partPatchesEqual(source.generatorSettings, settings) ? source : { ...source, generatorSettings: settings };
      const notes = slices.map(slice => layerNotes(slice, track, policy, section ? original : undefined));
      const partners = guide && rhythmStyle?.sharedPulseAndAnswer && (job === 'pulse' || job === 'answer')
        ? layers.filter(layer => layer.job === (job === 'pulse' ? 'answer' : 'pulse')) : [];
      let total = 0, styleTotal = 0, weight = 0;
      for (const [window, slice] of slices.entries()) {
        if (!slice.section.trackPresence[track.id]) continue;
        const realized = notes[window];
        const localLoad = realized.reduce((sum, note) => sum + attackWeight(note), 0) / slice.section.measures;
        const space = measureLayer(slice, realized, track.mix.volumePercent / 100, layering, ring);
        total += slice.weight * (space - Math.abs(localLoad - load) * .55 - (realized.length ? 0 : 7));
        if (guide) styleTotal += slice.weight * styleAccentFit(slice, realized, guide, partners.flatMap(layer => layer.notes[window]));
        weight += slice.weight;
      }
      const score = merit + total / (weight || 1) * (1 - experiment * .52) + styleTotal / (weight || 1) * (1 - experiment * .35);
      return { track, job, notes, score };
    }).sort((a, b) => b.score - a.score);
    const nearBest = trials.filter(trial => trial.score >= trials[0].score - experiment * 5);
    const choice = nearBest[Math.floor(random.nextDouble() ** 2 * nearBest.length)];
    reserved.add(choice.track.generatorSettings.pitch);
    add(choice);
  }
  const result = section || scope.instrumentsOnly ? layers.map(layer => layer.track)
    : placeKit(layers, eligible, (policy.stereoWidth ?? DEFAULT_DRUM_KIT_POLICY.stereoWidth) / 100, random.nextBoolean() ? 1 : -1);
  const replacements = new Map(result.map(track => [track.id, track]));
  return tracks.map(track => replacements.get(track.id) ?? track);
}
