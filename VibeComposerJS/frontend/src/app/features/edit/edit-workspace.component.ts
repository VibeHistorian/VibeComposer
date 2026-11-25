import { withoutManualAdjustments } from '../../core/music/manual-phrase-processing';
import { convertManualPhrase } from '../../core/music/manual-phrase-mode';
import { MAX_DRUM_SOUNDS } from '../../core/music/drum-instruments';
import { resolveProjectTimeline } from '../../core/music/project-timeline';
import { resolvedDrumSlots } from '../../core/project/project-drum-kit';
import { AudioPlaybackService } from '../../core/audio/audio-playback.service';
import type { LiveMidiNote } from '../../core/audio/midi-input.service';
import { LiveRecordingComponent } from '../recording/live-recording.component';
import { recordedSegments, keyboardLabels, type RecordedSpan, type RecordingPitch } from '../../core/audio/live-recording';
import { SelectComponent } from '../../shared/select.component';
import { afterNextRender, Injector, ChangeDetectionStrategy, Component, HostListener, input, output, computed, inject, signal, TemplateRef, viewChild } from '@angular/core';
import { DOCUMENT, TitleCasePipe } from '@angular/common';
import { Dialog } from '@angular/cdk/dialog';
import { type ArrangedPart, PART_GENERATION_AVAILABLE, tracksInRoleOrder } from '../../core/music/part-role';
import type { CompositionTrack } from '../../core/project/track.model';
import type { CompositionProject } from '../../core/project/project.model';
import type { PhraseNote } from '../../core/music/phrase-note';
import { generateTrackPhrase, phraseForTrack, layOutTrackPhrase } from '../../core/music/phrase';
import { hasManualPhrase, type PhraseRangeEdit } from '../../core/music/phrase-range-edit';
import { applyPhraseGlideEdits, clipGlideSegments, EMPTY_PHRASE_GLIDES, GLIDE_LANES, glideNeutral, glidesEqual,
  PITCH_GLIDE_SEMITONES, replaceGlideSegments, shiftGlideSegments, type GlideLane, type GlideSegment,
  type TrackGlides } from '../../core/music/phrase-glides';
import { MidiOutputService } from '../../core/audio/midi-output.service';
import { PhraseEditHistory, phraseNotesEqual, snapMidiToScale, type PhraseEditSnapshot } from '../../core/music/phrase-edit';
import { BASE_KEY, getDiatonicChords, getPitchClass } from '../../core/music/harmony';
import { ProjectService } from '../../core/project/project.service';
import { WorkspaceUiService } from '../../core/ui/workspace-ui.service';
import { ContextClipboardService } from '../../core/ui/context-clipboard.service';
import { fitImportedMidi, scaleTranslation, type NoteHarmony } from '../../core/music/midi-import-options';
import { isEditingText } from '../../shared/keyboard-shortcuts';
import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { MAX_MIDI_IMPORT_BYTES } from '../../core/music/midi-import-limits';
import { generatePhraseMidi } from '../../core/music/midi-export';
import { downloadProjectFile } from '../../core/project/download-file';
import { exportFilename, safeExportName } from '../../core/project/export-filename';
import { sectionPhraseBounds } from '../../core/music/phrase-bounds';
import { sectionChordDurations, sectionDurationBeats, sectionProject, sectionTranspose } from '../../core/music/section-context';


export interface PhraseDraftNote extends PhraseNote { readonly trackId: string; }

@Component({
  selector: 'vc-edit-workspace',
  imports: [LiveRecordingComponent, SelectComponent, TitleCasePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './edit-workspace.component.html',
  styleUrl: './edit-workspace.component.css',
})
export class EditWorkspaceComponent {
  private readonly audio = inject(AudioPlaybackService);
  private readonly liveVoices = new Map<string, () => void>();
  readonly heldRecordingNotes = signal<PhraseDraftNote[]>([]);
  readonly recordingPitches = signal<readonly number[]>([]);
  readonly soundingPitches = signal<readonly number[]>([]);
  private readonly heldInputPitches = new Map<string, number>();
  private readonly liveRecordingSelection = signal<readonly string[]>([]);
  readonly canvasSelectedNoteIds = computed(() => this.recorder()?.running() ? this.liveRecordingSelection() : this.selectedNoteIds());
  readonly canvasNotes = computed(() => [...this.notes(), ...this.heldRecordingNotes()]);
  previewInput(note: LiveMidiNote & RecordingPitch): void {
    const release = this.liveVoices.get(note.id);
    this.liveVoices.delete(note.id); this.heldInputPitches.delete(note.id);
    try { release?.(); } catch { /* A disposed voice must not retain input/highlight state. */ }
    if (note.down) {
      const track = note.trackId === undefined ? this.selectedTrack() : this.editedTracks().find(track => track.id === note.trackId);
      if (track) {
        this.heldInputPitches.set(note.id, note.midi);
        this.liveVoices.set(note.id, this.audio.liveAudition(track, {
          id: note.id, midi: note.midi, velocity: note.velocity, startBeat: 0, durationBeats: 1,
        }, this.project()));
      }
    }
    const pitches = [...new Set(this.heldInputPitches.values())];
    this.soundingPitches.set(pitches); this.recordingPitches.set(pitches);
  }
  updateHeldNotes(spans: readonly (RecordedSpan & { id: string })[]): void {
    const selected: string[] = [];
    this.heldRecordingNotes.set(spans.flatMap(span => {
      const trackId = span.trackId ?? this.selectedTrack().id;
      const retained = this.retainedRecordingSpan(span);
      if (!retained) return [];
      const segments = recordedSegments(retained, this.phraseBeats(), this.recordingGrid(), true)
        .map((segment, index) => ({ id: 'recording-' + span.id + '-' + index, trackId,
          midi: segment.midi, startBeat: segment.start, durationBeats: segment.end - segment.start, velocity: segment.velocity }));
      // A held note crossing a loop highlights its current segment, not earlier passes.
      if (segments.length) selected.push(segments.at(-1)!.id);
      return segments;
    }));
    this.liveRecordingSelection.set(selected);
  }
  private recordingSnapshot?: PhraseEditSnapshot<PhraseDraftNote>;
  private recordingActive = false;
  private lastRecordingIds: string[] = [];
  private recordingCycle = 0;
  private readonly recordingNoteCycles = new Map<string, number>();
  beginRecording(): void {
    this.recordingSnapshot = { notes: [...this.draftNotes()], selection: [...this.selectedNoteIds()], restore: false, restoredTrackIds: this.restoredTrackIds(), phraseTranspose: this.freeTranspose() };
    this.recordingActive = true; this.lastRecordingIds = []; this.recordingCycle = 0; this.recordingNoteCycles.clear();
  }
  private discardRecordedNotes(beforeCycle = Infinity): void {
    if (!this.lastRecordingIds.length) return;
    const removed = new Set(this.lastRecordingIds.filter(id => (this.recordingNoteCycles.get(id) ?? -Infinity) < beforeCycle));
    if (!removed.size) return;
    this.draftNotes.update(notes => notes.filter(note => !removed.has(note.id)));
    this.selectedNoteIds.update(ids => ids.filter(id => !removed.has(id)));
    if (this.recordingSnapshot && phraseNotesEqual(this.draftNotes(), this.recordingSnapshot.notes)) {
      this.restoredTrackIds.set(this.recordingSnapshot.restoredTrackIds ?? []);
      this.selectNotes(this.recordingSnapshot.selection);
    }
    this.lastRecordingIds = this.lastRecordingIds.filter(id => !removed.has(id));
    for (const id of removed) this.recordingNoteCycles.delete(id);
  }
  cancelRecording(): void {
    if (!this.lastRecordingIds.length) return;
    this.discardRecordedNotes(); this.record();
  }
  recordCursor(cursor: { beat: number }): void {
    if (!this.recordingActive) return;
    // Retain the previous pass for one measure after the next pass begins.
    const cycle = Math.max(0, Math.floor((cursor.beat - this.recordingMeasureBeats()) / this.phraseBeats()));
    if (cycle <= this.recordingCycle) return;
    if (this.preferences.preferences().recordingDiscardOnLoop) this.discardRecordedNotes(cycle);
    this.recordingCycle = cycle;
  }
  private retainedRecordingSpan(span: RecordedSpan): RecordedSpan | null {
    this.recordCursor({ beat: span.end });
    if (!this.recordingActive || !this.preferences.preferences().recordingDiscardOnLoop) return span;
    const origin = this.recordingCycle * this.phraseBeats();
    const start = Math.max(span.start, origin);
    // Quantize within the retained pass so fractional loops cannot snap into the old pass.
    return start < span.end ? { ...span, start: start - origin, end: span.end - origin } : null;
  }
  finishRecording(): void {
    if (this.recordingActive) {
      this.recordingActive = false;
      this.record();
    }
    for (const release of this.liveVoices.values()) { try { release(); } catch { /* Continue releasing the other voices. */ } }
    this.liveVoices.clear(); this.heldInputPitches.clear(); this.soundingPitches.set([]); this.liveRecordingSelection.set([]); this.heldRecordingNotes.set([]); this.recordingPitches.set([]);
  }
  recordSpan(span: RecordedSpan): void {
    const trackId = span.trackId ?? this.selectedTrack().id;
    const retained = this.retainedRecordingSpan(span);
    if (!retained) return;
    const grid = this.recordingGrid();
    const snappedStart = grid === null ? retained.start : Math.floor(retained.start / grid + 1e-7) * grid;
    let cycle = this.recordingCycle + Math.floor(snappedStart / this.phraseBeats());
    for (const segment of recordedSegments(retained, this.phraseBeats(), grid)) {
      if (this.draftNotes().filter(note => note.trackId === trackId).length >= 2048) break;
      const note = { id: this.makeNoteId(), trackId, midi: Math.max(0, Math.min(127, segment.midi - this.displayTranspose())),
        startBeat: segment.start, durationBeats: segment.end - segment.start, velocity: segment.velocity };
      this.lastRecordingIds.push(note.id); this.recordingNoteCycles.set(note.id, cycle++);
      this.draftNotes.update(notes => [...notes, note]); this.selectNote(note.id); this.markManual([trackId]);
    }
    if (!this.recordingActive) this.record();
  }
  private drawnDrumTrackForPitch(midi: number): CompositionTrack | undefined {
    const selected = this.selectedTrack();
    if (selected.role !== 'drums') return undefined;
    if (selected.generatorSettings.pitch === midi) return selected;
    return this.editedTracks().find(track => track.role === 'drums' && track.generatorSettings.pitch === midi);
  }
  readonly preferActionShortcut = (event: KeyboardEvent): boolean => this.canPasteNotes() && this.preferences.matchesShortcut('pasteNotes', event)
    || this.selectedNoteIds().length > 0
    && (['selectAllNotes', 'copyNotes', 'transposeNotes', 'deleteNotes', 'undo', 'redo', 'playback'] as const)
      .some(action => this.preferences.matchesShortcut(action, event));
  private readonly recorder = viewChild(LiveRecordingComponent);
  readonly recordingGrid = computed(() => this.preferences.preferences().recordingLockGrid ? this.snapBeats() : null);
  readonly recordingMeasureBeats = computed(() => {
    const section = this.project().arrangement.find(section => section.id === this.sectionId());
    return section ? sectionChordDurations(this.project(), section)[0] : 4;
  });
  readonly phrasePlayheadBeat = computed(() => this.audio.beat() - this.recordingBackingContext().startBeat);
  readonly recordingBackingContext = computed(() => ({ project: this.project(),
    startBeat: (resolveProjectTimeline(this.project()).sections.find(section => section.sectionId === this.sectionId())?.startBeat ?? 0) + this.phraseStartBeat(),
    trackIds: this.editedTracks().map(track => track.id) }));
  readonly keyboardScale = computed(() => this.selectedPart() === 'drums' ? [] : this.scalePitchClasses());
  readonly keyboardTonic = computed(() => 36 + this.snappingHarmony().tonic);
  readonly keyboardLabels = computed(() => this.selectedPart() !== 'drums' && this.preferences.preferences().keyboardNoteInput
    ? keyboardLabels(this.preferences.preferences().keyboardMatchScale ? this.keyboardScale() : [], this.keyboardTonic()) : []);
  readonly gridSelectOptions = computed(() => this.gridChoices.map(grid => ({ value: grid, label: String(grid === 1 / 6 ? '1/6' : grid === 1 / 3 ? '1/3' : grid === 2 / 3 ? '2/3' : grid === 4 / 3 ? '4/3' : grid) })));

  readonly projects = inject(ProjectService);
  private readonly preferences = inject(UiPreferencesService);
  private readonly midi = inject(MidiOutputService);
  private readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  private readonly dialogs = inject(Dialog);
  private readonly clipboard = inject(ContextClipboardService);
  readonly workspaceUi = inject(WorkspaceUiService);
  readonly project = this.projects.project;
  readonly inspectorTemplate = viewChild.required<TemplateRef<unknown>>('inspector');
  readonly sectionId = input<string | null>(null);
  private readonly section = computed(() => this.project().arrangement.find(section => section.id === this.sectionId()));
  readonly phraseStartBeat = input<number>(0);
  readonly phraseLength = input<number | null>(null);
  readonly initialNoteId = input<string | null>(null);
  readonly applied = output<void>();
  readonly cancelled = output<void>();
  readonly drumVoices = computed(() => {
    if (this.selectedPart() !== 'drums') return [];
    return [...new Set([...this.editedTracks().flatMap(track => track.role === 'drums' ? [track.generatorSettings.pitch] : []), ...this.notes().map(note => note.midi)])]
      .sort((left, right) => left - right).map(midi => ({ midi, label: this.midi.drumLabel(midi, this.project().drumSlots) }));
  });
  readonly selectedTrack = computed<CompositionTrack>(() => this.project().tracks.find((track) => track.id === this.workspaceUi.selectedTrackId())
    ?? this.project().tracks[0]);
  readonly editedTracks = computed(() => this.selectedTrack().role === 'drums'
    ? this.project().tracks.filter(track => track.role === 'drums') : [this.selectedTrack()]);
  readonly editedTrackIds = computed(() => this.editedTracks().map(track => track.id), {
    equal: (left, right) => left.length === right.length && left.every((id, index) => id === right[index]),
  });
  readonly drumInputTracks = computed(() => this.selectedPart() === 'drums'
    ? this.editedTracks().flatMap(track => track.role === 'drums' ? [{ trackId: track.id, midi: track.generatorSettings.pitch }] : [])
    : null);
  readonly selectedPart = computed<ArrangedPart>(() => this.selectedTrack().role);
  readonly tracks = computed(() => tracksInRoleOrder(this.project().tracks));
  readonly generationAvailable = computed(() => PART_GENERATION_AVAILABLE[this.selectedPart()]);
  readonly applyError = signal<string | null>(null);
  readonly midiDragOver = signal(false);
  readonly freeTranspose = signal(0);
  private noteInsert: AbortController | null = null;

  private phraseExport(): { bytes: Uint8Array<ArrayBuffer>; name: string } {
    const project = this.project();
    const section = project.arrangement.find(section => section.id === this.sectionId());
    const context = sectionProject(project, section);
    const label = this.selectedPart() === 'drums' ? 'Drums' : this.selectedTrack().name;
    const name = `${exportFilename(context, this.preferences.exportNameFields())}_${safeExportName(label) || 'Phrase'}_phrase`;
    const notes = this.notes();
    const tracks = this.editedTracks().map(track => ({ track,
      notes: notes.filter(note => note.trackId === track.id), glides: this.draftGlides()[track.id],
    }));
    return { name, bytes: new Uint8Array(generatePhraseMidi(name, context.tempoBpm, tracks, this.phraseBeats(), this.midi.drumMapping(), this.preferences.midiPpq(), resolvedDrumSlots(project, tracks.filter(({ track }) => track.role === 'drums').flatMap(({ notes }) => notes.map(note => note.midi))))) };
  }

  exportPhrase(): void {
    try {
      const { bytes, name } = this.phraseExport();
      downloadProjectFile(new Blob([bytes], { type: 'audio/midi' }), name, 'mid');
      this.applyError.set(null);
    } catch (error) {
      this.applyError.set(error instanceof Error ? `Could not export phrase: ${error.message}` : 'Could not export phrase.');
    }
  }

  midiDragEnter(event: DragEvent): void {
    if (!event.dataTransfer?.types.includes('Files')) return;
    event.preventDefault(); event.stopPropagation();
    event.dataTransfer.dropEffect = 'copy';
    this.midiDragOver.set(true);
  }

  midiDragLeave(event: DragEvent): void {
    const target = event.currentTarget as HTMLElement;
    if (event.relatedTarget instanceof Node && target.contains(event.relatedTarget)) return;
    this.midiDragOver.set(false);
  }

  async dropMidi(event: DragEvent): Promise<void> {
    event.preventDefault(); event.stopPropagation();
    this.midiDragOver.set(false);
    this.noteInsert?.abort();
    const file = event.dataTransfer?.files[0];
    if (!file) return;
    if (!/\.(mid|midi)$/i.test(file.name) || file.size > MAX_MIDI_IMPORT_BYTES) {
      this.applyError.set('Drop a MIDI (.mid or .midi) file up to 8 MB.');
      return;
    }
    const controller = new AbortController();
    this.noteInsert = controller;
    const track = this.selectedTrack();
    const discard = this.preferences.discardPhraseNotesOnMidiDrop();
    this.applyError.set(null);
    try {
      const buffer = await file.arrayBuffer();
      const { readMidiFile } = await import('../../core/ui/midi-file-import');
      const imported = await readMidiFile(buffer, Math.min(132, this.editBounds().endBeat), 2048, controller.signal, {
        injector: this.injector, transpose: this.displayTranspose(), root: this.snappingHarmony().tonic - this.displayTranspose(),
        mode: this.snappingHarmony().scale, snap: this.snapToScale(),
        ...(track.role === 'drums' ? { drumSlots: resolvedDrumSlots(this.project()) } : {}),
      });
      if (controller.signal.aborted || !imported) return;
      const current = this.draftNotes();
      const remaining = discard ? [] : current;
      const capacity = 2048 - remaining.filter(note => note.trackId === track.id).length;
      const added = imported.slice(0, Math.max(0, capacity)).map(note => ({ ...note, id: this.makeNoteId(), trackId: track.id }));
      if (!added.length) {
        this.applyError.set(capacity <= 0 ? 'This track already has 2048 notes. Enable discarding old notes in Settings > MIDI or remove some notes first.'
          : 'The first MIDI note track has no notes within this phrase’s duration.');
        return;
      }
      this.insertNotes(added, discard);
    } catch (error) {
      if (!controller.signal.aborted) this.applyError.set(error instanceof Error ? error.message : 'Cannot read this MIDI file.');
    } finally {
      if (this.noteInsert === controller) this.noteInsert = null;
    }
  }

  private readonly phraseHarmony = computed<NoteHarmony | null>(() => this.selectedPart() === 'drums' ? null : ({
    tonic: getPitchClass(BASE_KEY)! + this.displayTranspose(),
    scale: this.section()?.context?.scale ?? this.project().scale,
  }));
  readonly canPasteNotes = computed(() => this.clipboard.has('phraseNotes') && !this.recorder()?.running());

  copyNotes(): void {
    const selected = new Set(this.selectedNoteIds());
    const notes = this.displayNotes().filter(note => selected.has(note.id));
    if (!notes.length) return;
    const startBeat = notes[0].startBeat;
    this.clipboard.copy('phraseNotes', {
      notes: notes.map(note => ({ ...note, startBeat: note.startBeat - startBeat })),
      harmony: this.phraseHarmony(),
    });
  }

  async pasteNotes(): Promise<void> {
    if (!this.canPasteNotes() || this.dialogs.openDialogs.length) return;
    const copied = this.clipboard.read('phraseNotes');
    if (!copied) return;
    const startBeat = this.phrasePlayheadBeat();
    const bounds = this.editBounds();
    if (startBeat < bounds.startBeat || startBeat >= bounds.endBeat) {
      this.applyError.set('Move the playhead inside the editable phrase to paste notes.');
      return;
    }

    this.noteInsert?.abort();
    const controller = new AbortController();
    this.noteInsert = controller;
    this.applyError.set(null);
    const track = this.selectedTrack();
    const harmony = this.phraseHarmony();
    const source = copied.harmony;
    const choices = source && harmony && (source.scale !== harmony.scale || (source.tonic - harmony.tonic) % 12 !== 0)
      ? [scaleTranslation(copied.notes, source, harmony)] : [];
    const clip = { notes: [...copied.notes], durationBeats: copied.notes.reduce((end, note) => Math.max(end, note.startBeat + note.durationBeats), 0) };
    const phraseBeats = bounds.endBeat - startBeat;
    try {
      let notes: PhraseNote[] | null;
      if (choices.length || clip.durationBeats > phraseBeats) {
        const { showNoteInsertOptions } = await import('../../core/ui/midi-file-import');
        if (this.dialogs.openDialogs.length) return;
        notes = await showNoteInsertOptions(this.injector, controller.signal, { track: clip, phraseBeats, choices, paste: true });
      } else {
        notes = fitImportedMidi(clip, phraseBeats);
      }
      if (controller.signal.aborted || !notes || this.recorder()?.running()) return;
      const trackIds = new Set(this.editedTracks().map(candidate => candidate.id));
      const sourceTracks = new Map(copied.notes.map(note => [note.id, note.trackId]));
      const counts = new Map<string, number>();
      for (const note of this.draftNotes()) counts.set(note.trackId, (counts.get(note.trackId) ?? 0) + 1);
      const added: PhraseDraftNote[] = [];
      const transpose = this.displayTranspose();
      for (const note of notes) {
        const sourceTrackId = sourceTracks.get(note.id);
        const destination = track.role === 'drums' && source === null
          ? (sourceTrackId && trackIds.has(sourceTrackId) ? sourceTrackId : this.drawnDrumTrackForPitch(note.midi)?.id ?? track.id)
          : track.id;
        const count = counts.get(destination) ?? 0;
        if (count >= 2048) continue;
        counts.set(destination, count + 1);
        added.push({ ...note, id: this.makeNoteId(), trackId: destination,
          startBeat: startBeat + note.startBeat, midi: Math.max(0, Math.min(127, note.midi - transpose)) });
      }
      if (!added.length) {
        this.applyError.set('The destination tracks already have 2048 notes. Remove some notes before pasting.');
        return;
      }
      this.insertNotes(added);
    } catch (error) {
      if (!controller.signal.aborted) this.applyError.set(error instanceof Error ? error.message : 'Could not paste notes.');
    } finally {
      if (this.noteInsert === controller) this.noteInsert = null;
    }
  }

  private insertNotes(added: PhraseDraftNote[], discard = false): void {
    const current = this.draftNotes();
    this.draftNotes.set(discard ? added : [...current, ...added]);
    const affected = discard ? [...current, ...added] : added;
    this.markManual([...new Set(affected.map(note => note.trackId))]);
    this.selectNotes(added.map(note => note.id));
    this.record();
  }

  transposePhrase(value: number): void {
    if (!Number.isInteger(value) || value < -36 || value > 36 || value === this.freeTranspose()) return;
    const delta = value - this.freeTranspose();
    this.draftNotes.update(notes => notes.map(note => ({ ...note, midi: Math.max(0, Math.min(127, note.midi + delta)) })));
    this.freeTranspose.set(value);
    this.markManual(this.editedTracks().map(track => track.id));
    this.record();
  }

  ngOnDestroy(): void { this.recorder()?.stop(); this.finishRecording(); this.noteInsert?.abort(); }
  readonly selectedNoteIds = signal<readonly string[]>([]);
  readonly selectedNoteId = computed(() => this.selectedNoteIds()[0] ?? null);
  readonly snapBeats = signal(0.25);
  readonly harmonySnappingOptions = [
    { value: 'off', label: 'Harmony snapping off' },
    { value: 'global', label: 'Snap to global harmony' },
    { value: 'section', label: 'Snap to section harmony' },
  ] as const;
  readonly harmonySnapping = signal('global');
  setHarmonySnapping(value: string): void {
    if (this.harmonySnappingOptions.some(option => option.value === value)) this.harmonySnapping.set(value);
  }
  readonly snapToScale = computed(() => this.harmonySnapping() !== 'off' && this.selectedPart() !== 'drums');
  private readonly snappingHarmony = computed(() => {
    const project = this.project();
    const section = this.harmonySnapping() === 'section' ? this.section() : undefined;
    return { scale: section?.context?.scale ?? project.scale,
      tonic: getPitchClass(BASE_KEY)! + (project.transposeSemitones ?? 0) + sectionTranspose(section, this.selectedPart()) };
  });
  readonly scalePitchClasses = computed(() => {
    const { scale, tonic } = this.snappingHarmony();
    const transpose = tonic - getPitchClass(BASE_KEY)!;
    return getDiatonicChords(BASE_KEY, scale).map(chord => ((getPitchClass(chord.root)! + transpose) % 12 + 12) % 12);
  });
  readonly hasChanges = computed(() => {
    this.historyRevision();
    return this.changedTracks().length > 0 || this.changedGlides().length > 0;
  });
  readonly changedTracks = computed(() => {
    this.historyRevision();
    return this.editedTracks().filter(track => this.restoredTrackIds().includes(track.id)
      || !phraseNotesEqual(this.draftNotes().filter(note => note.trackId === track.id), this.baselineNotes.filter(note => note.trackId === track.id)));
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
  get placementDuration(): number { return this.lastDuration; }
  readonly phraseBeats = computed(() => {
    const project = this.projects.projectWithoutVolumeChanges();
    const section = project.arrangement.find(section => section.id === this.sectionId());
    return this.phraseLength() ?? (section ? sectionDurationBeats(project, section) : project.progression.length * 4);
  });
  readonly editBounds = computed(() => {
    const project = this.projects.projectWithoutVolumeChanges();
    const section = project.arrangement.find(section => section.id === this.sectionId());
    if (!section) return { startBeat: -4, endBeat: this.phraseBeats() + 4 };
    const bounds = sectionPhraseBounds(project, section);
    const sectionEnd = sectionDurationBeats(project, section);
    return { startBeat: this.phraseStartBeat() === 0 ? bounds.startBeat : 0,
      endBeat: this.phraseBeats() + (this.phraseStartBeat() + this.phraseBeats() >= sectionEnd ? bounds.endBeat - sectionEnd : 0) };
  });
  readonly draftNotes = signal<PhraseDraftNote[]>([]);
  readonly draftReady = signal(false);
  readonly laneOptions = [
    { value: 'velocity', label: 'Note velocity' },
    { value: 'pitch', label: 'Pitch glide' },
    { value: 'volume', label: 'Volume glide' },
  ] as const;
  readonly lane = signal('velocity');
  readonly glideLane = computed<GlideLane | null>(() => {
    const lane = this.lane();
    return lane === 'pitch' || lane === 'volume' ? lane : null;
  });
  readonly glideTrack = signal<CompositionTrack | null>(null);
  readonly canDrawGlides = computed(() => this.draftReady() && !this.recorder()?.running());
  readonly draftGlides = signal<TrackGlides>({});
  private baselineGlides: TrackGlides = {};
  readonly glideCurves = computed(() => {
    const track = this.glideTrack();
    return track ? this.draftGlides()[track.id] ?? EMPTY_PHRASE_GLIDES : EMPTY_PHRASE_GLIDES;
  });
  readonly laneSegments = computed(() => {
    const lane = this.glideLane();
    return lane ? this.glideCurves()[lane] : [];
  });
  readonly changedGlides = computed(() => {
    const track = this.glideTrack();
    if (!track) return [];
    const draft = this.draftGlides()[track.id];
    if (!draft) return [];
    const baseline = this.baselineGlides[track.id] ?? EMPTY_PHRASE_GLIDES;
    return GLIDE_LANES.flatMap(lane => glidesEqual(baseline[lane], draft[lane]) ? []
      : [{ trackId: track.id, lane, segments: draft[lane] }]);
  });

  setLane(value: string): void {
    if (this.laneOptions.some(option => option.value === value)) this.lane.set(value);
  }

  drawGlide(segments: readonly GlideSegment[]): void {
    const lane = this.glideLane();
    if (!lane || !segments.length) return;
    const neutral = glideNeutral(lane);
    const replacement = segments.filter(segment => segment.startValue !== neutral || segment.endValue !== neutral);
    this.setGlideSegments(lane, replaceGlideSegments(this.laneSegments(), segments[0].startBeat, segments.at(-1)!.endBeat, replacement));
  }

  addGlideRamp(up: boolean): void {
    const lane = this.glideLane();
    if (!lane) return;
    const selected = this.notes().filter(note => this.selectedNoteIds().includes(note.id));
    const bounds = this.editBounds();
    const startBeat = selected.length ? Math.max(bounds.startBeat, Math.min(...selected.map(note => note.startBeat))) : 0;
    const endBeat = selected.length ? Math.min(bounds.endBeat, Math.max(...selected.map(note => note.startBeat + note.durationBeats))) : this.phraseBeats();
    if (endBeat <= startBeat) return;
    this.drawGlide([{ startBeat, endBeat,
      startValue: lane === 'pitch' || up ? 0 : 1,
      endValue: lane === 'pitch' ? (up ? PITCH_GLIDE_SEMITONES : -PITCH_GLIDE_SEMITONES) : up ? 1 : 0,
    }]);
  }

  clearGlideLane(): void {
    const lane = this.glideLane();
    if (lane) this.setGlideSegments(lane, []);
  }

  private setGlideSegments(lane: GlideLane, segments: readonly GlideSegment[]): void {
    const track = this.glideTrack();
    if (!track || !this.canDrawGlides()) return;
    const current = this.draftGlides()[track.id];
    if (glidesEqual(current[lane], segments)) return;
    this.draftGlides.update(draft => ({ ...draft, [track.id]: { ...current, [lane]: segments } }));
    this.record();
  }

  // Draft history stores pitches before section and project transpose.
  private readonly localTranspose = computed(() => sectionTranspose(this.section(), this.selectedPart()));
  private readonly globalTranspose = computed(() => this.selectedPart() === 'drums' ? 0 : this.project().transposeSemitones ?? 0);
  readonly displayTranspose = computed(() => this.localTranspose() + this.globalTranspose());
  private displayMidi(midi: number): number {
    const sectionMidi = Math.max(0, Math.min(127, midi + this.localTranspose()));
    return Math.max(0, Math.min(127, sectionMidi + this.globalTranspose()));
  }
  readonly notes = computed(() => this.draftNotes().map(note => ({ ...note,
    midi: this.displayMidi(note.midi),
  })).sort((left, right) => left.startBeat - right.startBeat || left.midi - right.midi));
  readonly displayNotes = this.notes;
  readonly selectedNote = computed(() => this.displayNotes().find((note) => note.id === this.selectedNoteId()) ?? null);
  readonly isEdited = computed(() => hasManualPhrase(this.selectedTrack())
    || this.project().arrangement.find(section => section.id === this.sectionId())?.trackPhrases?.[this.selectedTrack().id] !== undefined);
  readonly noteCount = computed(() => this.notes().length);
  private newNoteId = 0;
  private baselineNotes: readonly PhraseDraftNote[] = [];
  private readonly restoredTrackIds = signal<readonly string[]>([]);

  ngOnInit(): void {
    this.loadTrackDraft(this.selectedTrack());
    if (this.initialNoteId()) this.selectNote(this.initialNoteId()!);
    this.history.reset({ notes: this.draftNotes(), glides: this.draftGlides(), selection: this.selectedNoteIds(), restore: false });
    this.draftReady.set(true);
  }

  selectTrack(trackId: string): void {
    this.noteInsert?.abort();
    this.recorder()?.stop();
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
  undo(): void { this.recorder()?.stop(); this.loadHistory(this.history.undo()); }
  redo(): void { this.recorder()?.stop(); this.loadHistory(this.history.redo()); }
  private loadHistory(snapshot?: PhraseEditSnapshot<PhraseDraftNote>): void {
    if (!snapshot) return;
    this.draftNotes.set([...snapshot.notes]);
    this.draftGlides.set(snapshot.glides ?? this.baselineGlides);
    this.selectNotes(snapshot.selection);
    this.restoredTrackIds.set(snapshot.restoredTrackIds ?? []);
    this.freeTranspose.set(snapshot.phraseTranspose ?? 0);
    this.historyRevision.update(value => value + 1);
    this.applyError.set(null);
  }
  private record(): void {
    if (this.recordingActive) return;
    this.history.record({ notes: this.draftNotes(), glides: this.draftGlides(), selection: this.selectedNoteIds(), restore: this.restoredTrackIds().length > 0, restoredTrackIds: this.restoredTrackIds(), phraseTranspose: this.freeTranspose() });
    this.historyRevision.update(value => value + 1);
    this.applyError.set(null);
  }
  @HostListener('document:keydown', ['$event'])
  keydown(event: KeyboardEvent): void {
    if (event.defaultPrevented || event.isComposing || isEditingText(event)) return;
    if (this.preferences.matchesShortcut('copyNotes', event) && this.selectedNoteIds().length && !this.dialogs.openDialogs.length) this.copyNotes();
    else if (this.preferences.matchesShortcut('pasteNotes', event) && this.canPasteNotes() && !this.dialogs.openDialogs.length) {
      if (!event.repeat) void this.pasteNotes();
    }
    else if (this.preferences.matchesShortcut('selectAllNotes', event)) this.selectAll();
    else if (this.preferences.matchesShortcut('transposeNotes', event)) this.openTranspose();
    else if (this.preferences.matchesShortcut('deleteNotes', event)) this.deleteSelected();
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
  setGrid(selection: string): void { this.snapBeats.set(Number(selection)); }
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
    this.setDisplayNotes(next);
    this.record();
  }
  replaceNotes(notes: readonly PhraseNote[], duplicate = false): void {
    if (!notes.length) return;
    if (duplicate && this.editedTracks().some(track => this.notes().filter(note => note.trackId === track.id).length
      + notes.filter(note => this.notes().find(existing => existing.id === note.id)?.trackId === track.id).length > 2048)) return;
    if (duplicate) {
      const copies = notes.map(note => ({ ...note, id: this.makeNoteId(), trackId: this.notes().find(existing => existing.id === note.id)?.trackId ?? this.selectedTrack().id }));
      this.setDisplayNotes([...this.notes(), ...copies]);
      this.selectNotes(copies.map(note => note.id));
    } else {
      const updates = new Map(notes.map(note => [note.id, note]));
      this.setDisplayNotes(this.notes().map(note => {
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
    if (this.selectedPart() === 'drums') return this.midi.drumLabel(midi, this.project().drumSlots);
    const names = ['C', 'C♯', 'D', 'D♯', 'E', 'F', 'F♯', 'G', 'G♯', 'A', 'A♯', 'B'];
    return `${names[midi % 12]}${Math.floor(midi / 12) - 1}`;
  }

  updateSelected(field: 'midi' | 'startBeat' | 'durationBeats' | 'velocity', event: Event): void {
    const selected = this.selectedNote();
    const inputValue = Number((event.target as HTMLInputElement).value);
    const value = field === 'startBeat' ? inputValue - 1 : field === 'midi' && Number.isInteger(inputValue) && inputValue >= 0 && inputValue <= 127 ? this.snapPitch(inputValue) : inputValue;
    if (!selected || !Number.isFinite(value)) return;

    const valid = field === 'midi' ? Number.isInteger(value) && value >= 0 && value <= 127
      : field === 'velocity' ? Number.isInteger(value) && value >= 1 && value <= 127
        : field === 'startBeat' ? value >= this.editBounds().startBeat && value + selected.durationBeats <= this.editBounds().endBeat
          : value > 0 && selected.startBeat + value <= this.editBounds().endBeat;
    if (!valid) {
      (event.target as HTMLInputElement).value = String(field === 'startBeat' ? selected.startBeat + 1 : selected[field]);
      return;
    }
    if (field === 'midi') (event.target as HTMLInputElement).value = String(value);
    const next = this.notes().map((note) => note.id === selected.id ? { ...note, [field]: value } : note);
    this.setDisplayNotes(next);
    this.markManual([selected.trackId]);
    this.record();
  }

  deleteSelected(): void {
    const ids = this.selectedNoteIds();
    if (!ids.length) return;
    this.markManual(this.notes().filter(note => ids.includes(note.id)).map(note => note.trackId));
    this.setDisplayNotes(this.notes().filter(note => !ids.includes(note.id)));
    this.selectedNoteIds.set([]);
    this.record();
  }

  restoreGenerated(): void {
    if (!this.generationAvailable()) return;
    const track = this.selectedTrack();
    const section = this.section();
    const usedIds = new Set(this.notes().filter(note => note.trackId !== track.id).map(note => note.id));
    const source = section ? this.sectionDraftNotes(withoutManualAdjustments(this.project()), section.id, track.id, true)
      .map(note => ({ ...note, startBeat: note.startBeat - this.phraseStartBeat() })) : generateTrackPhrase(this.project(), track);
    const notes = source.filter(note => note.startBeat >= this.editBounds().startBeat && note.startBeat < this.editBounds().endBeat)
      .map(note => {
        const id = usedIds.has(note.id) ? this.makeNoteId() : note.id; usedIds.add(id);
        return { ...note, id, trackId: track.id };
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
      ?? this.drawnDrumTrackForPitch(position.midi) ?? this.selectedTrack();
    if (this.notes().filter(note => note.trackId === track.id).length >= 2048 || position.startBeat < this.editBounds().startBeat || position.startBeat >= this.editBounds().endBeat) return;
    const note = { id: this.makeNoteId(), trackId: track.id, midi: this.snapPitch(position.midi), startBeat: position.startBeat,
      durationBeats: Math.min(position.durationBeats ?? this.lastDuration, this.editBounds().endBeat - position.startBeat), velocity: position.velocity ?? 90 };
    this.setDisplayNotes([...this.notes(), note]);
    this.selectNote(note.id);
    this.markManual([track.id]);
    this.record();
  }

  smartExit(): void {
    this.recorder()?.stop();
    if (!this.hasChanges()) { this.cancelChanges(); return; }
    this.commitChanges('smart');
  }
  applyLocalChanges(): void { if (this.sectionId()) this.commitChanges('local'); }
  applyChanges(): void { this.commitChanges('global'); }
  private phraseRange(): Omit<PhraseRangeEdit, 'notes'> {
    const startBeat = this.section() ? this.phraseStartBeat() : 0;
    const bounds = this.editBounds();
    return { startBeat, endBeat: startBeat + this.phraseBeats(),
      ...(bounds.startBeat < 0 ? { pickupBeats: -bounds.startBeat } : {}),
      ...(bounds.endBeat > this.phraseBeats() ? { tailBeats: bounds.endBeat - this.phraseBeats() } : {}),
    };
  }
  private commitChanges(destination: 'local' | 'global' | 'smart'): void {
    this.recorder()?.stop();
    if (!this.hasChanges()) return;
    const section = this.project().arrangement.find(section => section.id === this.sectionId());
    const sourceProject = withoutManualAdjustments(this.project());
    const range = this.phraseRange();
    const edits = this.changedTracks().map(track => {
      const local = destination === 'local' || destination === 'smart' && section?.trackPhrases?.[track.id] !== undefined;
      const mode = !local && track.role !== 'drums' ? section?.manualPhraseMode : undefined;
      const draft = this.draftNotes().filter(note => note.trackId === track.id);
      if (!local && this.restoredTrackIds().includes(track.id)) return { trackId: track.id, notes: null };
      const source = local && section ? this.sectionDraftNotes(sourceProject, section.id, track.id) : [];
      const start = this.phraseStartBeat() + this.editBounds().startBeat;
      const end = this.phraseStartBeat() + this.editBounds().endBeat;
      const preserved = source.filter(note => note.startBeat < start || note.startBeat >= end);
      const savedDraft = mode ? convertManualPhrase(draft, mode.to, mode.from) : draft;
      const changed = savedDraft.map(note => ({ ...note, startBeat: note.startBeat + (section ? this.phraseStartBeat() : 0) }));
      const ids = new Set<string>();
      const notes = [...preserved, ...changed].map(note => {
        const id = ids.has(note.id) ? this.makeNoteId() : note.id; ids.add(id);
        return { id, midi: note.midi, startBeat: note.startBeat,
          durationBeats: note.durationBeats, velocity: note.velocity };
      });
      return { trackId: track.id, ...(local && section ? { sectionId: section.id } : {}), notes,
        ...(!local ? { range } : {}) };
    });
    const glides = this.changedGlides().map(({ trackId, lane, segments }) => {
      const scoped = Object.hasOwn(section?.trackGlides ?? {}, trackId) ? section!.trackGlides![trackId] : undefined;
      const local = destination === 'local' || destination === 'smart' && (section?.trackPhrases?.[trackId] !== undefined
        || scoped?.some(edit => edit.lane === lane));
      return { trackId, sectionId: local ? section?.id : undefined, edit: { ...range, lane,
        segments: clipGlideSegments(shiftGlideSegments(segments, range.startBeat),
          range.startBeat - (range.pickupBeats ?? 0), range.endBeat + (range.tailBeats ?? 0)),
      } };
    });
    if (!this.projects.applyPhraseEdits(edits, glides)) {
      this.applyError.set(glides.length ? 'Cannot save these phrase edits. Check note timings and simplify the glide curves.'
        : `Cannot save these phrases. Check the ${MAX_DRUM_SOUNDS} distinct drum sounds per project, note timings, and the 2048-note limit per track.`);
      return;
    }
    this.applied.emit();
  }

  cancelChanges(): void { this.cancelled.emit(); }

  private setDisplayNotes(notes: readonly PhraseDraftNote[]): void {
    const transpose = this.displayTranspose();
    const originals = new Map(this.draftNotes().map(note => [note.id, note]));
    this.draftNotes.set(notes.map(note => {
      const original = originals.get(note.id);
      // Keep underlying pitches when only timing/velocity changes, including clipped notes.
      const unchangedPitch = original && note.midi === this.displayMidi(original.midi);
      return { ...note, midi: unchangedPitch ? original.midi : Math.max(0, Math.min(127, note.midi - transpose)) };
    }));
  }

  private makeNoteId(): string {
    this.newNoteId++;
    return `edit-${Date.now()}-${this.newNoteId}`;
  }

  private sectionDraftNotes(project: CompositionProject, sectionId: string, trackId: string, generatedOnly = false): PhraseNote[] {
    const section = project.arrangement.find(candidate => candidate.id === sectionId)!;
    const context = section.context?.keyChange === undefined ? section.context : { ...section.context, keyChange: 0 };
    const track = project.tracks.find(candidate => candidate.id === trackId)!;
    const trackPhrases = { ...section.trackPhrases };
    if (generatedOnly) delete trackPhrases[trackId];
    return layOutTrackPhrase({ ...project, transposeSemitones: 0,
      arrangement: [{ ...section, context, trackPhrases, trackPresence: { ...section.trackPresence, [trackId]: true },
        generatedTrackIds: generatedOnly ? [...(section.generatedTrackIds ?? []), trackId] : section.generatedTrackIds }] }, track);
  }

  private loadTrackDraft(track: CompositionTrack): void {
    this.freeTranspose.set(0);
    this.midiDragOver.set(false);
    const section = this.project().arrangement.find(section => section.id === this.sectionId());
    const tracks = track.role === 'drums' ? [track, ...this.project().tracks.filter(candidate => candidate.role === 'drums' && candidate.id !== track.id)] : [track];
    const ids = new Set<string>();
    const sourceProject = withoutManualAdjustments(this.project());
    this.baselineNotes = tracks.flatMap(track => {
      const source = section ? this.sectionDraftNotes(sourceProject, section.id, track.id)
        .filter(note => note.startBeat >= this.phraseStartBeat() + this.editBounds().startBeat
          && note.startBeat < this.phraseStartBeat() + this.editBounds().endBeat)
        .map(note => ({ ...note, startBeat: note.startBeat - this.phraseStartBeat() }))
        : phraseForTrack(sourceProject, sourceProject.tracks.find(candidate => candidate.id === track.id)!);
      return source.map(note => {
        const id = ids.has(note.id) ? this.makeNoteId() : note.id; ids.add(id);
        return { ...note, id, trackId: track.id };
      });
    });
    this.draftNotes.set([...this.baselineNotes]);
    this.loadGlideDraft(tracks, section);
    this.restoredTrackIds.set([]);
    this.selectedNoteIds.set([]);
    this.history.reset({ notes: this.draftNotes(), glides: this.draftGlides(), selection: [], restore: false });
    this.historyRevision.update(value => value + 1);
  }

  private loadGlideDraft(tracks: readonly CompositionTrack[], section: ReturnType<typeof this.section>): void {
    const start = this.phraseStartBeat(), bounds = this.editBounds();
    const end = section ? sectionDurationBeats(this.project(), section) : this.phraseBeats();
    const phraseBounds = section ? sectionPhraseBounds(this.project(), section) : bounds;
    const local = section?.trackGlides ?? {};
    this.baselineGlides = Object.fromEntries(tracks.map(track => {
      const glides = applyPhraseGlideEdits(track.phraseGlides, Object.hasOwn(local, track.id) ? local[track.id] : undefined, end, phraseBounds);
      const inDraft = (lane: GlideLane) => shiftGlideSegments(clipGlideSegments(glides[lane], start + bounds.startBeat, start + bounds.endBeat), -start);
      return [track.id, { pitch: inDraft('pitch'), volume: inDraft('volume') }];
    }));
    // The grouped drum editor keeps one channel lane when a different drum note is selected.
    const owner = tracks[0].role === 'drums' ? this.project().tracks.find(track => track.role === 'drums'
      && (track.phraseGlides?.length || Object.hasOwn(local, track.id))) ?? this.project().tracks.find(track => track.role === 'drums') : tracks[0];
    this.glideTrack.set(owner ?? null);
    this.draftGlides.set(this.baselineGlides);
  }
}
