import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, computed, effect, input, output, viewChild } from '@angular/core';
import type { ArrangedPart, ArrangementSection, CompositionProject, CompositionTrack } from '../core/project/project.model';
import { ARRANGED_PARTS } from '../core/project/project.model';
import { shouldGenerateTrackInSection } from '../core/music/arrangement-generator';
import { layOutTrackPhrase } from '../core/music/phrase';

interface OverviewSection {
  readonly section: ArrangementSection;
  readonly startBeat: number;
  readonly startMeasure: number;
}

interface InstrumentGroup {
  readonly role: ArrangedPart;
  readonly name: string;
  readonly tracks: readonly CompositionTrack[];
}

interface PreviewNote {
  readonly leftPercent: number;
  readonly widthPercent: number;
  readonly topPercent: number;
  readonly opacity: number;
}

interface OverviewHitArea {
  readonly key: string;
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
  readonly label: string;
  readonly activate: () => void;
}

const RULER_HEIGHT = 38;
const ROLE_COLORS: Readonly<Record<ArrangedPart, string>> = {
  bass: 'bass', chords: 'chords', arpeggio: 'arpeggio', drums: 'drum',
};

const ROLE_NAMES: Readonly<Record<ArrangedPart, string>> = {
  bass: 'Bass', chords: 'Chords', arpeggio: 'Arp', drums: 'Drums',
};

@Component({
  selector: 'vc-arrangement-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './arrangement-overview.component.html',
  styleUrl: './arrangement-overview.component.css',
})
export class ArrangementOverviewComponent implements AfterViewInit, OnDestroy {
  readonly project = input.required<CompositionProject>();
  readonly selectedSectionId = input<string | null>(null);
  readonly selectedTrackId = input('');
  readonly totalMeasures = input(0);
  readonly playheadPercent = input(0);
  readonly mixerRequested = output<void>();
  readonly randomizeRequested = output<void>();
  readonly sectionSelected = output<{ readonly sectionId: string; readonly trackId: string }>();
  readonly groupSelected = output<ArrangedPart>();
  readonly partToggled = output<{ readonly sectionId: string; readonly trackId: string; readonly present: boolean }>();

  readonly sections = computed<OverviewSection[]>(() => {
    const project = this.project();
    let startBeat = 0;
    let startMeasure = 1;
    return project.arrangement.map((section) => {
      const item = {
        section,
        startBeat,
        startMeasure,
      };
      startBeat += section.measures * 4;
      startMeasure += section.measures;
      return item;
    });
  });

  readonly groups = computed<InstrumentGroup[]>(() => ARRANGED_PARTS.map((role) => ({
    role,
    name: ROLE_NAMES[role],
    tracks: this.project().tracks.filter((track) => track.role === role),
  })));

  private readonly trackNotes = computed(() => {
    const project = this.project();
    return new Map(project.tracks.map((track) => [track.id, layOutTrackPhrase(project, track)]));
  });

  private readonly cellNotes = computed(() => {
    const trackNotes = this.trackNotes();
    const previews = new Map<string, readonly PreviewNote[]>();
    for (const section of this.sections()) {
      const sectionEndBeat = section.startBeat + section.section.measures * 4;
      for (const group of this.groups()) {
        const notes = group.tracks.flatMap((track) => (trackNotes.get(track.id) ?? [])
          .filter((note) => note.startBeat >= section.startBeat && note.startBeat < sectionEndBeat));
        if (notes.length === 0) {
          previews.set(this.cellKey(section.section.id, group.role), []);
          continue;
        }
        const minPitch = Math.min(...notes.map((note) => note.midi));
        const maxPitch = Math.max(...notes.map((note) => note.midi));
        const pitchSpan = Math.max(12, maxPitch - minPitch);
        const sectionBeats = section.section.measures * 4;
        previews.set(this.cellKey(section.section.id, group.role), notes.map((note) => ({
          leftPercent: (note.startBeat - section.startBeat) / sectionBeats * 100,
          widthPercent: Math.max(0.45, note.durationBeats / sectionBeats * 100),
          topPercent: (maxPitch - note.midi) / pitchSpan * 78 + 8,
          opacity: 0.48 + note.velocity / 127 * 0.48,
        })));
      }
    }
    return previews;
  });

  sectionLabel(type: string): string { return type.replaceAll('_', ' '); }
  measureLabel(section: OverviewSection): string {
    return `${section.startMeasure.toString().padStart(2, '0')}–${(section.startMeasure + section.section.measures - 1).toString().padStart(2, '0')}`;
  }
  previewNotes(sectionId: string, role: ArrangedPart): readonly PreviewNote[] {
    return this.cellNotes().get(this.cellKey(sectionId, role)) ?? [];
  }
  trackPresent(section: ArrangementSection, track: CompositionTrack): boolean {
    return shouldGenerateTrackInSection(section, track);
  }
  isGroupSelected(group: InstrumentGroup): boolean {
    const selectedTrackId = this.selectedTrackId();
    return !!selectedTrackId && group.tracks.some((track) => track.id === selectedTrackId);
  }
  toggleTrack(section: ArrangementSection, track: CompositionTrack): void {
    this.partToggled.emit({
      sectionId: section.id,
      trackId: track.id,
      present: !this.trackPresent(section, track),
    });
  }
  selectSection(sectionId: string): void {
    this.sectionSelected.emit({ sectionId, trackId: this.selectedTrackId() });
  }

  private cellKey(sectionId: string, role: ArrangedPart): string { return `${sectionId}:${role}`; }

  private readonly scrollHost = viewChild.required<ElementRef<HTMLDivElement>>('scrollHost');
  private readonly scrollContent = viewChild.required<ElementRef<HTMLDivElement>>('scrollContent');
  private readonly viewport = viewChild.required<ElementRef<HTMLDivElement>>('viewport');
  private readonly scoreCanvas = viewChild.required<ElementRef<HTMLCanvasElement>>('scoreCanvas');
  private readonly playheadCanvas = viewChild.required<ElementRef<HTMLCanvasElement>>('playheadCanvas');
  private resizeObserver?: ResizeObserver;
  private frame = 0;
  private ready = false;
  private width = 0;
  private height = 0;
  private labelWidth = 88;
  private rowHeight = 65;
  private pixelsPerBeat = 12;
  private offsetX = 0;
  private offsetY = 0;
  private hits: OverviewHitArea[] = [];
  private focusedKey: string | null = null;
  private accent = '#32d7f4';

  private readonly modelEffect = effect(() => {
    this.sections();
    this.groups();
    this.cellNotes();
    this.selectedSectionId();
    this.selectedTrackId();
    this.scheduleDraw();
  });

  private readonly playheadEffect = effect(() => {
    const percent = this.playheadPercent();
    this.drawPlayhead(percent);
  });

  ngAfterViewInit(): void {
    this.ready = true;
    this.resizeObserver = new ResizeObserver(() => this.scheduleDraw());
    this.resizeObserver.observe(this.scrollHost().nativeElement);
    this.scheduleDraw();
  }

  ngOnDestroy(): void {
    this.ready = false;
    this.resizeObserver?.disconnect();
    cancelAnimationFrame(this.frame);
  }

  onScroll(): void { this.scheduleDraw(); }

  private scheduleDraw(): void {
    if (!this.ready || this.frame) return;
    this.frame = requestAnimationFrame(() => {
      this.frame = 0;
      if (this.ready) this.drawOverview();
    });
  }

  private beatX(beat: number): number { return this.labelWidth + beat * this.pixelsPerBeat - this.offsetX; }

  private drawOverview(): void {
    const host = this.scrollHost().nativeElement;
    this.width = host.clientWidth;
    this.height = host.clientHeight;
    if (this.width <= 0 || this.height <= 0) return;
    const sections = this.sections();
    const groups = this.groups();
    const totalBeats = sections.reduce((sum, item) => sum + item.section.measures * 4, 0);
    this.labelWidth = this.width <= 680 ? 72 : 88;
    // All section widths are proportional to duration, including short sections.
    const minimumBeatWidth = sections.reduce((width, item) => Math.max(width, 170 / Math.max(1, item.section.measures * 4)), 12);
    this.pixelsPerBeat = Math.max(minimumBeatWidth, (this.width - this.labelWidth) / Math.max(1, totalBeats));
    this.rowHeight = Math.max(65, (this.height - RULER_HEIGHT) / Math.max(1, groups.length));
    const content = this.scrollContent().nativeElement;
    content.style.width = `${Math.max(this.width, this.labelWidth + totalBeats * this.pixelsPerBeat)}px`;
    content.style.height = `${Math.max(this.height, RULER_HEIGHT + groups.length * this.rowHeight)}px`;
    const viewport = this.viewport().nativeElement;
    viewport.style.width = `${this.width}px`;
    viewport.style.height = `${this.height}px`;
    this.offsetX = host.scrollLeft;
    this.offsetY = host.scrollTop;
    const dpr = Math.min(globalThis.devicePixelRatio || 1, 2);
    for (const canvas of [this.scoreCanvas().nativeElement, this.playheadCanvas().nativeElement]) {
      const width = Math.round(this.width * dpr);
      const height = Math.round(this.height * dpr);
      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;
      }
    }
    const canvas = this.scoreCanvas().nativeElement;
    const context = canvas.getContext('2d');
    if (!context) return;
    context.setTransform(dpr, 0, 0, dpr, 0, 0);
    context.clearRect(0, 0, this.width, this.height);
    const style = getComputedStyle(document.documentElement);
    const colors = new Map<string, string>();
    const color = (name: string): string => {
      let value = colors.get(name);
      if (!value) { value = style.getPropertyValue(name).trim() || '#000000'; colors.set(name, value); }
      return value;
    };
    this.accent = color('--accent-primary');
    const fill = (x: number, y: number, width: number, height: number, name: string): void => {
      context.fillStyle = color(name);
      context.fillRect(x, y, width, height);
    };
    const text = (value: string, x: number, y: number, name: string, font = '10px monospace'): void => {
      context.font = font;
      context.fillStyle = color(name);
      context.fillText(value, x, y);
    };
    const hit = (area: OverviewHitArea, left: number, top: number): void => {
      const x = Math.max(left, area.x);
      const y = Math.max(top, area.y);
      const right = Math.min(this.width, area.x + area.width);
      const bottom = Math.min(this.height, area.y + area.height);
      if (right > x && bottom > y) this.hits.push({ ...area, x, y, width: right - x, height: bottom - y });
    };
    this.hits = [];
    fill(0, 0, this.width, this.height, '--surface-base');

    // Clip the timeline behind the fixed group labels and ruler.
    context.save();
    context.beginPath(); context.rect(this.labelWidth, RULER_HEIGHT, Math.max(0, this.width - this.labelWidth), Math.max(0, this.height - RULER_HEIGHT)); context.clip();
    for (let row = 0; row < groups.length; row++) {
      const group = groups[row];
      const y = RULER_HEIGHT + row * this.rowHeight - this.offsetY;
      if (y + this.rowHeight <= RULER_HEIGHT || y >= this.height) continue;
      const role = ROLE_COLORS[group.role];
      const noteColor = color(group.role === 'drums' ? '--role-drums' : `--role-${role}-strong`);
      for (const item of sections) {
        const x = this.beatX(item.startBeat);
        const width = item.section.measures * 4 * this.pixelsPerBeat;
        if (x + width <= this.labelWidth || x >= this.width) continue;
        fill(x, y, width, this.rowHeight, `--role-${role}-surface`);
        fill(x, y, 1, this.rowHeight, '--surface-base');
        fill(x, y + this.rowHeight - 1, width, 1, '--border-subtle');
        if (this.selectedSectionId() === item.section.id) {
          context.strokeStyle = this.accent;
          context.strokeRect(x + 0.5, y + 0.5, width - 1, this.rowHeight - 1);
        }
        hit({ key: `section:${item.section.id}:${group.role}`, x, y, width, height: this.rowHeight,
          label: `${group.name}, ${this.sectionLabel(item.section.type)}, bars ${this.measureLabel(item)}`,
          activate: () => this.selectSection(item.section.id) }, this.labelWidth, RULER_HEIGHT);
        const togglesPerRow = Math.max(1, Math.floor((width - 10) / 19));
        const toggleRows = Math.ceil(group.tracks.length / togglesPerRow);
        const previewHeight = Math.max(12, this.rowHeight - 10 - Math.max(1, toggleRows) * 19);
        context.save();
        context.beginPath(); context.rect(x, y + 5, width, previewHeight); context.clip();
        for (let bar = 1; bar < item.section.measures; bar++) fill(x + bar * 4 * this.pixelsPerBeat, y + 5, 1, previewHeight, '--border-subtle');
        context.fillStyle = noteColor;
        for (const note of this.previewNotes(item.section.id, group.role)) {
          const noteX = x + note.leftPercent / 100 * width;
          const noteWidth = Math.max(1, note.widthPercent / 100 * width);
          if (noteX + noteWidth <= this.labelWidth || noteX >= this.width) continue;
          context.globalAlpha = note.opacity;
          context.fillRect(noteX, y + 5 + note.topPercent / 100 * previewHeight, noteWidth, 2);
        }
        context.globalAlpha = 1;
        context.restore();
        for (let index = 0; index < group.tracks.length; index++) {
          const track = group.tracks[index];
          const toggleRow = Math.floor(index / togglesPerRow);
          const rowCount = Math.min(togglesPerRow, group.tracks.length - toggleRow * togglesPerRow);
          const centerX = x + (width - rowCount * 19 + 3) / 2 + index % togglesPerRow * 19 + 8;
          const centerY = y + this.rowHeight - (toggleRows - toggleRow - 1) * 19 - 12;
          const present = this.trackPresent(item.section, track);
          context.globalAlpha = present ? 1 : 0.48;
          context.beginPath(); context.arc(centerX, centerY, 8, 0, Math.PI * 2);
          context.fillStyle = color(present ? `--role-${role}-surface` : '--surface-button'); context.fill();
          context.strokeStyle = present ? noteColor : color('--border-default'); context.stroke();
          context.textAlign = 'center';
          text(String(index + 1), centerX, centerY + 3, present ? (group.role === 'drums' ? '--role-drums' : `--role-${role}-text`) : '--text-muted', '9px monospace');
          context.textAlign = 'start'; context.globalAlpha = 1;
          hit({ key: `toggle:${item.section.id}:${track.id}`, x: centerX - 8, y: centerY - 8, width: 16, height: 16,
            label: `Toggle ${group.name} track ${index + 1} in ${this.sectionLabel(item.section.type)}. ${present ? 'Present' : 'Absent'}.`,
            activate: () => this.toggleTrack(item.section, track) }, this.labelWidth, RULER_HEIGHT);
        }
      }
    }
    context.restore();

    context.save();
    context.beginPath(); context.rect(this.labelWidth, 0, Math.max(0, this.width - this.labelWidth), RULER_HEIGHT); context.clip();
    for (const item of sections) {
      const x = this.beatX(item.startBeat);
      const width = item.section.measures * 4 * this.pixelsPerBeat;
      if (x + width <= this.labelWidth || x >= this.width) continue;
      fill(x, 0, width, RULER_HEIGHT, this.selectedSectionId() === item.section.id ? '--surface-accent' : '--surface-base');
      fill(x, 0, 1, RULER_HEIGHT, '--border-subtle');
      context.save(); context.beginPath(); context.rect(x + 6, 0, width - 12, RULER_HEIGHT); context.clip();
      text(this.sectionLabel(item.section.type), x + 7, 15, '--text-primary', '600 10px sans-serif');
      text(this.measureLabel(item), x + 7, 30, '--text-muted');
      context.restore();
      hit({ key: `heading:${item.section.id}`, x, y: 0, width, height: RULER_HEIGHT,
        label: `${this.sectionLabel(item.section.type)}, bars ${this.measureLabel(item)}`,
        activate: () => this.selectSection(item.section.id) }, this.labelWidth, 0);
    }
    context.restore();

    context.save(); context.beginPath(); context.rect(0, RULER_HEIGHT, this.labelWidth, Math.max(0, this.height - RULER_HEIGHT)); context.clip();
    for (let row = 0; row < groups.length; row++) {
      const group = groups[row];
      const y = RULER_HEIGHT + row * this.rowHeight - this.offsetY;
      if (y + this.rowHeight <= RULER_HEIGHT || y >= this.height) continue;
      fill(0, y, this.labelWidth, this.rowHeight, this.isGroupSelected(group) ? '--surface-accent' : '--surface-low');
      fill(0, y + this.rowHeight - 1, this.labelWidth, 1, '--border-subtle');
      context.fillStyle = color(group.role === 'drums' ? '--role-drums' : `--role-${ROLE_COLORS[group.role]}`);
      context.beginPath(); context.arc(12, y + this.rowHeight / 2, 4, 0, Math.PI * 2); context.fill();
      text(group.name, 23, y + this.rowHeight / 2 + 3, '--text-secondary', '10px sans-serif');
      hit({ key: `group:${group.role}`, x: 0, y, width: this.labelWidth, height: this.rowHeight,
        label: `Select ${group.name} group`, activate: () => this.groupSelected.emit(group.role) }, 0, RULER_HEIGHT);
    }
    context.restore();
    fill(0, 0, this.labelWidth, RULER_HEIGHT, '--surface-base');
    text('PART', 6, 30, '--text-muted');
    fill(0, RULER_HEIGHT - 1, this.width, 1, '--border-subtle');
    fill(this.labelWidth - 1, RULER_HEIGHT, 1, this.height - RULER_HEIGHT, '--border-subtle');
    const focused = this.hits.find((area) => area.key === this.focusedKey);
    if (focused) {
      context.strokeStyle = this.accent;
      context.strokeRect(focused.x + 1, focused.y + 1, focused.width - 2, focused.height - 2);
      canvas.setAttribute('aria-label', `${focused.label} Use arrow keys to navigate and Enter to activate.`);
    }
    this.drawPlayhead(this.playheadPercent());
  }

  private drawPlayhead(percent: number): void {
    if (!this.ready || this.width <= 0 || this.height <= 0) return;
    const context = this.playheadCanvas().nativeElement.getContext('2d');
    if (!context) return;
    const dpr = Math.min(globalThis.devicePixelRatio || 1, 2);
    context.setTransform(dpr, 0, 0, dpr, 0, 0);
    context.clearRect(0, 0, this.width, this.height);
    const totalBeats = this.sections().reduce((sum, item) => sum + item.section.measures * 4, 0);
    const x = this.beatX(Math.min(100, Math.max(0, percent)) / 100 * totalBeats);
    if (x < this.labelWidth || x > this.width) return;
    context.fillStyle = this.accent;
    context.fillRect(Math.min(this.width - 1, Math.floor(x)), RULER_HEIGHT, 1, this.height - RULER_HEIGHT);
  }

  private hitAt(event: MouseEvent): OverviewHitArea | undefined {
    const bounds = this.scoreCanvas().nativeElement.getBoundingClientRect();
    const x = (event.clientX - bounds.left) * this.width / Math.max(1, bounds.width);
    const y = (event.clientY - bounds.top) * this.height / Math.max(1, bounds.height);
    for (let index = this.hits.length - 1; index >= 0; index--) {
      const area = this.hits[index];
      if (x >= area.x && x < area.x + area.width && y >= area.y && y < area.y + area.height) return area;
    }
    return undefined;
  }

  onCanvasClick(event: MouseEvent): void {
    const area = this.hitAt(event);
    if (!area) return;
    this.focusedKey = area.key;
    area.activate();
    this.scheduleDraw();
  }

  onPointerMove(event: PointerEvent): void {
    const area = this.hitAt(event);
    const canvas = this.scoreCanvas().nativeElement;
    canvas.style.cursor = area ? 'pointer' : 'default';
    canvas.title = area?.label ?? '';
  }

  onCanvasKeydown(event: KeyboardEvent): void {
    if (!this.hits.length) return;
    const index = this.hits.findIndex((area) => area.key === this.focusedKey);
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.hits[Math.max(0, index)].activate();
    } else if (event.key.startsWith('Arrow')) {
      event.preventDefault();
      const offset = event.key === 'ArrowLeft' || event.key === 'ArrowUp' ? -1 : 1;
      this.focusedKey = this.hits[(index + offset + this.hits.length) % this.hits.length].key;
    } else return;
    this.scheduleDraw();
  }
}
