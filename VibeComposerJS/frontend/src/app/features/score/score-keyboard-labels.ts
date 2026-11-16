interface PitchRow { readonly midi: number; readonly y: number; readonly height: number; readonly drum: boolean; }
/** Stagger consecutive labels in dense views, preserving stable columns while scrolling. */
export function keyboardLabelLayout(rows: readonly PitchRow[], labels: ReadonlyMap<number, string>) {
  const visible = rows.filter(row => !row.drum && labels.has(row.midi));
  const dense = visible.some((row, index) => index > 0 && row.y - visible[index - 1].y < 10);
  const gaps = dense ? visible.slice(2).map((row, index) => row.y - visible[index].y - 1) : [];
  const fontSize = Math.max(1, Math.min(9, ...gaps));
  const columns = new Map([...labels.keys()].sort((a, b) => b - a).map((midi, index) => [midi, index % 2]));
  return visible.map(row => ({ midi: row.midi, label: labels.get(row.midi)!,
    y: row.y, column: dense ? columns.get(row.midi)! : 0, fontSize }));
}
