import { ChangeDetectionStrategy, Component, EventEmitter, HostListener, Input, Output, computed, inject, signal, TemplateRef, viewChild } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import type { ArrangedPart, CompositionTrack, PhraseNote } from '../../core/project/project.model';
import { PART_GENERATION_AVAILABLE, tracksInRoleOrder } from '../../core/project/project.model';
import { generateTrackPhrase, phraseForTrack, layOutTrackPhrase } from '../../core/music/phrase';
import { drumInstrumentName } from '../../core/music/drum-instruments';
import { PhraseEditHistory, type PhraseEditSnapshot } from '../../core/music/phrase-edit';
import { ProjectService } from '../../core/project/project.service';
import { WorkspaceUiService } from '../../shared/workspace-ui.service';


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
  readonly inspectorTemplate = viewChild.required<TemplateRef<unknown>>('inspector');
  @Input() sectionId: string | null = null;
  @Input() phraseStartBeat = 0;
  @Input() phraseLength: number | null = null;
  @Input() initialNoteId: string | null = null;
  @Output() applied = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();
  readonly drumVoices = computed(() => {
    const track = this.selectedTrack();
    if (track.role !== 'drums') return [];
    // Manual phrases keep all their pitches editable without adding preset groove lanes.
    return [...new Set([track.generatorSettings.pitch, ...this.notes().map(note => note.midi)])]
      .sort((left, right) => left - right).map(midi => ({ midi, label: drumInstrumentName(midi) }));
  });
  readonly selectedTrack = computed<CompositionTrack>(() => this.project().tracks.find((track) => track.id === this.workspaceUi.selectedTrackId())
    ?? this.project().tracks[0]);
  readonly selectedPart = computed<ArrangedPart>(() => this.selectedTrack().role);
  readonly tracks = computed(() => tracksInRoleOrder(this.project().tracks));
  readonly generationAvailable = computed(() => PART_GENERATION_AVAILABLE[this.selectedPart()]);
  readonly applyError = signal<string | null>(null);
  readonly selectedNoteIds = signal<readonly string[]>([]);
  readonly selectedNoteId = computed(() => this.selectedNoteIds()[0] ?? null);
  readonly snapBeats = signal(0.25);
  readonly gridChoices = [0.125, 1 / 6, 0.25, 1 / 3, 0.5, 2 / 3, 1, 4 / 3, 2, 4];
  readonly transposeOpen = signal(false);
  readonly transposeAmount = signal(0);
  readonly historyRevision = signal(0);
  private readonly history = new PhraseEditHistory();
  readonly canUndo = computed(() => { this.historyRevision(); return this.history.canUndo; });
  readonly canRedo = computed(() => { this.historyRevision(); return this.history.canRedo; });
  private lastDuration = 0.5;
  readonly phraseBeats = computed(() => this.phraseLength ?? this.project().progression.length * 4);
  readonly draftNotes = signal<PhraseNote[]>([]);
  readonly notes = computed(() => [...this.draftNotes()].sort((left, right) => left.startBeat - right.startBeat || left.midi - right.midi));
  readonly displayNotes = this.notes;
  readonly selectedNote = computed(() => this.displayNotes().find((note) => note.id === this.selectedNoteId()) ?? null);
  readonly isEdited = computed(() => this.selectedTrack().editedPhrase !== undefined
    || this.project().arrangement.find(section => section.id === this.sectionId)?.trackPhrases?.[this.selectedTrack().id] !== undefined);
  readonly noteCount = computed(() => this.notes().length);
  private newNoteId = 0;
  private baselineNotes: readonly PhraseNote[] = [];
  private restoreOnApply = false;

  ngOnInit(): void {
    this.loadTrackDraft(this.selectedTrack());
    if (this.initialNoteId) this.selectNote(this.initialNoteId);
    this.history.reset({ notes: this.draftNotes(), selection: this.selectedNoteIds(), restore: false });
  }

  selectTrack(trackId: string): void {
    this.workspaceUi.selectTrack(trackId);
    const track = this.project().tracks.find((candidate) => candidate.id === trackId);
    if (track) this.loadTrackDraft(track);
    this.selectedNoteIds.set([]);
  }

  selectNote(id: string): void {
    this.selectedNoteIds.set([id]);
  }

  selectNotes(ids: readonly string[]): void { this.selectedNoteIds.set(ids); }
  selectAll(): void { this.selectNotes(this.notes().map(note => note.id)); }
  undo(): void { this.loadHistory(this.history.undo()); }
  redo(): void { this.loadHistory(this.history.redo()); }
  private loadHistory(snapshot?: PhraseEditSnapshot): void {
    if (!snapshot) return;
    this.draftNotes.set([...snapshot.notes]);
    this.selectNotes(snapshot.selection);
    this.restoreOnApply = snapshot.restore;
    this.historyRevision.update(value => value + 1);
    this.applyError.set(null);
  }
  private record(): void {
    this.history.record({ notes: this.draftNotes(), selection: this.selectedNoteIds(), restore: this.restoreOnApply });
    this.historyRevision.update(value => value + 1);
    this.applyError.set(null);
  }
  @HostListener('document:keydown', ['$event'])
  keydown(event: KeyboardEvent): void {
    if (event.defaultPrevented || (event.target instanceof Element && event.target.closest('input, textarea, select, [contenteditable="true"]'))) return;
    const command = event.ctrlKey || event.metaKey;
    const key = event.key.toLowerCase();
    if (command && key === 'z') event.shiftKey ? this.redo() : this.undo();
    else if (command && key === 'y') this.redo();
    else if (command && key === 'a') this.selectAll();
    else if (command && key === 't') this.transposeOpen.set(true);
    else if (event.key === 'Delete' || event.key === 'Backspace') this.deleteSelected();
    else return;
    event.preventDefault();
    event.stopPropagation();
  }
  transposeSelected(): void {
    const amount = this.transposeAmount();
    if (!Number.isInteger(amount)) return;
    this.replaceNotes(this.notes().filter(n => this.selectedNoteIds().includes(n.id))
      .map(n => ({ ...n, midi: Math.max(0, Math.min(127, n.midi + amount)) })));
    this.transposeOpen.set(false);
  }
  setTranspose(event: Event): void { this.transposeAmount.set(Number((event.target as HTMLInputElement).value)); }
  setGrid(event: Event): void { this.snapBeats.set(Number((event.target as HTMLSelectElement).value)); }
  splitNotes(position: { id: string; beat: number }): void {
    const ids = this.selectedNoteIds().includes(position.id) ? this.selectedNoteIds() : [position.id];
    const next = this.notes().flatMap(note => {
      const end = note.startBeat + note.durationBeats;
      if (!ids.includes(note.id) || position.beat <= note.startBeat || position.beat >= end) return [note];
      return [{ ...note, durationBeats: position.beat - note.startBeat },
        { ...note, id: this.makeNoteId(), startBeat: position.beat, durationBeats: end - position.beat }];
    });
    if (next.length > 2048) return;
    this.draftNotes.set(next);
    this.restoreOnApply = false;
    this.record();
  }
  replaceNotes(notes: readonly PhraseNote[], duplicate = false): void {
    if (!notes.length) return;
    if (duplicate && this.notes().length + notes.length > 2048) return;
    if (duplicate) {
      const copies = notes.map(note => ({ ...note, id: this.makeNoteId() }));
      this.draftNotes.update(existing => [...existing, ...copies]);
      this.selectNotes(copies.map(note => note.id));
    } else {
      const updates = new Map(notes.map(note => [note.id, note]));
      this.draftNotes.update(existing => existing.map(note => {
        const updated = updates.get(note.id);
        return updated ? { id: updated.id, midi: updated.midi, startBeat: updated.startBeat,
          durationBeats: updated.durationBeats, velocity: updated.velocity } : note;
      }));
    }
    if (notes.length === 1) this.lastDuration = notes[0].durationBeats;
    this.restoreOnApply = false;
    this.record();
  }

  noteName(midi: number): string {
    if (this.selectedPart() === 'drums') return drumInstrumentName(midi);
    const names = ['C', 'C♯', 'D', 'D♯', 'E', 'F', 'F♯', 'G', 'G♯', 'A', 'A♯', 'B'];
    return `${names[midi % 12]}${Math.floor(midi / 12) - 1}`;
  }

  addNote(): void {
    if (this.notes().length >= 2048) {
      return;
    }
    const track = this.selectedTrack();
    const preferredPitch = this.selectedNote()?.midi ?? (track.role === 'drums' ? track.generatorSettings.pitch : track.role === 'bass' ? 48 : 60);
    const pitch = Math.max(0, Math.min(127, preferredPitch));
    const durationBeats = Math.min(0.5, this.phraseBeats());
    const occupied = new Set(this.notes().filter((note) => note.midi === pitch)
      .map((note) => Math.round(note.startBeat * 4)));
    let slot = 0;
    while (slot < this.phraseBeats() * 4 && occupied.has(slot)) slot++;
    const startBeat = Math.min(slot / 4, Math.max(0, this.phraseBeats() - durationBeats));
    const note: PhraseNote = {
      id: this.makeNoteId(), midi: pitch, startBeat, durationBeats, velocity: 90,
    };
    this.draftNotes.set([...this.notes(), note]);
    this.restoreOnApply = false;
    this.selectNote(note.id);
    this.record();
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
    this.draftNotes.set(next);
    this.restoreOnApply = false;
    this.record();
  }

  deleteSelected(): void {
    const ids = this.selectedNoteIds();
    if (!ids.length) return;
    this.draftNotes.set(this.notes().filter(note => !ids.includes(note.id)));
    this.restoreOnApply = false;
    this.selectedNoteIds.set([]);
    this.record();
  }

  restoreGenerated(): void {
    if (!this.generationAvailable()) return;
    this.draftNotes.set(generateTrackPhrase(this.project(), this.selectedTrack()));
    this.restoreOnApply = true;
    this.selectedNoteIds.set([]);
    this.record();
  }

  replaceNote(note: PhraseNote): void {
    this.replaceNotes([note]);
  }

  addNoteAt(position: { midi: number; startBeat: number; durationBeats?: number; velocity?: number }): void {
    if (this.notes().length >= 2048 || position.startBeat >= this.phraseBeats()) return;
    const note = { id: this.makeNoteId(), midi: position.midi, startBeat: Math.max(0, position.startBeat),
      durationBeats: Math.min(position.durationBeats ?? this.lastDuration, this.phraseBeats() - position.startBeat), velocity: position.velocity ?? 90 };
    this.draftNotes.update(notes => [...notes, note]);
    this.selectNote(note.id);
    this.restoreOnApply = false;
    this.record();
  }

  applyLocalChanges(): void {
    if (!this.sectionId) return;
    const transpose = this.selectedPart() === 'drums' ? 0 : this.project().transposeSemitones ?? 0;
    const section = this.project().arrangement.find(section => section.id === this.sectionId);
    if (!section) return;
    const track = this.selectedTrack();
    const source = layOutTrackPhrase({ ...this.project(), arrangement: [{ ...section, trackPresence: { ...section.trackPresence, [track.id]: true } }] }, track);
    const end = this.phraseStartBeat + this.phraseBeats();
    const preserved = source.filter(note => note.startBeat < this.phraseStartBeat || note.startBeat >= end);
    const changed = this.notes().map(note => ({ ...note, startBeat: note.startBeat + this.phraseStartBeat }));
    const ids = new Set<string>();
    const notes = [...preserved, ...changed].map(note => {
      const id = ids.has(note.id) ? this.makeNoteId() : note.id;
      ids.add(id);
      return { ...note, id, midi: Math.max(0, Math.min(127, note.midi - transpose)) };
    });
    if (!this.projects.updateSectionTrackPhrase(this.sectionId, track.id, notes)) {
      this.applyError.set('Cannot save this section phrase. Check note timings and the 2048-note limit.');
      return;
    }
    this.applied.emit();
  }

  applyChanges(): void {
    if (this.restoreOnApply) this.projects.clearTrackPhrase(this.selectedTrack().id);
    else {
      const transpose = this.sectionId && this.selectedPart() !== 'drums' ? this.project().transposeSemitones ?? 0 : 0;
      if (!this.projects.updateTrackPhrase(this.selectedTrack().id, this.draftNotes().map(note => ({ ...note, midi: Math.max(0, Math.min(127, note.midi - transpose)) })))) {
        this.applyError.set('Cannot save this global phrase. Notes must fit within 128 beats and the 2048-note limit.');
        return;
      }
    }
    this.applied.emit();
  }

  cancelChanges(): void { this.cancelled.emit(); }

  private makeNoteId(): string {
    this.newNoteId++;
    return `edit-${Date.now()}-${this.newNoteId}`;
  }

  private loadTrackDraft(track: CompositionTrack): void {
    const section = this.project().arrangement.find(section => section.id === this.sectionId);
    const source = section ? layOutTrackPhrase({ ...this.project(), arrangement: [{ ...section, trackPresence: { ...section.trackPresence, [track.id]: true } }] }, track)
      .filter(note => note.startBeat >= this.phraseStartBeat && note.startBeat < this.phraseStartBeat + this.phraseBeats())
      .map(note => ({ ...note, startBeat: note.startBeat - this.phraseStartBeat, durationBeats: Math.min(note.durationBeats, this.phraseBeats() - (note.startBeat - this.phraseStartBeat)) }))
      : phraseForTrack(this.project(), track);
    this.baselineNotes = [...source].sort((left, right) => left.startBeat - right.startBeat || left.midi - right.midi);
    const ids = new Set<string>();
    this.baselineNotes = this.baselineNotes.map(note => {
      const id = ids.has(note.id) ? this.makeNoteId() : note.id;
      ids.add(id);
      return { ...note, id };
    });
    this.draftNotes.set([...this.baselineNotes]);
    this.restoreOnApply = false;
    this.selectedNoteIds.set([]);
    this.history.reset({ notes: this.draftNotes(), selection: [], restore: false });
    this.historyRevision.update(value => value + 1);
  }

}
