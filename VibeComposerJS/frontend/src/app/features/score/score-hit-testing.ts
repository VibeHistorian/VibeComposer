import type { ScorePoint, ScoreRect } from './score-geometry';

export interface NoteHitArea<Note = { readonly id: string }> extends ScoreRect {
  readonly note: Note;
  readonly trackIndex: number;
  readonly visualBounds?: ScoreRect;
}

export const HIT_BUCKET_WIDTH = 96;
export type NoteDragZone = 'start' | 'move' | 'duration';

export interface ScoreHitGeometry<Note> {
  readonly width: number;
  readonly height: number;
  readonly scoreHeight: number;
  readonly harmonicHeight: number;
  readonly drumHeight: number;
  readonly trackCount: number;
  readonly editable: boolean;
  readonly velocityVisible: boolean;
  readonly selectedIds: readonly string[];
  readonly areas: readonly NoteHitArea<Note>[];
  readonly columnCount: number;
  readonly buckets: ReadonlyMap<number, readonly NoteHitArea<Note>[]>;
}

/** Selection wins overlaps in the editor; otherwise the last painted note wins. */
export function hitAreaAt<Note extends { readonly id: string }>(point: ScorePoint, geometry: ScoreHitGeometry<Note>): NoteHitArea<Note> | undefined {
  const { x, y } = point;
  const { width, height, scoreHeight, harmonicHeight, drumHeight, trackCount, editable, velocityVisible,
    areas, columnCount, buckets } = geometry;
  if (trackCount === 0 || y < 0 || x < 0 || x >= width) return undefined;
  const selectedIds = editable ? geometry.selectedIds : [];
  if (y >= scoreHeight) {
    if (editable && velocityVisible && y < height) {
      const matches = (area: NoteHitArea<Note>): boolean => Math.abs(x - area.x) <= 5;
      return areas.find(area => selectedIds.includes(area.note.id) && matches(area)) ?? areas.find(matches);
    }
    return undefined;
  }
  const trackIndex = y >= harmonicHeight && drumHeight > 0 ? 1 : 0;
  const column = Math.min(columnCount - 1, Math.floor(x / HIT_BUCKET_WIDTH));
  const candidates = buckets.get(trackIndex * columnCount + column) ?? [];
  let fallback: NoteHitArea<Note> | undefined;
  for (let index = candidates.length - 1; index >= 0; index--) {
    const area = candidates[index];
    if (x >= area.x && x <= area.x + area.width && y >= area.y && y <= area.y + area.height) {
      if (selectedIds.includes(area.note.id)) return area;
      fallback ??= area;
    }
  }
  return fallback;
}

export function addHitArea<Note>(area: NoteHitArea<Note>, columnCount: number, buckets: Map<number, NoteHitArea<Note>[]>): void {
  const firstColumn = Math.floor(area.x / HIT_BUCKET_WIDTH);
  const lastColumn = Math.min(columnCount - 1, Math.floor((area.x + area.width) / HIT_BUCKET_WIDTH));
  for (let column = firstColumn; column <= lastColumn; column++) {
    const bucketId = area.trackIndex * columnCount + column;
    const bucket = buckets.get(bucketId);
    if (bucket) bucket.push(area);
    else buckets.set(bucketId, [area]);
  }
}

export function noteDragZone(area: ScoreRect, x: number, altKey = false): NoteDragZone {
  if (altKey || x <= area.x + Math.min(5, area.width / 3)) return 'start';
  return x >= area.x + area.width - Math.min(7, area.width / 3) ? 'duration' : 'move';
}

export function boxNoteIds<Note extends { readonly id: string }>(areas: readonly NoteHitArea<Note>[], start: ScorePoint, end: ScorePoint): string[] {
  return areas.filter(area => area.x + area.width >= Math.min(start.x, end.x) && area.x <= Math.max(start.x, end.x)
    && area.y + area.height >= Math.min(start.y, end.y) && area.y <= Math.max(start.y, end.y)).map(area => area.note.id);
}
