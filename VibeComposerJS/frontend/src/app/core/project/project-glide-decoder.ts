import { PITCH_GLIDE_SEMITONES, type GlideSegment, type PhraseGlideEdit } from '../music/phrase-glides';

const MAX_GLIDE_SEGMENTS = 4096;

export function decodePhraseGlideEdits(value: unknown): PhraseGlideEdit[] | undefined {
  if (!Array.isArray(value) || value.length > MAX_GLIDE_SEGMENTS) return undefined;
  let count = 0;
  const edits: PhraseGlideEdit[] = [];
  for (const raw of value) {
    if (!raw || typeof raw !== 'object') return undefined;
    const { lane, startBeat, endBeat, pickupBeats, tailBeats, segments } = raw as PhraseGlideEdit;
    if ((lane !== 'pitch' && lane !== 'volume') || !Number.isFinite(startBeat) || !Number.isFinite(endBeat)
      || startBeat < 0 || endBeat <= startBeat || endBeat > 2048
      || pickupBeats !== undefined && (startBeat !== 0 || !validMargin(pickupBeats))
      || tailBeats !== undefined && !validMargin(tailBeats)
      || !Array.isArray(segments)) return undefined;
    count += segments.length;
    if (count > MAX_GLIDE_SEGMENTS) return undefined;
    const min = lane === 'pitch' ? -PITCH_GLIDE_SEMITONES : 0, max = lane === 'pitch' ? PITCH_GLIDE_SEMITONES : 1;
    let edge = startBeat - (pickupBeats ?? 0);
    const decoded: GlideSegment[] = [];
    for (const segment of segments) {
      if (!segment || typeof segment !== 'object' || !Number.isFinite(segment.startBeat) || !Number.isFinite(segment.endBeat)
        || segment.startBeat < edge || segment.endBeat <= segment.startBeat || segment.endBeat > endBeat + (tailBeats ?? 0)
        || ![segment.startValue, segment.endValue].every(value => Number.isFinite(value) && value >= min && value <= max)) return undefined;
      edge = segment.endBeat;
      decoded.push({ startBeat: segment.startBeat, endBeat: edge, startValue: segment.startValue, endValue: segment.endValue });
    }
    edits.push({ lane, startBeat, endBeat, ...(pickupBeats !== undefined ? { pickupBeats } : {}),
      ...(tailBeats !== undefined ? { tailBeats } : {}), segments: decoded });
  }
  return edits;
}

function validMargin(value: number): boolean { return Number.isFinite(value) && value > 0 && value <= 64; }
