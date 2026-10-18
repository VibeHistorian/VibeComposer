import { afterNextRender, Injector, ChangeDetectionStrategy, Component, EventEmitter, HostListener, Input, Output, computed, inject, signal, TemplateRef, viewChild } from '@angular/core';
import { DOCUMENT, TitleCasePipe } from '@angular/common';
import type { ArrangedPart, CompositionTrack, PhraseNote } from '../../core/project/project.model';
import { PART_GENERATION_AVAILABLE, tracksInRoleOrder } from '../../core/project/project.model';
import { generateTrackPhrase, phraseForTrack, layOutTrackPhrase } from '../../core/music/phrase';
import { MidiOutputService } from '../../core/audio/midi-output.service';
import { PhraseEditHistory, phraseNotesEqual, snapMidiToScale, type PhraseEditSnapshot } from '../../core/music/phrase-edit';
import { getDiatonicChords, getPitchClass } from '../../core/music/harmony';
import { ProjectService } from '../../core/project/project.service';
import { WorkspaceUiService } from '../../shared/workspace-ui.service';
import { isEditingText } from '../../shared/keyboard-shortcuts';


export interface PhraseDraftNote extends PhraseNote { readonly trackId: string; }

@Component({
  selector: 'vc-edit-workspace',
  imports: [TitleCasePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './edit-workspace.component.html',
  styleUrl: './edit-workspace.component.css',
})
export class EditWorkspaceComponent {
  readonly projects = inject(ProjectService);
  private readonly midi = inject(MidiOutputService);
  private readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);
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
    if (this.selectedPart() !== 'drums') return [];
    return [...new Set([...this.editedTracks().flatMap(track => track.role === 'drums' ? [track.generatorSettings.pitch] : []), ...this.notes().map(note => note.midi)])]
      .sort((left, right) => left - right).map(midi => ({ midi, label: this.midi.drumLabel(midi) }));
  });
  readonly selectedTrack = computed<CompositionTrack>(() => this.project().tracks.find((track) => track.id === this.workspaceUi.selectedTrackId())
    ?? this.project().tracks[0]);
  readonly editedTracks = computed(() => this.selectedTrack().role === 'drums' ? this.project().tracks.filter(track => track.role === 'drums') : [this.selectedTrack()]);
  readonly selectedPart = computed<ArrangedPart>(() => this.selectedTrack().role);
  readonly tracks = computed(() => tracksInRoleOrder(this.project().tracks));
  readonly generationAvailable = computed(() => PART_GENERATION_AVAILABLE[this.selectedPart()]);
  readonly applyError = signal<string | null>(null);
  readonly selectedNoteIds = signal<readonly string[]>([]);
  readonly selectedNoteId = computed(() => this.selectedNoteIds()[0] ?? null);
  readonly snapBeats = signal(0.25);
  readonly snapToScale = signal(true);
  readonly scalePitchClasses = computed(() => {
    const transpose = this.sectionId ? this.project().transposeSemitones ?? 0 : 0;
    return getDiatonicChords(this.project().key, this.project().scale)
      .map(chord => ((getPitchClass(chord.root)! + transpose) % 12 + 12) % 12);
  });
  readonly hasChanges = computed(() => {
    this.historyRevision();
    return this.changedTracks().length > 0;
  });
  readonly changedTracks = computed(() => {
    this.historyRevision();
    return this.editedTracks().filter(track => this.restoredTrackIds().includes(track.id)
      || !phraseNotesEqual(this.notes().filter(note => note.trackId === track.id), this.baselineNotes.filter(note => note.trackId === track.id)));
  });
  private markManual(trackIds: readonly string[]): void {
    this.restoredTrackIds.update(ids => ids.filter(id => !trackIds.includes(id)));
  }
  openTranspose(): void {
    this.transposeOpen.set(true);
    afterNextRender(() => {
      const field = this.document.getElementById('phrase-transpose') as HTMLInputElement | null;
      field?.focus();
      field?.select();
    }, { injector: this.injector });
  }
  private snapPitch(midi: number): number {
    return this.snapToScale() && this.selectedPart() !== 'drums' ? snapMidiToScale(midi, this.scalePitchClasses()) : midi;
  }
  readonly gridChoices = [0.125, 1 / 6, 0.25, 1 / 3, 0.5, 2 / 3, 1, 4 / 3, 2, 4];
  readonly transposeOpen = signal(false);
  readonly transposeAmount = signal(0);
  readonly historyRevision = signal(0);
  private readonly history = new PhraseEditHistory<PhraseDraftNote>();
  readonly canUndo = computed(() => { this.historyRevision(); return this.history.canUndo; });
  readonly canRedo = computed(() => { this.historyRevision(); return this.history.canRedo; });
  private lastDuration = 0.5;
  readonly phraseBeats = computed(() => this.phraseLength ?? this.project().progression.length * 4);
  readonly draftNotes = signal<PhraseDraftNote[]>([]);
  readonly draftReady = signal(false);
  readonly notes = computed(() => [...this.draftNotes()].sort((left, right) => left.startBeat - right.startBeat || left.midi - right.midi));
  readonly displayNotes = this.notes;
  readonly selectedNote = computed(() => this.displayNotes().find((note) => note.id === this.selectedNoteId()) ?? null);
  readonly isEdited = computed(() => this.selectedTrack().editedPhrase !== undefined
    || this.project().arrangement.find(section => section.id === this.sectionId)?.trackPhrases?.[this.selectedTrack().id] !== undefined);
  readonly noteCount = computed(() => this.notes().length);
  private newNoteId = 0;
  private baselineNotes: readonly PhraseDraftNote[] = [];
  private readonly restoredTrackIds = signal<readonly string[]>([]);

  ngOnInit(): void {
    this.loadTrackDraft(this.selectedTrack());
    if (this.initialNoteId) this.selectNote(this.initialNoteId);
    this.history.reset({ notes: this.draftNotes(), selection: this.selectedNoteIds(), restore: false });
    this.draftReady.set(true);
  }

  selectTrack(trackId: string): void {
    const editingDrums = this.selectedPart() === 'drums';
    this.workspaceUi.selectTrack(trackId);
    const track = this.project().tracks.find((candidate) => candidate.id === trackId);
    if (track && !(editingDrums && track.role === 'drums')) this.loadTrackDraft(track);
    this.selectedNoteIds.set([]);
  }

  selectNote(id: string): void {
    this.selectNotes([id]);
  }

  selectNotes(ids: readonly string[]): void {
    this.selectedNoteIds.set(ids);
    const note = this.notes().find(note => note.id === ids[0]);
    if (note) this.workspaceUi.selectedTrackId.set(note.trackId);
  }
  selectAll(): void { this.selectNotes(this.notes().map(note => note.id)); }
  undo(): void { this.loadHistory(this.history.undo()); }
  redo(): void { this.loadHistory(this.history.redo()); }
  private loadHistory(snapshot?: PhraseEditSnapshot<PhraseDraftNote>): void {
    if (!snapshot) return;
    this.draftNotes.set([...snapshot.notes]);
    this.selectNotes(snapshot.selection);
    this.restoredTrackIds.set(snapshot.restoredTrackIds ?? []);
    this.historyRevision.update(value => value + 1);
    this.applyError.set(null);
  }
  private record(): void {
    this.history.record({ notes: this.draftNotes(), selection: this.selectedNoteIds(), restore: this.restoredTrackIds().length > 0, restoredTrackIds: this.restoredTrackIds() });
    this.historyRevision.update(value => value + 1);
    this.applyError.set(null);
  }
  @HostListener('document:keydown', ['$event'])
  keydown(event: KeyboardEvent): void {
    if (event.defaultPrevented || event.isComposing || isEditingText(event)) return;
    const command = event.ctrlKey || event.metaKey;
    const key = event.key.toLowerCase();
    if (command && key === 'a') this.selectAll();
    else if (event.shiftKey && !command && !event.altKey && key === 't') this.openTranspose();
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
    if (this.editedTracks().some(track => next.filter(note => note.trackId === track.id).length > 2048)) return;
    this.markManual(this.notes().filter(note => ids.includes(note.id)).map(note => note.trackId));
    this.draftNotes.set(next);
    this.record();
  }
  replaceNotes(notes: readonly PhraseNote[], duplicate = false): void {
    if (!notes.length) return;
    if (duplicate && this.editedTracks().some(track => this.notes().filter(note => note.trackId === track.id).length
      + notes.filter(note => this.notes().find(existing => existing.id === note.id)?.trackId === track.id).length > 2048)) return;
    if (duplicate) {
      const copies = notes.map(note => ({ ...note, id: this.makeNoteId(), trackId: this.notes().find(existing => existing.id === note.id)?.trackId ?? this.selectedTrack().id }));
      this.draftNotes.update(existing => [...existing, ...copies]);
      this.selectNotes(copies.map(note => note.id));
    } else {
      const updates = new Map(notes.map(note => [note.id, note]));
      this.draftNotes.update(existing => existing.map(note => {
        const updated = updates.get(note.id);
        return updated ? { trackId: note.trackId, id: updated.id, midi: updated.midi, startBeat: updated.startBeat,
          durationBeats: updated.durationBeats, velocity: updated.velocity } : note;
      }));
    }
    if (notes.length === 1) this.lastDuration = notes[0].durationBeats;
    this.markManual(notes.map(note => this.notes().find(existing => existing.id === note.id)?.trackId ?? this.selectedTrack().id));
    this.record();
  }

  noteName(midi: number): string {
    if (this.selectedPart() === 'drums') return this.midi.drumLabel(midi);
    const names = ['C', 'C♯', 'D', 'D♯', 'E', 'F', 'F♯', 'G', 'G♯', 'A', 'A♯', 'B'];
    return `${names[midi % 12]}${Math.floor(midi / 12) - 1}`;
  }

  addNote(): void {
    if (this.notes().filter(note => note.trackId === this.selectedTrack().id).length >= 2048) {
      return;
    }
    const track = this.selectedTrack();
    const preferredPitch = this.selectedNote()?.midi ?? (track.role === 'drums' ? track.generatorSettings.pitch : track.role === 'bass' ? 48 : 60);
    const pitch = this.snapPitch(Math.max(0, Math.min(127, preferredPitch)));
    const durationBeats = Math.min(0.5, this.phraseBeats());
    const occupied = new Set(this.notes().filter((note) => note.midi === pitch)
      .map((note) => Math.round(note.startBeat * 4)));
    let slot = 0;
    while (slot < this.phraseBeats() * 4 && occupied.has(slot)) slot++;
    const startBeat = Math.min(slot / 4, Math.max(0, this.phraseBeats() - durationBeats));
    const note: PhraseDraftNote = {
      trackId: track.id,
      id: this.makeNoteId(), midi: pitch, startBeat, durationBeats, velocity: 90,
    };
    this.draftNotes.set([...this.notes(), note]);
    this.markManual([track.id]);
    this.selectNote(note.id);
    this.record();
  }

  updateSelected(field: 'midi' | 'startBeat' | 'durationBeats' | 'velocity', event: Event): void {
    const selected = this.selectedNote();
    const inputValue = Number((event.target as HTMLInputElement).value);
    const value = field === 'startBeat' ? inputValue - 1 : field === 'midi' && Number.isInteger(inputValue) && inputValue >= 0 && inputValue <= 127 ? this.snapPitch(inputValue) : inputValue;
    if (!selected || !Number.isFinite(value)) return;

    const valid = field === 'midi' ? Number.isInteger(value) && value >= 0 && value <= 127
      : field === 'velocity' ? Number.isInteger(value) && value >= 1 && value <= 127
        : field === 'startBeat' ? value >= 0 && value + selected.durationBeats <= this.phraseBeats()
          : value > 0 && selected.startBeat + value <= this.phraseBeats();
    if (!valid) {
      (event.target as HTMLInputElement).value = String(field === 'startBeat' ? selected.startBeat + 1 : selected[field]);
      return;
    }
    if (field === 'midi') (event.target as HTMLInputElement).value = String(value);
    const next = this.notes().map((note) => note.id === selected.id ? { ...note, [field]: value } : note);
    this.draftNotes.set(next);
    this.markManual([selected.trackId]);
    this.record();
  }

  deleteSelected(): void {
    const ids = this.selectedNoteIds();
    if (!ids.length) return;
    this.markManual(this.notes().filter(note => ids.includes(note.id)).map(note => note.trackId));
    this.draftNotes.set(this.notes().filter(note => !ids.includes(note.id)));
    this.selectedNoteIds.set([]);
    this.record();
  }

  restoreGenerated(): void {
    if (!this.generationAvailable()) return;
    const track = this.selectedTrack();
    const usedIds = new Set(this.notes().filter(note => note.trackId !== track.id).map(note => note.id));
    const notes = generateTrackPhrase(this.project(), track).filter(note => note.startBeat < this.phraseBeats())
      .map(note => {
        const id = usedIds.has(note.id) ? this.makeNoteId() : note.id; usedIds.add(id);
        return { ...note, id, trackId: track.id, durationBeats: Math.min(note.durationBeats, this.phraseBeats() - note.startBeat) };
      });
    this.draftNotes.update(current => [...current.filter(note => note.trackId !== track.id), ...notes]);
    this.restoredTrackIds.update(ids => [...new Set([...ids, track.id])]);
    this.selectedNoteIds.set([]);
    this.record();
  }

  replaceNote(note: PhraseNote): void {
    this.replaceNotes([note]);
  }

  addNoteAt(position: { trackId?: string; midi: number; startBeat: number; durationBeats?: number; velocity?: number }): void {
    const track = this.editedTracks().find(track => track.id === position.trackId)
      ?? (this.selectedPart() === 'drums' ? this.editedTracks().find(track => track.role === 'drums' && track.generatorSettings.pitch === position.midi && track.id === this.selectedTrack().id)
        ?? this.editedTracks().find(track => track.role === 'drums' && track.generatorSettings.pitch === position.midi) : undefined) ?? this.selectedTrack();
    if (this.notes().filter(note => note.trackId === track.id).length >= 2048 || position.startBeat >= this.phraseBeats()) return;
    const note = { id: this.makeNoteId(), trackId: track.id, midi: this.snapPitch(position.midi), startBeat: Math.max(0, position.startBeat),
      durationBeats: Math.min(position.durationBeats ?? this.lastDuration, this.phraseBeats() - position.startBeat), velocity: position.velocity ?? 90 };
    this.draftNotes.update(notes => [...notes, note]);
    this.selectNote(note.id);
    this.markManual([track.id]);
    this.record();
  }

  smartExit(): void {
    if (!this.hasChanges()) { this.cancelChanges(); return; }
    this.commitChanges('smart');
  }
  applyLocalChanges(): void { if (this.sectionId) this.commitChanges('local'); }
  applyChanges(): void { this.commitChanges('global'); }
  private commitChanges(destination: 'local' | 'global' | 'smart'): void {
    if (!this.hasChanges()) return;
    const section = this.project().arrangement.find(section => section.id === this.sectionId);
    const edits = this.changedTracks().map(track => {
      const local = destination === 'local' || destination === 'smart' && section?.trackPhrases?.[track.id] !== undefined;
      const transpose = this.sectionId && track.role !== 'drums' ? this.project().transposeSemitones ?? 0 : 0;
      const draft = this.notes().filter(note => note.trackId === track.id);
      if (!local && this.restoredTrackIds().includes(track.id)) return { trackId: track.id, notes: null };
      const source = local && section ? layOutTrackPhrase({ ...this.project(), arrangement: [{ ...section,
        trackPresence: { ...section.trackPresence, [track.id]: true } }] }, track) : [];
      const end = this.phraseStartBeat + this.phraseBeats();
      const preserved = source.filter(note => note.startBeat < this.phraseStartBeat || note.startBeat >= end);
      const changed = draft.map(note => ({ ...note, startBeat: note.startBeat + (local ? this.phraseStartBeat : 0) }));
      const ids = new Set<string>();
      const notes = [...preserved, ...changed].map(note => {
        const id = ids.has(note.id) ? this.makeNoteId() : note.id; ids.add(id);
        return { id, midi: Math.max(0, Math.min(127, note.midi - transpose)), startBeat: note.startBeat,
          durationBeats: note.durationBeats, velocity: note.velocity };
      });
      return { trackId: track.id, ...(local && section ? { sectionId: section.id } : {}), notes };
    });
    if (!this.projects.applyPhraseEdits(edits)) {
      this.applyError.set('Cannot save these phrases. Check note timings and the 2048-note limit per track.');
      return;
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
    const tracks = track.role === 'drums' ? [track, ...this.project().tracks.filter(candidate => candidate.role === 'drums' && candidate.id !== track.id)] : [track];
    const ids = new Set<string>();
    this.baselineNotes = tracks.flatMap(track => {
      const source = section ? layOutTrackPhrase({ ...this.project(), arrangement: [{ ...section, trackPresence: { ...section.trackPresence, [track.id]: true } }] }, track)
        .filter(note => note.startBeat >= this.phraseStartBeat && note.startBeat < this.phraseStartBeat + this.phraseBeats())
        .map(note => ({ ...note, startBeat: note.startBeat - this.phraseStartBeat, durationBeats: Math.min(note.durationBeats, this.phraseBeats() - (note.startBeat - this.phraseStartBeat)) }))
        : phraseForTrack(this.project(), track);
      return source.map(note => {
        const id = ids.has(note.id) ? this.makeNoteId() : note.id; ids.add(id);
        return { ...note, id, trackId: track.id };
      });
    });
    this.draftNotes.set([...this.baselineNotes]);
    this.restoredTrackIds.set([]);
    this.selectedNoteIds.set([]);
    this.history.reset({ notes: this.draftNotes(), selection: [], restore: false });
    this.historyRevision.update(value => value + 1);
  }
}
