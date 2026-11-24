import { glideNeutral, glideSpans, glideValue, type GlideLane, type GlideSegment, type PhraseGlides } from '../music/phrase-glides';
import { beatToSeconds, type TempoPoint } from '../music/section-context';

export interface ChannelGlideAudio {
  readonly pitch?: ConstantSourceNode;
  readonly volume?: GainNode;
}

export function createChannelGlideAudio(context: BaseAudioContext, destination: AudioNode,
  glides: ReadonlyMap<number, PhraseGlides>): Map<number, ChannelGlideAudio> {
  const channels = new Map<number, ChannelGlideAudio>();
  for (const [channel, curves] of glides) {
    const pitch = curves.pitch.length ? context.createConstantSource() : undefined;
    if (pitch) { pitch.offset.value = 0; pitch.start(); }
    const volume = curves.volume.length ? context.createGain() : undefined;
    volume?.connect(destination);
    channels.set(channel, { pitch, volume });
  }
  return channels;
}

export function disposeChannelGlideAudio(channels: ReadonlyMap<number, ChannelGlideAudio>): void {
  for (const { pitch, volume } of channels.values()) {
    pitch?.stop();
    pitch?.disconnect();
    volume?.disconnect();
  }
}

/** Value is linear in beats, so split AudioParam ramps wherever tempo changes. */
export function scheduleGlideParam(param: AudioParam, lane: GlideLane, segments: readonly GlideSegment[],
  start: number, end: number, tempo: readonly TempoPoint[], offset: number): void {
  const scale = lane === 'pitch' ? 100 : 1;
  const timeAt = (beat: number) => Math.max(0, beatToSeconds(tempo, beat) + offset);
  for (const span of glideSpans(segments, start, end, glideNeutral(lane))) {
    param.setValueAtTime(span.startValue * scale, timeAt(span.startBeat));
    if (span.startValue === span.endValue) continue;
    for (const point of tempo) {
      if (point.beat <= span.startBeat) continue;
      if (point.beat >= span.endBeat) break;
      param.linearRampToValueAtTime(glideValue(span, point.beat) * scale, timeAt(point.beat));
    }
    param.linearRampToValueAtTime(span.endValue * scale, timeAt(span.endBeat));
  }
}
