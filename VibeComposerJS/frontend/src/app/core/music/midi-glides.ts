import { glideNeutral, glideSpans, glideValue, PITCH_GLIDE_SEMITONES, type GlideLane, type GlideSegment } from './phrase-glides';

export const MIDI_EXPRESSION_CC = 11;
export const PITCH_BEND_SENSITIVITY = [
  [101, 0], [100, 0], [6, PITCH_GLIDE_SEMITONES], [38, 0], [101, 127], [100, 127],
] as const;
const MIDI_GLIDE_INTERVAL_SECONDS = 0.02;

/** Pitch bend is signed around its 14-bit midpoint. Expression is a 7-bit CC. */
export function midiGlideValue(lane: GlideLane, value: number): number {
  return lane === 'pitch'
    ? Math.max(-8192, Math.min(8191, Math.round(8192 * value / PITCH_GLIDE_SEMITONES)))
    : Math.max(0, Math.min(127, Math.round(value * 127)));
}

export function midiGlideEvents(lane: GlideLane, segments: readonly GlideSegment[], start: number, end: number,
  secondsAt: (beat: number) => number, beatAt: (seconds: number) => number,
  emit: (seconds: number, value: number) => void): void {
  let last: number | undefined;
  const send = (time: number, raw: number) => {
    const value = midiGlideValue(lane, raw);
    if (value !== last) { last = value; emit(time, value); }
  };
  for (const span of glideSpans(segments, start, end, glideNeutral(lane))) {
    const time = secondsAt(span.startBeat);
    send(time, span.startValue);
    if (span.startValue === span.endValue) continue;
    const stop = secondsAt(span.endBeat);
    const step = Math.min(MIDI_GLIDE_INTERVAL_SECONDS, (stop - time) / 2);
    for (let next = time + step; next > time && next < stop; next += step) {
      send(next, glideValue(span, beatAt(next)));
    }
  }
}
