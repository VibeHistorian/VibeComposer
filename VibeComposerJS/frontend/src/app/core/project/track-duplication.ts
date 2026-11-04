/** Track-owned events to copy when appending a duplicate. */
export interface TrackDuplicationOptions {
  readonly manualNotes: boolean;
  readonly presences: boolean;
  readonly overrides: boolean;
  readonly variations: boolean;
}
