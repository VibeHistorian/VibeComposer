/** Canvas coordinates are CSS pixels; offsets are fractions of the full song. */
export interface ScoreTimeGeometry {
  readonly width: number;
  readonly horizontalScale: number;
  readonly horizontalOffset: number;
  readonly totalBeats: number;
  readonly startBeat?: number;
}

export interface ScorePitchGeometry {
  readonly height: number;
  readonly velocityVisible: boolean;
  readonly harmonicHeight: number;
  readonly drumHeight: number;
  readonly pitchMin: number;
  readonly pitchMax: number;
  readonly verticalScale: number;
  readonly verticalOffset: number;
}

export interface ScorePoint { readonly x: number; readonly y: number }
export interface ScoreRect extends ScorePoint { readonly width: number; readonly height: number }
export interface CanvasBounds { readonly left: number; readonly top: number; readonly width: number; readonly height: number }

export const VELOCITY_HEIGHT = 34;
const MAX_PITCH_ROW_HEIGHT = 30;

export function canvasPoint(clientX: number, clientY: number, bounds: CanvasBounds, size: { width: number; height: number }): ScorePoint {
  return { x: (clientX - bounds.left) * size.width / Math.max(1, bounds.width),
    y: (clientY - bounds.top) * size.height / Math.max(1, bounds.height) };
}

export function scoreX(beat: number, geometry: ScoreTimeGeometry): number {
  return ((beat - (geometry.startBeat ?? 0)) / Math.max(1, geometry.totalBeats) - geometry.horizontalOffset) * geometry.width * geometry.horizontalScale;
}

export function scoreBeat(x: number, geometry: ScoreTimeGeometry): number {
  return (geometry.horizontalOffset + x / geometry.width / geometry.horizontalScale) * geometry.totalBeats + (geometry.startBeat ?? 0);
}

export function scoreHeight(geometry: Pick<ScorePitchGeometry, 'height' | 'velocityVisible'>): number {
  return Math.max(1, geometry.height - (geometry.velocityVisible ? VELOCITY_HEIGHT : 0));
}

export function laneHeight(geometry: ScorePitchGeometry): number {
  return Math.min(MAX_PITCH_ROW_HEIGHT, Math.max(1, geometry.harmonicHeight) * geometry.verticalScale / (geometry.pitchMax - geometry.pitchMin + 1));
}

export function maxVerticalScale(geometry: ScorePitchGeometry, editable: boolean): number {
  return Math.min(editable ? 128 : 8,
    Math.max(1, MAX_PITCH_ROW_HEIGHT * (geometry.pitchMax - geometry.pitchMin + 1) / Math.max(1, geometry.harmonicHeight)));
}

export function contentHeight(geometry: ScorePitchGeometry): number {
  return laneHeight(geometry) * (geometry.pitchMax - geometry.pitchMin + 1) + geometry.drumHeight;
}

export function clampViewport(time: ScoreTimeGeometry, pitch: ScorePitchGeometry): { horizontalOffset: number; verticalOffset: number } {
  return {
    horizontalOffset: Math.min(1 - 1 / time.horizontalScale, Math.max(0, time.horizontalOffset)),
    verticalOffset: Math.min(Math.max(0, contentHeight(pitch) - scoreHeight(pitch)), Math.max(0, pitch.verticalOffset)),
  };
}

export function sectionViewport(startBeat: number, endBeat: number, totalBeats: number): { horizontalScale: number; horizontalOffset: number } {
  const length = Math.max(0.001, endBeat - startBeat);
  const horizontalScale = Math.max(1, totalBeats / length);
  return { horizontalScale, horizontalOffset: (startBeat + length / 2) / totalBeats - 0.5 / horizontalScale };
}

export function pitchAt(y: number, geometry: ScorePitchGeometry, drumPitches: readonly number[]): number {
  return y >= geometry.harmonicHeight && drumPitches.length
    ? drumPitches[Math.min(drumPitches.length - 1, Math.floor((y - geometry.harmonicHeight) / geometry.drumHeight * drumPitches.length))]
    : geometry.pitchMax - Math.floor((y + geometry.verticalOffset) / laneHeight(geometry));
}
