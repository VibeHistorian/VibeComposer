import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import type {
  ArpeggioPattern, ArpeggioRate, ArpeggioSettings, ArrangedPart, ArrangementSection, BassRhythm, BassSettings,
  ChordRhythm, ChordSettings, ChordVoicing, CompositionTrack, DrumGroove, DrumSettings, PhraseNote,
} from '../core/project/project.model';
import { ARRANGED_PARTS } from '../core/project/project.model';
import { AudioPlaybackService } from '../core/audio/audio-playback.service';
import { layOutTrackPhrase, phraseForTrack } from '../core/music/phrase';
import { shouldGenerateTrackInSection } from '../core/music/arrangement-generator';
import { ProjectService } from '../core/project/project.service';
import { WorkspaceUiService } from './workspace-ui.service';

type TrackRow = CompositionTrack & {
  readonly color: ArrangedPart;
  readonly noteCount: number;
};

interface TimelineSection {
  readonly section: ArrangementSection;
  readonly startBeat: number;
  readonly startMeasure: number;
  readonly widthPercent: number;
}

interface ScoreNote extends PhraseNote {
  readonly part: string;
  readonly color: ArrangedPart;
  readonly topPercent: number;
  readonly opacity: number;
}

const ROLE_NAMES: Readonly<Record<ArrangedPart, string>> = {
  bass: 'Bass', chords: 'Chords', arpeggio: 'Arpeggio', drums: 'Drums',
};

const INSTRUMENTS: ReadonlyArray<{ program: number; name: string }> = [
  { program: 0, name: 'Acoustic Piano' }, { program: 4, name: 'Electric Piano' },
  { program: 10, name: 'Music Box' }, { program: 11, name: 'Vibraphone' },
  { program: 24, name: 'Nylon Guitar' }, { program: 25, name: 'Steel Guitar' },
  { program: 32, name: 'Acoustic Bass' }, { program: 33, name: 'Electric Bass' },
  { program: 38, name: 'Synth Bass' }, { program: 40, name: 'Violin' },
  { program: 48, name: 'Strings' }, { program: 56, name: 'Trumpet' },
  { program: 65, name: 'Alto Sax' }, { program: 73, name: 'Flute' },
  { program: 80, name: 'Square Lead' }, { program: 88, name: 'Warm Pad' },
];

@Component({
  selector: 'vc-workspace-canvas',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './workspace-canvas.component.html',
  styleUrl: './workspace-canvas.component.css',
})
export class WorkspaceCanvasComponent {
  readonly projects = inject(ProjectService);
  readonly playback = inject(AudioPlaybackService);
  readonly workspaceUi = inject(WorkspaceUiService);
  readonly project = this.projects.project;
  readonly instruments = INSTRUMENTS;
  readonly midiChannels = Array.from({ length: 16 }, (_, index) => index + 1);
  readonly roles = ARRANGED_PARTS.map((role) => ({ role, name: ROLE_NAMES[role] }));
  readonly bassRhythms: ReadonlyArray<{ value: BassRhythm; label: string }> = [
    { value: 'alternating', label: 'Alternating' }, { value: 'full', label: 'Full' },
    { value: 'half', label: 'Half time' }, { value: 'tresillo', label: 'Tresillo' }, { value: 'sparse', label: 'Sparse' },
  ];
  readonly chordRhythms: ReadonlyArray<{ value: ChordRhythm; label: string }> = [
    { value: 'full', label: 'Full' }, { value: 'half', label: 'Half time' },
    { value: 'tresillo', label: 'Tresillo' }, { value: 'sparse', label: 'Sparse' }, { value: 'single', label: 'Single hit' },
  ];
  readonly arpPatterns: ReadonlyArray<{ value: ArpeggioPattern; label: string }> = [
    { value: 'up', label: 'Up' }, { value: 'down', label: 'Down' },
    { value: 'up-down', label: 'Up and down' }, { value: 'random', label: 'Random' },
  ];
  readonly drumGrooves: ReadonlyArray<{ value: DrumGroove; label: string }> = [
    { value: 'rock', label: 'Rock' }, { value: 'four-on-floor', label: 'Four on floor' },
    { value: 'half-time', label: 'Half time' }, { value: 'sparse', label: 'Sparse' },
  ];
  readonly selectedTrackId = this.workspaceUi.selectedTrackId;
  readonly selectedSectionId = signal<string | null>(null);
  readonly hiddenTracks = signal(new Set<string>());
  readonly collapsedTracks = signal(new Set<string>());

  readonly totalMeasures = computed(() => this.project().arrangement.reduce((sum, section) => sum + section.measures, 0));
  readonly totalBeats = computed(() => this.totalMeasures() * 4);
  readonly playheadPercent = computed(() => this.totalBeats() > 0
    ? Math.min(100, Math.max(0, this.playback.beat() / this.totalBeats() * 100)) : 0);
  readonly timelineSections = computed<TimelineSection[]>(() => {
    let beat = 0;
    let measure = 1;
    const measures = this.totalMeasures();
    return this.project().arrangement.map((section) => {
      const item = { section, startBeat: beat, startMeasure: measure, widthPercent: section.measures / measures * 100 };
      beat += section.measures * 4;
      measure += section.measures;
      return item;
    });
  });
  readonly tracks = computed<TrackRow[]>(() => this.project().tracks.map((track) => ({
    ...track,
    color: track.role,
    noteCount: phraseForTrack(this.project(), track).length,
  })));
  readonly selectedTrack = computed<TrackRow>(() => this.tracks().find((track) => track.id === this.selectedTrackId()) ?? this.tracks()[0]);
  readonly selectedPart = computed(() => this.selectedTrack().role);
  readonly hasChannelCollision = computed(() => this.tracks().some((track) => track.id !== this.selectedTrack().id
    && track.midiChannel === this.selectedTrack().midiChannel));
  readonly selectedSection = computed(() => this.project().arrangement.find((section) => section.id === this.selectedSectionId()) ?? null);
  readonly scoreNotes = computed<ScoreNote[]>(() => this.tracks().flatMap((track) => {
    const notes = layOutTrackPhrase(this.project(), track);
    if (notes.length === 0) return [];
    const minPitch = Math.min(...notes.map((note) => note.midi));
    const maxPitch = Math.max(...notes.map((note) => note.midi));
    const pitchSpan = Math.max(12, maxPitch - minPitch);
    return notes.map((note) => ({
      ...note,
      part: track.id,
      color: track.role,
      topPercent: (maxPitch - note.midi) / pitchSpan * 86 + 3,
      opacity: 0.3 + note.velocity / 127 * 0.7,
    }));
  }));

  tracksFor(role: ArrangedPart): TrackRow[] { return this.tracks().filter((track) => track.role === role); }

  selectTrack(trackId: string): void {
    if (!this.project().tracks.some((track) => track.id === trackId)) return;
    this.workspaceUi.selectTrack(trackId);
    this.selectedSectionId.set(null);
  }

  selectSection(section: ArrangementSection, trackId: string): void {
    this.selectedSectionId.set(section.id);
    this.workspaceUi.selectTrack(trackId);
  }

  addTrack(role: ArrangedPart): void {
    const trackId = this.projects.addTrack(role);
    if (trackId) this.selectTrack(trackId);
  }

  duplicateTrack(trackId: string): void {
    const duplicateId = this.projects.duplicateTrack(trackId);
    if (duplicateId) this.selectTrack(duplicateId);
  }

  removeTrack(trackId: string): void {
    const current = this.tracks();
    const index = current.findIndex((track) => track.id === trackId);
    if (index < 0 || current.filter((track) => track.role === current[index].role).length <= 1) return;
    this.projects.removeTrack(trackId);
    if (this.selectedTrackId() === trackId) this.selectTrack(this.project().tracks[Math.max(0, index - 1)].id);
  }

  canRemoveTrack(trackId: string): boolean {
    const track = this.project().tracks.find((candidate) => candidate.id === trackId);
    return !!track && this.tracks().filter((candidate) => candidate.role === track.role).length > 1;
  }

  canMoveTrack(trackId: string, offset: -1 | 1): boolean {
    const index = this.project().tracks.findIndex((track) => track.id === trackId);
    return index + offset >= 0 && index + offset < this.project().tracks.length;
  }

  instrumentName(track: TrackRow): string {
    return track.role === 'drums' ? 'GM Percussion' : this.instruments.find((item) => item.program === track.mix.program)?.name ?? `GM ${track.mix.program + 1}`;
  }

  reorderTrack(trackId: string, offset: -1 | 1): void {
    this.projects.reorderTrack(trackId, offset);
  }

  toggleVisibility(trackId: string): void {
    const next = new Set(this.hiddenTracks());
    if (next.has(trackId)) next.delete(trackId);
    else next.add(trackId);
    this.hiddenTracks.set(next);
  }

  isVisible(trackId: string): boolean {
    return !this.hiddenTracks().has(trackId);
  }

  toggleCollapsed(trackId: string): void {
    const next = new Set(this.collapsedTracks());
    if (next.has(trackId)) next.delete(trackId);
    else next.add(trackId);
    this.collapsedTracks.set(next);
  }

  toggleMix(trackId: string, setting: 'muted' | 'solo'): void {
    const track = this.project().tracks.find((candidate) => candidate.id === trackId);
    if (!track) return;
    this.projects.updateTrack(trackId, { mix: { [setting]: !track.mix[setting] } });
    this.playback.updateMix(this.project());
  }

  sectionPlays(section: ArrangementSection, track: TrackRow): boolean {
    return shouldGenerateTrackInSection(BigInt(this.project().seed), section, track);
  }

  noteLeft(note: ScoreNote): number { return note.startBeat / this.totalBeats() * 100; }
  noteWidth(note: ScoreNote): number { return Math.max(0.12, note.durationBeats / this.totalBeats() * 100); }
  velocityHeight(note: ScoreNote): number { return Math.max(8, note.velocity / 127 * 100); }
  sectionLabel(type: string): string { return type.replaceAll('_', ' '); }

  updateBassRhythm(event: Event): void {
    const value = this.inputValue(event);
    if (this.bassRhythms.some((option) => option.value === value)) this.updateGenerator({ rhythm: value as BassRhythm });
  }
  updateBassVariation(event: Event): void {
    const value = Number(this.inputValue(event));
    if (Number.isInteger(value) && value >= 0 && value <= 100) this.updateGenerator({ noteVariation: value });
  }
  updateBassOctave(event: Event): void {
    this.updateGenerator({ octaveInterval: (event.target as HTMLInputElement).checked });
  }
  updateChordRhythm(event: Event): void {
    const value = this.inputValue(event);
    if (this.chordRhythms.some((option) => option.value === value)) this.updateGenerator({ rhythm: value as ChordRhythm });
  }
  updateChordVoicing(event: Event): void {
    const value = this.inputValue(event);
    if (value === 'close' || value === 'open') this.updateGenerator({ voicing: value as ChordVoicing });
  }
  updateChordLength(event: Event): void {
    const value = Number(this.inputValue(event));
    if (Number.isInteger(value) && value >= 25 && value <= 125) this.updateGenerator({ noteLengthPercent: value });
  }
  updateArpeggioPattern(event: Event): void {
    const value = this.inputValue(event);
    if (this.arpPatterns.some((option) => option.value === value)) this.updateGenerator({ pattern: value as ArpeggioPattern });
  }
  updateArpeggioRate(event: Event): void {
    const value = this.inputValue(event);
    if (value === 'eighth' || value === 'sixteenth') this.updateGenerator({ rate: value as ArpeggioRate });
  }
  updateArpeggioOctaves(event: Event): void {
    const value = Number(this.inputValue(event));
    if (value === 1 || value === 2) this.updateGenerator({ octaves: value });
  }
  updateDrumGroove(event: Event): void {
    const value = this.inputValue(event);
    if (this.drumGrooves.some((option) => option.value === value)) this.updateGenerator({ groove: value as DrumGroove });
  }
  updateDrumSwing(event: Event): void {
    const value = Number(this.inputValue(event));
    if (Number.isInteger(value) && value >= 50 && value <= 75) this.updateGenerator({ swingPercent: value });
  }
  updateTrackName(event: Event): void {
    this.projects.updateTrack(this.selectedTrackId(), { name: this.inputValue(event) });
  }
  restoreTrackPhrase(): void {
    this.projects.clearTrackPhrase(this.selectedTrackId());
  }
  updateChannel(event: Event): void {
    const channel = Number(this.inputValue(event));
    if (Number.isInteger(channel)) this.projects.updateTrack(this.selectedTrackId(), { midiChannel: channel });
  }
  updateInstrument(event: Event): void {
    if (this.selectedPart() === 'drums') return;
    const program = Number(this.inputValue(event));
    if (this.instruments.some((instrument) => instrument.program === program)) {
      this.projects.updateTrack(this.selectedTrackId(), { mix: { program } });
      this.playback.updateMix(this.project());
    }
  }
  updateVolume(event: Event): void {
    const volumePercent = Number(this.inputValue(event));
    if (Number.isInteger(volumePercent) && volumePercent >= 0 && volumePercent <= 100) {
      this.projects.updateTrack(this.selectedTrackId(), { mix: { volumePercent } });
      this.playback.updateMix(this.project());
    }
  }
  updatePan(event: Event): void {
    const panPercent = Number(this.inputValue(event));
    if (Number.isInteger(panPercent) && panPercent >= -100 && panPercent <= 100) {
      this.projects.updateTrack(this.selectedTrackId(), { mix: { panPercent } });
      this.playback.updateMix(this.project());
    }
  }
  panName(value: number): string {
    if (value === 0) return 'Center';
    return `${Math.abs(value)}% ${value < 0 ? 'Left' : 'Right'}`;
  }

  private updateGenerator(patch: Partial<BassSettings | ChordSettings | ArpeggioSettings | DrumSettings>): void {
    this.projects.updateTrackGeneratorSettings(this.selectedTrackId(), patch);
  }

  private inputValue(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }
}
