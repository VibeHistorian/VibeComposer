import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, computed, inject, signal, TemplateRef, viewChild } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import type { ArrangedPart, CompositionTrack, PhraseNote } from '../../core/project/project.model';
import { PART_GENERATION_AVAILABLE, tracksInRoleOrder } from '../../core/project/project.model';
import { generateTrackPhrase, phraseForTrack, layOutTrackPhrase } from '../../core/music/phrase';
import { drumInstrumentName } from '../../core/music/drum-instruments';
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
  readonly selectedNoteId = signal<string | null>(null);
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
    if (this.initialNoteId) this.selectedNoteId.set(this.initialNoteId);
  }

  selectTrack(trackId: string): void {
    this.workspaceUi.selectTrack(trackId);
    const track = this.project().tracks.find((candidate) => candidate.id === trackId);
    if (track) this.loadTrackDraft(track);
    this.selectedNoteId.set(null);
  }

  selectNote(id: string): void {
    this.selectedNoteId.set(id);
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
    this.draftNotes.set(next);
    this.restoreOnApply = false;
  }

  deleteSelected(): void {
    const selected = this.selectedNote();
    if (!selected) return;
    this.draftNotes.set(this.notes().filter((note) => note.id !== selected.id));
    this.restoreOnApply = false;
    this.selectedNoteId.set(null);
  }

  restoreGenerated(): void {
    if (!this.generationAvailable()) return;
    this.draftNotes.set(generateTrackPhrase(this.project(), this.selectedTrack()));
    this.restoreOnApply = true;
    this.selectedNoteId.set(null);
  }

  replaceNote(note: PhraseNote): void {
    this.draftNotes.update(notes => notes.map(existing => existing.id === note.id ? { id: note.id, midi: note.midi,
      startBeat: note.startBeat, durationBeats: note.durationBeats, velocity: note.velocity } : existing));
    this.restoreOnApply = false;
  }

  addNoteAt(position: { midi: number; startBeat: number }): void {
    if (this.notes().length >= 2048 || position.startBeat >= this.phraseBeats()) return;
    const note = { id: this.makeNoteId(), midi: position.midi, startBeat: Math.max(0, position.startBeat),
      durationBeats: Math.min(0.5, this.phraseBeats() - position.startBeat), velocity: 90 };
    this.draftNotes.update(notes => [...notes, note]);
    this.selectedNoteId.set(note.id);
    this.restoreOnApply = false;
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
  }

}
