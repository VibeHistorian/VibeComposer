import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import type { ArrangedPart, CompositionTrack, PhraseNote } from '../../core/project/project.model';
import { phraseForTrack } from '../../core/music/phrase';
import { ProjectService } from '../../core/project/project.service';
import { WorkspaceUiService } from '../../shared/workspace-ui.service';

const ROW_HEIGHT = 12;
const BEAT_SNAP = 0.25;

interface NoteDrag {
  readonly id: string;
  readonly trackId: string;
  readonly pointerId: number;
  readonly mode: 'move' | 'resize';
  readonly originX: number;
  readonly originY: number;
  readonly laneWidth: number;
  readonly initialStartBeat: number;
  readonly initialDurationBeats: number;
  readonly initialMidi: number;
  readonly startBeat: number;
  readonly durationBeats: number;
  readonly midi: number;
}

@Component({
  selector: 'vc-edit-workspace',
  imports: [TitleCasePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './edit-workspace.component.html',
  styleUrl: './edit-workspace.component.css',
})
export class EditWorkspaceComponent {
  readonly projects = inject(ProjectService);
  readonly workspaceUi = inject(WorkspaceUiService);
  readonly project = this.projects.project;
  readonly parts: ReadonlyArray<{ key: ArrangedPart; label: string; color: string }> = [
    { key: 'bass', label: 'Bass', color: 'bass' },
    { key: 'chords', label: 'Chords', color: 'chords' },
    { key: 'arpeggio', label: 'Arpeggio', color: 'arpeggio' },
    { key: 'drums', label: 'Drums', color: 'drums' },
  ];
  readonly drumVoices = [
    { midi: 36, label: 'Kick' },
    { midi: 38, label: 'Snare' },
    { midi: 42, label: 'Closed hat' },
  ] as const;
  readonly drumSteps = Array.from({ length: 16 }, (_, index) => index);
  readonly selectedTrack = computed<CompositionTrack>(() => this.project().tracks.find((track) => track.id === this.workspaceUi.selectedTrackId())
    ?? this.project().tracks[0]);
  readonly selectedPart = computed<ArrangedPart>(() => this.selectedTrack().role);
  readonly selectedNoteId = signal<string | null>(null);
  readonly noteDrag = signal<NoteDrag | null>(null);
  readonly drumBar = signal(0);
  readonly phraseBeats = computed(() => this.project().progression.length * 4);
  readonly canvasWidth = computed(() => Math.max(690, this.project().progression.length * 96 + 48));
  readonly phrase = computed(() => phraseForTrack(this.project(), this.selectedTrack()));
  readonly notes = computed(() => [...this.phrase()].sort((left, right) => left.startBeat - right.startBeat || left.midi - right.midi));
  readonly displayNotes = computed(() => {
    const notes = this.notes();
    const drag = this.noteDrag();
    return drag ? notes.map((note) => note.id === drag.id
      ? { ...note, startBeat: drag.startBeat, durationBeats: drag.durationBeats, midi: drag.midi }
      : note) : notes;
  });
  readonly selectedNote = computed(() => this.displayNotes().find((note) => note.id === this.selectedNoteId()) ?? null);
  readonly isEdited = computed(() => this.selectedTrack().editedPhrase !== undefined);
  readonly drumBars = computed(() => Array.from({ length: this.project().progression.length }, (_, index) => index));
  readonly drumNotes = computed(() => this.notes().filter((note) => Math.floor(note.startBeat / 4) === this.drumBar()));
  readonly pitchRange = computed(() => {
    const notes = this.notes();
    const lowest = notes.length ? Math.min(...notes.map((note) => note.midi)) : 48;
    const highest = notes.length ? Math.max(...notes.map((note) => note.midi)) : 72;
    const min = Math.max(0, Math.floor((lowest - 2) / 12) * 12);
    const max = Math.min(127, Math.ceil((highest + 2) / 12) * 12);
    return { min, max: Math.max(min + 12, max) };
  });
  readonly pitches = computed(() => {
    const { min, max } = this.pitchRange();
    return Array.from({ length: max - min + 1 }, (_, index) => max - index);
  });
  readonly laneHeight = computed(() => this.pitches().length * ROW_HEIGHT);
  readonly noteCount = computed(() => this.notes().length);
  private newNoteId = 0;

  selectTrack(trackId: string): void {
    this.workspaceUi.selectTrack(trackId);
    this.selectedNoteId.set(null);
    this.noteDrag.set(null);
    this.drumBar.set(0);
  }

  selectNote(id: string): void {
    this.selectedNoteId.set(id);
  }

  noteLeft(note: PhraseNote): number {
    return note.startBeat / this.phraseBeats() * 100;
  }

  noteWidth(note: PhraseNote): number {
    return note.durationBeats / this.phraseBeats() * 100;
  }

  noteTop(note: PhraseNote): number {
    return (this.pitchRange().max - note.midi) * ROW_HEIGHT;
  }

  beginNotePointer(event: PointerEvent, note: PhraseNote, mode: 'move' | 'resize', lane: HTMLDivElement): void {
    if (event.button !== 0 || lane.clientWidth <= 0) return;
    this.selectNote(note.id);
    lane.setPointerCapture(event.pointerId);
    this.noteDrag.set({
      id: note.id,
      trackId: this.selectedTrack().id,
      pointerId: event.pointerId,
      mode,
      originX: event.clientX,
      originY: event.clientY,
      laneWidth: lane.clientWidth,
      initialStartBeat: note.startBeat,
      initialDurationBeats: note.durationBeats,
      initialMidi: note.midi,
      startBeat: note.startBeat,
      durationBeats: note.durationBeats,
      midi: note.midi,
    });
  }

  moveNotePointer(event: PointerEvent): void {
    const drag = this.noteDrag();
    if (!drag || drag.pointerId !== event.pointerId) return;
    const beatDelta = this.snapBeat((event.clientX - drag.originX) / drag.laneWidth * this.phraseBeats());
    const startBeat = drag.mode === 'move'
      ? this.clamp(drag.initialStartBeat + beatDelta, 0, this.phraseBeats() - drag.initialDurationBeats)
      : drag.initialStartBeat;
    const maxDuration = this.phraseBeats() - startBeat;
    const durationBeats = drag.mode === 'resize'
      ? this.clamp(this.snapBeat(drag.initialDurationBeats + beatDelta), Math.min(BEAT_SNAP, maxDuration), maxDuration)
      : drag.initialDurationBeats;
    const pitchRange = this.pitchRange();
    const midi = drag.mode === 'move'
      ? this.clamp(drag.initialMidi - Math.round((event.clientY - drag.originY) / ROW_HEIGHT), pitchRange.min, pitchRange.max)
      : drag.initialMidi;
    this.noteDrag.set({ ...drag, startBeat, durationBeats, midi });
  }

  finishNotePointer(event: PointerEvent): void {
    const drag = this.noteDrag();
    if (!drag || drag.pointerId !== event.pointerId) return;
    const changed = drag.startBeat !== drag.initialStartBeat
      || drag.durationBeats !== drag.initialDurationBeats || drag.midi !== drag.initialMidi;
    if (changed) {
      const notes = this.notes().map((note) => note.id === drag.id
        ? { ...note, startBeat: drag.startBeat, durationBeats: drag.durationBeats, midi: drag.midi }
        : note);
      this.projects.updateTrackPhrase(drag.trackId, notes);
    }
    this.noteDrag.set(null);
  }

  cancelNotePointer(event: PointerEvent): void {
    if (this.noteDrag()?.pointerId === event.pointerId) this.noteDrag.set(null);
  }

  noteName(midi: number): string {
    const names = ['C', 'C♯', 'D', 'D♯', 'E', 'F', 'F♯', 'G', 'G♯', 'A', 'A♯', 'B'];
    return `${names[midi % 12]}${Math.floor(midi / 12) - 1}`;
  }

  isBlackKey(midi: number): boolean {
    return [1, 3, 6, 8, 10].includes(midi % 12);
  }

  hasDrumHit(midi: number, step: number): boolean {
    const expectedBeat = this.drumBar() * 4 + step * 0.25;
    return this.drumNotes().some((note) => note.midi === midi && Math.abs(note.startBeat - expectedBeat) < 0.12);
  }

  toggleDrumHit(midi: number, step: number): void {
    const beat = this.drumBar() * 4 + step * 0.25;
    const notes = this.notes();
    const existing = notes.find((note) => note.midi === midi && Math.abs(note.startBeat - beat) < 0.12);
    const next = existing ? notes.filter((note) => note.id !== existing.id) : [
      ...notes,
      { id: this.makeNoteId(), midi, startBeat: beat, durationBeats: 0.2, velocity: midi === 42 ? 72 : 90 },
    ];
    this.projects.updateTrackPhrase(this.selectedTrack().id, next);
  }

  addNote(): void {
    if (this.selectedPart() === 'drums' || this.notes().length >= 2048) {
      return;
    }
    const preferredPitch = this.selectedNote()?.midi ?? (this.selectedPart() === 'bass' ? 48 : 60);
    const pitch = Math.max(0, Math.min(127, preferredPitch));
    const durationBeats = 0.5;
    const occupied = new Set(this.notes().filter((note) => note.midi === pitch)
      .map((note) => Math.round(note.startBeat * 4)));
    let slot = 0;
    while (slot < this.phraseBeats() * 4 && occupied.has(slot)) slot++;
    const startBeat = Math.min(slot / 4, Math.max(0, this.phraseBeats() - durationBeats));
    const note: PhraseNote = {
      id: this.makeNoteId(), midi: pitch, startBeat, durationBeats, velocity: 90,
    };
    this.projects.updateTrackPhrase(this.selectedTrack().id, [...this.notes(), note]);
    this.selectedNoteId.set(note.id);
  }

  updateSelected(field: 'midi' | 'startBeat' | 'durationBeats' | 'velocity', event: Event): void {
    const selected = this.selectedNote();
    const inputValue = Number((event.target as HTMLInputElement).value);
    const value = field === 'startBeat' ? inputValue - 1 : inputValue;
    if (!selected || !Number.isFinite(value)) return;

    const valid = field === 'midi' ? Number.isInteger(value) && value >= 0 && value <= 127
      : field === 'velocity' ? Number.isInteger(value) && value >= 1 && value <= 127
        : field === 'startBeat' ? value >= 0 && value + selected.durationBeats <= this.phraseBeats()
          : value > 0 && selected.startBeat + value <= this.phraseBeats();
    if (!valid) {
      (event.target as HTMLInputElement).value = String(field === 'startBeat' ? selected.startBeat + 1 : selected[field]);
      return;
    }
    const next = this.notes().map((note) => note.id === selected.id ? { ...note, [field]: value } : note);
    this.projects.updateTrackPhrase(this.selectedTrack().id, next);
  }

  deleteSelected(): void {
    const selected = this.selectedNote();
    if (!selected) return;
    this.projects.updateTrackPhrase(this.selectedTrack().id, this.notes().filter((note) => note.id !== selected.id));
    this.selectedNoteId.set(null);
  }

  restoreGenerated(): void {
    this.projects.clearTrackPhrase(this.selectedTrack().id);
    this.selectedNoteId.set(null);
  }

  updateDrumBar(event: Event): void {
    const value = Number((event.target as HTMLSelectElement).value);
    if (Number.isInteger(value) && value >= 0 && value < this.drumBars().length) {
      this.drumBar.set(value);
    }
  }

  private makeNoteId(): string {
    this.newNoteId++;
    return `edit-${Date.now()}-${this.newNoteId}`;
  }

  private snapBeat(value: number): number {
    return Math.round(value / BEAT_SNAP) * BEAT_SNAP;
  }

  private clamp(value: number, minimum: number, maximum: number): number {
    return Math.max(minimum, Math.min(maximum, value));
  }
}
