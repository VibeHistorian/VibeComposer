const LOG_K = 15;
const LOG_SCALE = Math.log1p(LOG_K);
// CSS pixels: at the renderer's maximum 2x resolution this is a quarter device pixel.
export const LOG_NOTE_MAX_ERROR = 0.125;
const contours = new Map<number, readonly number[]>();

function envelope(t: number): number {
  return 1 - Math.log1p(LOG_K * t) / LOG_SCALE;
}

/** Normalized contour, cached by height bucket and envelope family, independent of note length.
 * Subdivide at the exact maximum log/chord error rather than feeding many cubic curves
 * through Pixi's subdivision, fill triangulation and stroke tessellation on every redraw. */
export function logNoteContour(height: number, plucky: boolean): readonly number[] {
  const bucket = 2 ** Math.ceil(Math.log2(Math.max(1, height)));
  const key = plucky ? bucket : -bucket;
  const cached = contours.get(key);
  if (cached) return cached;
  const samples: number[] = [0, 1];
  const subdivide = (a: number, fa: number, b: number, fb: number): void => {
    const slope = (fb - fa) / (b - a);
    // The extremum of the interpolation error is where f'(t) equals the chord slope.
    const t = Math.max(a, Math.min(b, (-LOG_K / (LOG_SCALE * slope) - 1) / LOG_K));
    const ft = envelope(t);
    const error = Math.abs(ft - (fa + slope * (t - a))) * bucket / 2;
    if (error > LOG_NOTE_MAX_ERROR) {
      subdivide(a, fa, t, ft);
      subdivide(t, ft, b, fb);
    } else samples.push(b, fb);
  };
  subdivide(0, 1, 1, 0);
  const top: number[] = [];
  if (plucky) {
    for (let i = 0; i < samples.length; i += 2) top.push(samples[i], (1 - samples[i + 1]) / 2);
  } else {
    for (let i = samples.length - 2; i >= 0; i -= 2) top.push(1 - samples[i], samples[i + 1] / 2);
  }
  const polygon = [...top];
  // The two sides share the pointed tip; close at the vertical left edge.
  for (let i = top.length - 4; i >= 0; i -= 2) polygon.push(top[i], 1 - top[i + 1]);
  Object.freeze(polygon);
  contours.set(key, polygon);
  return polygon;
}

export function logNotePolygon(x: number, y: number, width: number, height: number, plucky: boolean): number[] {
  const contour = logNoteContour(height, plucky);
  const polygon = new Array<number>(contour.length);
  for (let i = 0; i < contour.length; i += 2) {
    polygon[i] = x + contour[i] * width;
    polygon[i + 1] = y + contour[i + 1] * height;
  }
  return polygon;
}
