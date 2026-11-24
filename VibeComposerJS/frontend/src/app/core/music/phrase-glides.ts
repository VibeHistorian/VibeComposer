export const GLIDE_LANES = ['pitch', 'volume'] as const;
export type GlideLane = typeof GLIDE_LANES[number];
export const PITCH_GLIDE_SEMITONES = 2;

export interface GlideSegment {
  readonly startBeat: number;
  readonly endBeat: number;
  readonly startValue: number;
  readonly endValue: number;
}

export type PhraseGlides = Readonly<Record<GlideLane, readonly GlideSegment[]>>;
export const EMPTY_PHRASE_GLIDES: PhraseGlides = { pitch: [], volume: [] };

/** Independent controller replacement in section-relative beats, including optional pickup and tail. */
export interface PhraseGlideEdit {
  readonly lane: GlideLane;
  readonly startBeat: number;
  readonly endBeat: number;
  readonly pickupBeats?: number;
  readonly tailBeats?: number;
  readonly segments: readonly GlideSegment[];
}

export function glideNeutral(lane: GlideLane): number { return lane === 'pitch' ? 0 : 1; }

export function glideValue(segment: GlideSegment, beat: number): number {
  if (beat <= segment.startBeat) return segment.startValue;
  if (beat >= segment.endBeat) return segment.endValue;
  return segment.startValue + (segment.endValue - segment.startValue) * (beat - segment.startBeat) / (segment.endBeat - segment.startBeat);
}

export function shiftGlideSegments(segments: readonly GlideSegment[], offset: number): readonly GlideSegment[] {
  return offset === 0 ? segments : segments.map(segment => ({ ...segment,
    startBeat: segment.startBeat + offset, endBeat: segment.endBeat + offset,
  }));
}

/** All lane segments are sorted and disjoint. Find the first segment that can overlap the window. */
export function clipGlideSegments(segments: readonly GlideSegment[], start: number, end: number): GlideSegment[] {
  let low = 0, high = segments.length;
  while (low < high) {
    const middle = (low + high) >>> 1;
    if (segments[middle].endBeat <= start) low = middle + 1; else high = middle;
  }
  const clipped: GlideSegment[] = [];
  for (let index = low; index < segments.length && segments[index].startBeat < end; index++) {
    const segment = segments[index];
    const startBeat = Math.max(start, segment.startBeat), endBeat = Math.min(end, segment.endBeat);
    if (endBeat <= startBeat) continue;
    clipped.push(startBeat === segment.startBeat && endBeat === segment.endBeat ? segment : {
      startBeat, endBeat, startValue: glideValue(segment, startBeat), endValue: glideValue(segment, endBeat),
    });
  }
  return clipped;
}

export function replaceGlideSegments(segments: readonly GlideSegment[], start: number, end: number,
  replacement: readonly GlideSegment[]): readonly GlideSegment[] {
  if (end <= start) return segments;
  return [...clipGlideSegments(segments, -Infinity, start), ...replacement, ...clipGlideSegments(segments, end, Infinity)];
}

/** New material takes precedence where curves overlap, without masking gaps in another phrase. */
export function overlayGlideSegments(base: readonly GlideSegment[], added: readonly GlideSegment[]): readonly GlideSegment[] {
  if (!base.length) return added;
  if (!added.length) return base;
  if (base.at(-1)!.endBeat <= added[0].startBeat) return [...base, ...added];
  const result: GlideSegment[] = [];
  let edge = -Infinity;
  for (const segment of added) {
    for (const earlier of clipGlideSegments(base, edge, segment.startBeat)) result.push(earlier);
    result.push(segment);
    edge = segment.endBeat;
  }
  return result.concat(clipGlideSegments(base, edge, Infinity));
}

export function applyPhraseGlideEdits(global: readonly PhraseGlideEdit[] | undefined,
  local: readonly PhraseGlideEdit[] | undefined, sectionEnd: number,
  bounds: { readonly startBeat: number; readonly endBeat: number }): PhraseGlides {
  if (!global?.length && !local?.length) return EMPTY_PHRASE_GLIDES;
  const result: Record<GlideLane, readonly GlideSegment[]> = { pitch: [], volume: [] };
  for (const edit of [...global ?? [], ...local ?? []]) {
    let segments = result[edit.lane];
    const replace = (start: number, end: number, offset = 0) => {
      if (end <= start) return;
      const replacement = clipGlideSegments(edit.segments, start - offset, end - offset)
        .filter(segment => segment.startValue !== glideNeutral(edit.lane) || segment.endValue !== glideNeutral(edit.lane));
      // Clip after translation as well so adjacent windows meet at the same floating-point boundary.
      segments = replaceGlideSegments(segments, start, end,
        clipGlideSegments(shiftGlideSegments(replacement, offset), start, end));
    };
    if (edit.pickupBeats) replace(Math.max(bounds.startBeat, -edit.pickupBeats), 0);
    replace(edit.startBeat, Math.min(sectionEnd, edit.endBeat));
    if (edit.tailBeats) replace(sectionEnd, Math.min(bounds.endBeat, sectionEnd + edit.tailBeats), sectionEnd - edit.endBeat);
    result[edit.lane] = segments;
  }
  return result;
}

export function appendPhraseGlideEdit(previous: readonly PhraseGlideEdit[] | undefined, edit: PhraseGlideEdit): PhraseGlideEdit[] {
  return [...(previous ?? []).filter(old => !(edit.lane === old.lane && edit.startBeat <= old.startBeat && edit.endBeat >= old.endBeat
    && (edit.pickupBeats ?? 0) >= (old.pickupBeats ?? 0) && (edit.tailBeats ?? 0) >= (old.tailBeats ?? 0))),
    { ...edit, segments: edit.segments.map(segment => ({ ...segment })) }];
}

export function glidesEqual(left: readonly GlideSegment[], right: readonly GlideSegment[]): boolean {
  return left === right || left.length === right.length && left.every((segment, index) => {
    const other = right[index];
    return segment.startBeat === other.startBeat && segment.endBeat === other.endBeat
      && segment.startValue === other.startValue && segment.endValue === other.endValue;
  });
}

/** Include neutral gaps so playback and export reset the controller as each glide ends. */
export function glideSpans(segments: readonly GlideSegment[], start: number, end: number, neutral: number): GlideSegment[] {
  const spans: GlideSegment[] = [];
  let edge = start;
  const gap = (endBeat: number) => {
    if (edge < endBeat) spans.push({ startBeat: edge, endBeat, startValue: neutral, endValue: neutral });
  };
  for (const segment of clipGlideSegments(segments, start, end)) {
    gap(segment.startBeat);
    spans.push(segment);
    edge = segment.endBeat;
  }
  gap(end);
  return spans;
}

export type TrackGlides = Readonly<Record<string, PhraseGlides>>;

export interface GlideReplacement {
  readonly startBeat: number;
  readonly endBeat: number;
  readonly glides?: readonly { readonly trackId: string; readonly lane: GlideLane; readonly segments: readonly GlideSegment[] }[];
}

export function withGlidePreview(committed: TrackGlides, preview: GlideReplacement | null): TrackGlides {
  if (!preview?.glides?.length) return committed;
  const byTrack = { ...committed };
  for (const { trackId, lane, segments } of preview.glides) {
    const current = Object.hasOwn(byTrack, trackId) ? byTrack[trackId] : EMPTY_PHRASE_GLIDES;
    byTrack[trackId] = { ...current, [lane]: replaceGlideSegments(current[lane], preview.startBeat, preview.endBeat, segments) };
  }
  return byTrack;
}

/** Tracks retain ownership. On a shared MIDI channel the last overlapping track in project order supplies the value. */
export function glidesByChannel(tracks: readonly { id: string; midiChannel: number }[],
  byTrack: TrackGlides): ReadonlyMap<number, PhraseGlides> {
  const channels = new Map<number, PhraseGlides>();
  for (const track of tracks) {
    if (!Object.hasOwn(byTrack, track.id)) continue;
    const glides = byTrack[track.id];
    if (!glides.pitch.length && !glides.volume.length) continue;
    const prior = channels.get(track.midiChannel) ?? EMPTY_PHRASE_GLIDES;
    channels.set(track.midiChannel, {
      pitch: overlayGlideSegments(prior.pitch, glides.pitch),
      volume: overlayGlideSegments(prior.volume, glides.volume),
    });
  }
  return channels;
}
