import { drumInstrumentName, isDrumPitch, MAX_DRUM_SOUNDS } from '../music/drum-instruments';
import { midiPointIndex, type MidiPoint, type MidiProjectSource, type MidiSourceNote, type MidiSourceTrack } from '../music/midi-project-source';
import type { SectionType } from './arrangement.model';
import { MAX_ARRANGEMENT_MEASURES, MAX_ARRANGEMENT_SECTIONS, MAX_PHRASE_NOTES,
  MAX_PROJECT_TRACKS, MAX_SECTION_MEASURES } from './project-limits';

const DEFAULT_TEMPO = 120;
const DEFAULT_MEASURE_BEATS = 4;
const MIN_MEASURE_BEATS = 0.25;
const PREFERRED_SECTION_MEASURES = 8;

export interface MidiSectionPlan {
  /** All measure starts and the section end, in source ticks. */
  readonly boundaries: readonly number[];
  readonly type: SectionType;
  readonly bpm: number;
}

interface ImportedTrack extends MidiSourceTrack {
  readonly drumPitch?: number;
  readonly drumSlotPitch?: number;
}

function splitDrumTracks(tracks: readonly MidiSourceTrack[], endTick: number, warnings: string[]): ImportedTrack[] {
  const drums = tracks.filter(track => track.channel === 10);
  const pitches = new Set<number>();
  for (const track of drums) for (const note of track.notes) {
    if (note.tick >= endTick) break;
    pitches.add(note.midi);
  }
  const sorted = [...pitches].sort((a, b) => a - b);
  const groups = sorted.slice(0, MAX_DRUM_SOUNDS).map(pitch => ({ pitch, notes: [] as MidiSourceNote[], source: undefined as MidiSourceTrack | undefined }));
  const byPitch = new Map(groups.map(group => [group.pitch, group]));
  const last = groups.at(-1);
  let mergedMix = false;
  for (const track of drums) for (const note of track.notes) {
    if (note.tick >= endTick) break;
    const group = byPitch.get(note.midi) ?? last!;
    group.source ??= track;
    mergedMix ||= group.source.volume !== track.volume || group.source.pan !== track.pan;
    group.notes.push(note);
  }
  if (mergedMix) warnings.push('Combined drum hits with different source mix settings into shared tracks.');
  const overflow = sorted.length > MAX_DRUM_SOUNDS;
  if (overflow) warnings.push(`Percussion sounds ${MAX_DRUM_SOUNDS} onward share the last drum track and one chromatic MIDI slot.`);
  return groups.map(group => {
    const source = group.source!;
    const combined = overflow && group === last;
    const label = combined ? 'Other percussion' : drumInstrumentName(group.pitch);
    const suffix = ` · ${label}`;
    const name = sorted.length === 1 && source.name ? source.name
      : source.name ? source.name.slice(0, 48 - suffix.length).trimEnd() + suffix : label;
    return { ...source, name, notes: group.notes.sort((a, b) => a.tick - b.tick), drumPitch: group.pitch,
      ...(combined || !isDrumPitch(group.pitch) ? { drumSlotPitch: group.pitch } : {}) };
  });
}

function measureBoundaries(source: MidiProjectSource): number[] {
  const { ppq, meters, warnings } = source;
  const boundaries = [0];
  let start = 0, beats = DEFAULT_MEASURE_BEATS, change = 0, approximated = false;
  while (start < source.endTick && boundaries.length <= MAX_ARRANGEMENT_MEASURES) {
    // Slot lengths below a sixteenth note cannot be stored. Fold very early meter changes into the new slot.
    while (change < meters.length && meters[change].tick < Math.min(source.endTick, start + MIN_MEASURE_BEATS * ppq)) {
      const mark = meters[change++];
      const supported = mark.value >= MIN_MEASURE_BEATS && mark.value <= 32;
      approximated ||= !supported || mark.tick > start;
      beats = supported ? mark.value : DEFAULT_MEASURE_BEATS;
    }
    const next = meters[change]?.tick ?? Infinity;
    start = Math.min(start + beats * ppq, next < source.endTick ? next : Infinity);
    boundaries.push(start);
  }
  if (approximated) warnings.push('Some time signatures were approximated to fit project measure lengths.');
  if (start < source.endTick) warnings.push(`The song was shortened to ${MAX_ARRANGEMENT_MEASURES} measures.`);
  return boundaries;
}

function markerType(label: string): SectionType {
  const match = /^(intro|verse|chorus|half[ _-]*chorus|breakdown|chill|build[ _-]*up|climax|outro)[ _-]*([123])?\b/i.exec(label);
  switch (match?.[1].toLowerCase().replace(/[ _-]/g, '')) {
    case 'intro': return 'INTRO';
    case 'chorus': return match[2] === '3' ? 'CHORUS3' : match[2] === '2' ? 'CHORUS2' : 'CHORUS1';
    case 'halfchorus': return 'HALF_CHORUS';
    case 'breakdown': return 'BREAKDOWN';
    case 'chill': return 'CHILL';
    case 'buildup': return match[2] === '2' ? 'BUILDUP2' : 'BUILDUP1';
    case 'climax': return 'CLIMAX';
    case 'outro': return 'OUTRO';
    default: return match?.[2] === '3' ? 'VERSE3' : match?.[2] === '2' ? 'VERSE2' : 'VERSE1';
  }
}

export function planMidiArrangement(source: MidiProjectSource) {
  const boundaries = measureBoundaries(source);
  const endTick = boundaries.at(-1)!;
  const usableTracks = source.tracks.filter(track => track.notes[0].tick < endTick);
  const drums = splitDrumTracks(usableTracks, endTick, source.warnings);
  const pitched = usableTracks.filter(track => track.channel !== 10);
  const tracks: ImportedTrack[] = [...pitched.slice(0, MAX_PROJECT_TRACKS - drums.length), ...drums];
  if (pitched.length + drums.length > tracks.length) source.warnings.push(`Some pitched tracks were skipped to fit the ${MAX_PROJECT_TRACKS}-track project limit.`);
  const tempos: MidiPoint<number>[] = [];
  let roundedTempo = false;
  for (const [tick, raw] of new Map(source.tempos.filter(point => point.tick < Math.min(source.endTick, endTick))
    .map(point => [point.tick, point.value]))) {
    const value = Math.max(30, Math.min(300, Math.round(raw)));
    roundedTempo ||= Math.abs(value - raw) > 0.01;
    if (value !== (tempos.at(-1)?.value ?? DEFAULT_TEMPO)) tempos.push({ tick, value });
  }
  if (roundedTempo) source.warnings.push('Tempo values were rounded or limited to the project’s BPM range.');

  const breaks = new Set([0]);
  const mappedTempos = new Map<number, MidiPoint<number>>();
  const labels = new Map<number, SectionType>();
  const requiredSplits = Math.ceil(MAX_ARRANGEMENT_MEASURES / MAX_SECTION_MEASURES) - 1;
  const placeBreak = (tick: number): number | undefined => {
    const next = boundaries.findIndex(beat => beat >= tick);
    if (next < 0 || tick === endTick) return undefined;
    let placed = tick;
    if (boundaries[next] !== tick) {
      const previous = boundaries[next - 1];
      const left = tick - previous, right = boundaries[next] - tick;
      const minimum = MIN_MEASURE_BEATS * source.ppq;
      if (boundaries.length <= MAX_ARRANGEMENT_MEASURES && left >= minimum && right >= minimum
        && breaks.size < MAX_ARRANGEMENT_SECTIONS - requiredSplits) {
        boundaries.splice(next, 0, tick);
      } else {
        placed = left <= right || boundaries[next] === endTick ? previous : boundaries[next];
      }
    }
    if (!breaks.has(placed) && breaks.size >= MAX_ARRANGEMENT_SECTIONS - requiredSplits) return undefined;
    breaks.add(placed);
    return placed;
  };
  let approximatedTempo = false, approximatedMarkers = false;
  // Keep the largest speed changes when a file contains more changes than section slots.
  const tempoChanges = tempos.map((point, index) => ({ point,
    difference: Math.abs(point.value - (tempos[index - 1]?.value ?? DEFAULT_TEMPO)) }));
  tempoChanges.sort((a, b) => b.difference - a.difference);
  for (const { point } of tempoChanges) {
    const placed = placeBreak(point.tick);
    approximatedTempo ||= placed !== point.tick;
    if (placed !== undefined && (mappedTempos.get(placed)?.tick ?? -1) < point.tick) mappedTempos.set(placed, point);
  }
  for (const marker of source.markers) {
    if (marker.tick >= source.endTick || marker.tick >= endTick) break;
    const placed = placeBreak(marker.tick);
    approximatedMarkers ||= placed !== marker.tick;
    if (placed !== undefined) labels.set(placed, markerType(marker.value));
  }
  if (approximatedTempo) source.warnings.push('Some tempo changes were approximated to fit project sections.');
  if (approximatedMarkers) source.warnings.push('Some MIDI markers were moved or skipped to fit project sections.');

  const counts = tracks.map(track => {
    const row = new Uint32Array(boundaries.length - 1);
    let index = 0;
    for (const note of track.notes) {
      if (note.tick >= endTick) break;
      while (note.tick >= boundaries[index + 1]) index++;
      row[index]++;
    }
    return row;
  });
  // Reserve required size splits before choosing additional divisions for dense phrases.
  for (let index = 1, length = 1; index < boundaries.length - 1; index++, length++) {
    if (length === MAX_SECTION_MEASURES) breaks.add(boundaries[index]);
    if (breaks.has(boundaries[index])) length = 0;
  }
  const cumulative = counts.map(() => 0);
  for (let index = 0; index < boundaries.length - 1; index++) {
    if (!breaks.has(boundaries[index]) && breaks.size < MAX_ARRANGEMENT_SECTIONS
      && cumulative.some((count, track) => count + counts[track][index] > MAX_PHRASE_NOTES)) breaks.add(boundaries[index]);
    if (breaks.has(boundaries[index])) cumulative.fill(0);
    counts.forEach((row, track) => { cumulative[track] += row[index]; });
  }
  for (let index = 1, last = 0; index < boundaries.length - 1; index++) {
    if (index - last >= PREFERRED_SECTION_MEASURES && breaks.size < MAX_ARRANGEMENT_SECTIONS) breaks.add(boundaries[index]);
    if (breaks.has(boundaries[index])) last = index;
  }
  const sections: MidiSectionPlan[] = [];
  let start = 0;
  for (let end = 1; end < boundaries.length; end++) {
    if (end !== boundaries.length - 1 && !breaks.has(boundaries[end])) continue;
    const tick = boundaries[start];
    const sourceTempo = tempos[midiPointIndex(tempos, tick)];
    const placed = mappedTempos.get(tick);
    const bpm = (placed && placed.tick > (sourceTempo?.tick ?? -1) ? placed : sourceTempo)?.value ?? DEFAULT_TEMPO;
    sections.push({ boundaries: boundaries.slice(start, end + 1), type: labels.get(tick) ?? 'VERSE1', bpm });
    start = end;
  }
  return { tracks, sections, endTick };
}
