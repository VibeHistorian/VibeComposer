const EPS = 0.01; // Java Constants.DBL_ERR
export interface SwingNote { rhythm: number; duration: number }

/** Port of MidiGenerator.swingPhrase with quarter-note swing units and no global override. */
export function swingNotes<T extends SwingNote>(notes: T[], percent: number, durationMultiplier = 0.95): void {
  if (percent === 50) return;
  const separators: number[] = [];
  let time = 0;
  notes.forEach((note, index) => {
    time += note.rhythm;
    if (time + EPS > 4) { separators.push(index); time = 0; }
  });
  if (time > EPS) separators.push(notes.length - 1);
  let separator = 0, adjustment = percent / 50 - 1;
  let swung: T | undefined, suitable: T | undefined;
  time = 0;
  const multiple = (unit: number) => Math.abs(time - Math.round(time / unit) * unit) < EPS;
  const adjust = (note: T, minimum = true) => {
    note.rhythm += adjustment;
    note.duration = (minimum ? Math.max(0.125, note.rhythm) : note.rhythm) * durationMultiplier;
  };
  notes.forEach((note, index) => {
    const duration = note.rhythm;
    if (duration < EPS) return;
    if (index > separators[separator]) {
      separator++; adjustment = percent / 50 - 1; time = 0;
      if (swung) { adjustment *= -1; adjust(swung, false); adjustment *= -1; swung = suitable = undefined; }
    }
    time += duration;
    let processed = false;
    if (!swung) { if (duration - Math.abs(adjustment) > EPS) suitable = note; processed = true; }
    else if (duration - Math.abs(adjustment) > EPS && !suitable) { suitable = note; processed = true; }
    if (multiple(1)) {
      if (!swung && multiple(2)) suitable = undefined;
      else if (suitable) {
        adjust(suitable); adjustment *= -1;
        swung = swung ? undefined : suitable; suitable = undefined;
      } else if (swung) { adjust(swung); adjustment *= -1; swung = suitable = undefined; }
    }
    if (!processed && !multiple(2) && swung && duration - Math.abs(adjustment) > EPS && !suitable) suitable = note;
  });
  if (swung) adjust(swung);
}
