import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import type {
  ArpeggioPattern, ArpeggioRate, ArrangedPart, ArrangementSection, BassRhythm, ChordRhythm, ChordVoicing,
  DrumGroove, PhraseNote,
} from '../core/project/project.model';
import { ARRANGED_PARTS } from '../core/project/project.model';
import { AudioPlaybackService } from '../core/audio/audio-playback.service';
import { layOutPhrase, phraseForProject } from '../core/music/phrase';
import { shouldGeneratePartInSection } from '../core/music/arrangement-generator';
import { ProjectService } from '../core/project/project.service';
import { WorkspaceUiService } from './workspace-ui.service';

interface TrackRow {
  readonly id: ArrangedPart;
  readonly name: string;
  readonly color: string;
  readonly midiChannel: number;
  readonly noteCount: number;
  readonly muted: boolean;
  readonly solo: boolean;
}

interface TimelineSection {
  readonly section: ArrangementSection;
  readonly startBeat: number;
  readonly startMeasure: number;
  readonly widthPercent: number;
}

interface ScoreNote extends PhraseNote {
  readonly part: ArrangedPart;
  readonly color: string;
  readonly top: number;
  readonly opacity: number;
}

const PARTS: ReadonlyArray<{ id: ArrangedPart; name: string; color: string; midiChannel: number }> = [
  { id: 'bass', name: 'Bass', color: 'bass', midiChannel: 1 },
  { id: 'chords', name: 'Chords', color: 'chords', midiChannel: 2 },
  { id: 'arpeggio', name: 'Arpeggio', color: 'arpeggio', midiChannel: 3 },
  { id: 'drums', name: 'Drums', color: 'drums', midiChannel: 10 },
];

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
  readonly selectedPart = signal<ArrangedPart>('bass');
  readonly selectedSectionId = signal<string | null>(null);
  readonly visibleParts = signal(new Set<ArrangedPart>(ARRANGED_PARTS));
  readonly collapsedParts = signal(new Set<ArrangedPart>());

  readonly totalMeasures = computed(() => this.project().arrangement.reduce((sum, section) => sum + section.measures, 0));
  readonly totalBeats = computed(() => this.totalMeasures() * 4);
  readonly playheadPercent = computed(() => this.totalBeats() > 0
    ? Math.min(100, Math.max(0, this.playback.beat() / this.totalBeats() * 100)) : 0);
  readonly timelineSections = computed<TimelineSection[]>(() => {
    let beat = 0;
    let measure = 1;
    const measures = this.totalMeasures();
    return this.project().arrangement.map((section) => {
      const item = {
        section,
        startBeat: beat,
        startMeasure: measure,
        widthPercent: section.measures / measures * 100,
      };
      beat += section.measures * 4;
      measure += section.measures;
      return item;
    });
  });
  readonly selectedSection = computed(() => this.project().arrangement.find((section) => section.id === this.selectedSectionId()) ?? null);
  readonly tracks = computed<TrackRow[]>(() => {
    const project = this.project();
    return PARTS.map((part) => ({
      ...part,
      noteCount: phraseForProject(project, part.id).length,
      muted: project.mix[part.id].muted,
      solo: project.mix[part.id].solo,
    }));
  });
  readonly scoreNotes = computed<ScoreNote[]>(() => {
    const project = this.project();
    const allNotes = PARTS.flatMap((part) => layOutPhrase(project, part.id, phraseForProject(project, part.id))
      .map((note) => ({ ...note, part: part.id, color: part.color })));
    if (allNotes.length === 0) return [];
    const minPitch = Math.min(...allNotes.map((note) => note.midi));
    const maxPitch = Math.max(...allNotes.map((note) => note.midi));
    const pitchSpan = Math.max(12, maxPitch - minPitch);
    return allNotes.map((note) => ({
      ...note,
      top: (maxPitch - note.midi) / pitchSpan * 160,
      opacity: 0.28 + note.velocity / 127 * 0.72,
    }));
  });
  readonly selectedTrack = computed(() => this.tracks().find((track) => track.id === this.selectedPart())!);

  selectTrack(part: ArrangedPart): void {
    this.selectedPart.set(part);
    this.selectedSectionId.set(null);
  }

  selectSection(section: ArrangementSection, part: ArrangedPart): void {
    this.selectedSectionId.set(section.id);
    this.selectedPart.set(part);
  }

  toggleVisibility(part: ArrangedPart): void {
    const next = new Set(this.visibleParts());
    if (next.has(part)) next.delete(part);
    else next.add(part);
    this.visibleParts.set(next);
  }

  toggleCollapsed(part: ArrangedPart): void {
    const next = new Set(this.collapsedParts());
    if (next.has(part)) next.delete(part);
    else next.add(part);
    this.collapsedParts.set(next);
  }

  toggleMix(part: ArrangedPart, setting: 'muted' | 'solo'): void {
    const current = this.project().mix[part][setting];
    this.projects.updateMixSettings(part, { [setting]: !current });
    this.playback.updateMix(this.project());
  }

  sectionPlays(section: ArrangementSection, part: ArrangedPart): boolean {
    return shouldGeneratePartInSection(BigInt(this.project().seed), section, part);
  }

  noteLeft(note: ScoreNote): number {
    return note.startBeat / this.totalBeats() * 100;
  }

  noteWidth(note: ScoreNote): number {
    return Math.max(0.12, note.durationBeats / this.totalBeats() * 100);
  }

  sectionLabel(type: string): string {
    return type.replaceAll('_', ' ');
  }

  updateBassRhythm(event: Event): void {
    const value = this.inputValue(event);
    if (this.bassRhythms.some((option) => option.value === value)) {
      this.projects.updateBassSettings({ rhythm: value as BassRhythm });
    }
  }

  updateBassVariation(event: Event): void {
    const value = Number(this.inputValue(event));
    if (Number.isInteger(value) && value >= 0 && value <= 100) this.projects.updateBassSettings({ noteVariation: value });
  }

  updateBassOctave(event: Event): void {
    this.projects.updateBassSettings({ octaveInterval: (event.target as HTMLInputElement).checked });
  }

  updateChordRhythm(event: Event): void {
    const value = this.inputValue(event);
    if (this.chordRhythms.some((option) => option.value === value)) {
      this.projects.updateChordSettings({ rhythm: value as ChordRhythm });
    }
  }

  updateChordVoicing(event: Event): void {
    const value = this.inputValue(event);
    if (value === 'close' || value === 'open') this.projects.updateChordSettings({ voicing: value as ChordVoicing });
  }

  updateChordLength(event: Event): void {
    const value = Number(this.inputValue(event));
    if (Number.isInteger(value) && value >= 25 && value <= 125) this.projects.updateChordSettings({ noteLengthPercent: value });
  }

  updateArpeggioPattern(event: Event): void {
    const value = this.inputValue(event);
    if (this.arpPatterns.some((option) => option.value === value)) {
      this.projects.updateArpeggioSettings({ pattern: value as ArpeggioPattern });
    }
  }

  updateArpeggioRate(event: Event): void {
    const value = this.inputValue(event);
    if (value === 'eighth' || value === 'sixteenth') this.projects.updateArpeggioSettings({ rate: value as ArpeggioRate });
  }

  updateArpeggioOctaves(event: Event): void {
    const value = Number(this.inputValue(event));
    if (value === 1 || value === 2) this.projects.updateArpeggioSettings({ octaves: value });
  }

  updateDrumGroove(event: Event): void {
    const value = this.inputValue(event);
    if (this.drumGrooves.some((option) => option.value === value)) {
      this.projects.updateDrumSettings({ groove: value as DrumGroove });
    }
  }

  updateDrumSwing(event: Event): void {
    const value = Number(this.inputValue(event));
    if (Number.isInteger(value) && value >= 50 && value <= 75) this.projects.updateDrumSettings({ swingPercent: value });
  }

  updateInstrument(event: Event): void {
    if (this.selectedPart() === 'drums') return;
    const program = Number(this.inputValue(event));
    if (this.instruments.some((instrument) => instrument.program === program)) {
      this.projects.updateMixSettings(this.selectedPart(), { program });
      this.playback.updateMix(this.project());
    }
  }

  updateVolume(event: Event): void {
    const volumePercent = Number(this.inputValue(event));
    if (Number.isInteger(volumePercent) && volumePercent >= 0 && volumePercent <= 100) {
      this.projects.updateMixSettings(this.selectedPart(), { volumePercent });
      this.playback.updateMix(this.project());
    }
  }

  updatePan(event: Event): void {
    const panPercent = Number(this.inputValue(event));
    if (Number.isInteger(panPercent) && panPercent >= -100 && panPercent <= 100) {
      this.projects.updateMixSettings(this.selectedPart(), { panPercent });
      this.playback.updateMix(this.project());
    }
  }

  panName(value: number): string {
    if (value === 0) return 'Center';
    return `${Math.abs(value)}% ${value < 0 ? 'Left' : 'Right'}`;
  }

  private inputValue(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }
}
