import { UiPreferencesService } from '../core/ui/ui-preferences.service';
import {
  AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, effect, inject, input, output, signal, viewChild,
} from '@angular/core';
import type { Application as PixiApplication, Color as PixiColor, Graphics as PixiGraphics } from 'pixi.js';
import { drumInstrumentName } from '../core/music/drum-instruments';
import type { PhraseSource } from '../core/music/phrase';
import { dragPhraseNotes, snapMidiToScale, type PhraseDragMode } from '../core/music/phrase-edit';
import type { ArrangedPart, PhraseNote } from '../core/project/project.model';

export interface ScoreCanvasTrack {
  readonly id: string;
  readonly name: string;
  readonly color: ArrangedPart;
  /** A combined score lane retains the IDs of its independent source tracks. */
  readonly drumPitch?: number;
  readonly trackIds?: readonly string[];
}

export interface ScoreCanvasNote extends PhraseNote {
  readonly part: string;
  readonly trackName?: string;
  readonly trackOrder?: number;
  readonly phraseSource?: PhraseSource;
  readonly color: ArrangedPart;
  readonly topPercent: number;
  readonly opacity: number;
}

export interface ScoreCanvasSection {
  readonly startBeat: number;
}

export interface ScoreSectionFocus {
  readonly startBeat: number;
  readonly endBeat: number;
}

export interface ScoreViewport {
  readonly scale: number;
  readonly offsetPercent: number;
  readonly width: number;
  readonly laneHeight: number;
  readonly verticalOffset: number;
  readonly scoreHeight: number;
}

interface NoteHitArea {
  readonly note: ScoreCanvasNote;
  readonly trackIndex: number;
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

interface ScoreCanvasModel {
  readonly tracks: readonly ScoreCanvasTrack[];
  readonly notes: readonly ScoreCanvasNote[];
  readonly sections: readonly ScoreCanvasSection[];
  readonly totalBeats: number;
  readonly selectedTrackId: string;
  readonly hiddenTrackIds: ReadonlySet<string>;
}

const VELOCITY_HEIGHT = 34;
const HIT_BUCKET_WIDTH = 96;
const MIN_TIME_GRID_SPACING = 8;
const MAX_PITCH_ROW_HEIGHT = 50;
const COLORS: Readonly<Record<ArrangedPart, { note: string; velocity: string; glow: string }>> = {
  melody: { note: '--role-melody-strong', velocity: '--role-melody-strong', glow: '--role-melody-glow' },
  bass: { note: '--role-bass-strong', velocity: '--role-bass-strong', glow: '--role-bass-glow' },
  chords: { note: '--role-chords-strong', velocity: '--role-chords-strong', glow: '--role-chords-glow' },
  arpeggio: { note: '--role-arpeggio-strong', velocity: '--role-arpeggio-strong', glow: '--role-arpeggio-glow' },
  drums: { note: '--role-snare', velocity: '--role-drums', glow: '--role-snare-glow' },
};

@Component({
  selector: 'vc-score-canvas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './score-canvas.component.html',
  styleUrl: './score-canvas.component.css',
})
export class ScoreCanvasComponent implements AfterViewInit, OnDestroy {
  readonly tracks = input.required<readonly ScoreCanvasTrack[]>();
  readonly notes = input.required<readonly ScoreCanvasNote[]>();
  readonly notesReady = input(true);
  readonly sections = input.required<readonly ScoreCanvasSection[]>();
  readonly totalBeats = input.required<number>();
  readonly playheadPercent = input.required<number>();
  readonly selectedTrackId = input.required<string>();
  readonly hiddenTrackIds = input.required<ReadonlySet<string>>();
  readonly editBounds = input<ScoreSectionFocus | null>(null);
  readonly editable = input(false);
  readonly velocityVisible = input(false);
  readonly selectedNoteIds = input<readonly string[]>([]);
  readonly snapBeats = input(0.25);
  readonly snapToScale = input(true);
  readonly scalePitchClasses = input<readonly number[]>([]);
  private middlePress: { pointerId: number; startedEmpty: boolean; canApply: boolean } | null = null;
  readonly selectionChanged = output<readonly string[]>();
  readonly notesChanged = output<{ notes: readonly PhraseNote[]; duplicate: boolean }>();
  readonly splitRequested = output<{ id: string; beat: number }>();
  readonly deleteRequested = output<string>();
  readonly auditionRequested = output<readonly PhraseNote[]>();
  private lastAudition = 0;
  readonly noteChanged = output<PhraseNote>();
  readonly applyRequested = output<void>();
  readonly noteAdded = output<{ trackId?: string; midi: number; startBeat: number }>();
  readonly gutterRows = signal<readonly { midi: number; label: string; y: number; height: number; drum: boolean; black: boolean; inScale: boolean }[]>([]);
  readonly octaveLines = signal<readonly { midi: number; y: number; height: number; opacity: number }[]>([]);
  readonly noteTooltip = signal<{ track: string; color: string; pitch: string; velocity: number; source: string; x: number; y: number } | null>(null);
  private pitchMin = 48;
  private pitchMax = 72;
  private drumPitches: number[] = [];
  private harmonicHeight = 0;
  private drumHeight = 0;
  private drag: { note: ScoreCanvasNote; x: number; y: number; pointerId: number; mode: PhraseDragMode; current: PhraseNote;
    originals: readonly ScoreCanvasNote[]; preview: readonly PhraseNote[]; duplicate: boolean; lockPitch: boolean } | null = null;
  private box: { x: number; y: number; endX: number; endY: number; pointerId: number; previous: readonly string[] } | null = null;
  private shape: { mode: 'pitch' | 'velocity'; pointerId: number; notes: Map<string, PhraseNote> } | null = null;
  private readonly touches = new Map<number, { x: number; y: number }>();
  private pinch: { distance: number; x: number; y: number } | null = null;
  private touchGesture = false;
  private touchTap: { x: number; y: number } | null = null;
  private readonly onContextMenu = (event: MouseEvent): void => { if (this.editable()) event.preventDefault(); };
  private suppressClick = false;
  readonly sectionFocus = input<ScoreSectionFocus | null>(null);
  readonly noteSelected = output<ScoreCanvasNote>();
  readonly noteTrackSelected = output<ScoreCanvasNote>();
  readonly trackSelected = output<ScoreCanvasTrack>();
  readonly seekRequested = output<number>();
  readonly viewportChanged = output<ScoreViewport>();

  private readonly rendererHost = viewChild.required<ElementRef<HTMLDivElement>>('rendererHost');
  private readonly horizontalScroll = viewChild<ElementRef<HTMLDivElement>>('horizontalScroll');
  private readonly horizontalContent = viewChild<ElementRef<HTMLDivElement>>('horizontalContent');
  private readonly verticalScroll = viewChild<ElementRef<HTMLDivElement>>('verticalScroll');
  private readonly verticalContent = viewChild<ElementRef<HTMLDivElement>>('verticalContent');
  private staticGraphics?: PixiGraphics;
  private dynamicGraphics?: PixiGraphics;
  private colorClass?: typeof PixiColor;
  private readonly preferences = inject(UiPreferencesService);
  private readonly modelEffect = effect(() => {
    this.preferences.appearance();
    const editable = this.editable();
    if (editable && !this.wasEditable) {
      this.overviewViewport = { horizontalScale: this.horizontalScale, horizontalOffset: this.horizontalOffset,
        verticalScale: this.verticalScale, verticalOffset: this.verticalOffset };
      this.verticalScale = 1;
      this.verticalOffset = 0;
      this.pendingEditPitchFocus = true;
    } else if (!editable && this.wasEditable && this.overviewViewport) {
      Object.assign(this, this.overviewViewport);
      this.overviewViewport = null;
      this.pendingSectionFocus = null;
      this.pendingEditPitchFocus = false;
    }
    const focus = this.sectionFocus();
    if (focus !== this.lastSectionFocus) {
      this.pendingSectionFocus = !editable && this.wasEditable ? null : focus;
      this.lastSectionFocus = focus;
    }
    this.wasEditable = editable;
    this.velocityVisible();
    this.selectedNoteIds();
    this.snapBeats();
    this.snapToScale();
    this.scalePitchClasses();
    this.notesReady();
    this.model = {
      tracks: this.tracks(),
      notes: this.notes(),
      sections: this.sections(),
      totalBeats: this.totalBeats(),
      selectedTrackId: this.selectedTrackId(),
      hiddenTrackIds: this.hiddenTrackIds(),
    };
    this.noteTooltip.set(null);
    this.drawStatic();
  });
  private readonly playheadEffect = effect(() => {
    this.playhead = this.playheadPercent();
    this.drawDynamic();
  });

  private app?: PixiApplication;
  private resizeObserver?: ResizeObserver;
  private model?: ScoreCanvasModel;
  private hitAreas: NoteHitArea[] = [];
  private hitAreaBuckets = new Map<number, NoteHitArea[]>();
  private hitColumnCount = 0;
  private playhead = 0;
  private hoverX: number | null = null;
  private focusedNoteIndex = -1;
  private destroyed = false;
  private width = 0;
  private height = 0;
  private horizontalScale = 1;
  // Offset in fractions of the full song, independent of canvas size.
  private horizontalOffset = 0;
  private verticalScale = 1;
  private verticalOffset = 0;
  private lastSectionFocus: ScoreSectionFocus | null = null;
  private wasEditable = false;
  private overviewViewport: { horizontalScale: number; horizontalOffset: number; verticalScale: number; verticalOffset: number } | null = null;
  private pendingSectionFocus: ScoreSectionFocus | null = null;
  private pendingEditPitchFocus = false;

  ngAfterViewInit(): void {
    const host = this.rendererHost().nativeElement;
    this.resizeObserver = new ResizeObserver(() => this.resizeRenderer());
    this.resizeObserver.observe(host);
    void this.initializeRenderer(host);
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.resizeObserver?.disconnect();
    const canvas = this.app?.canvas;
    canvas?.removeEventListener('click', this.onCanvasClick);
    canvas?.removeEventListener('contextmenu', this.onContextMenu);
    canvas?.removeEventListener('pointerdown', this.onPointerDown);
    canvas?.removeEventListener('pointerup', this.onPointerUp);
    canvas?.removeEventListener('pointercancel', this.onPointerCancel);
    canvas?.removeEventListener('auxclick', this.onAuxClick);
    canvas?.removeEventListener('dblclick', this.onDoubleClick);
    canvas?.removeEventListener('pointermove', this.onPointerMove);
    canvas?.removeEventListener('pointerleave', this.onPointerLeave);
    canvas?.removeEventListener('keydown', this.onCanvasKeydown);
    canvas?.removeEventListener('focus', this.onCanvasFocus);
    canvas?.removeEventListener('wheel', this.onCanvasWheel);
    this.app?.destroy({ removeView: true }, { children: true });
    this.app = undefined;
  }

  private async initializeRenderer(host: HTMLDivElement): Promise<void> {
    let app: PixiApplication | undefined;
    try {
      const pixi = await import('pixi.js');
      if (this.destroyed) return;
      this.colorClass = pixi.Color;
      this.staticGraphics = new pixi.Graphics({ roundPixels: false });
      this.dynamicGraphics = new pixi.Graphics({ roundPixels: false });
      app = new pixi.Application();
      await app.init({
        width: Math.max(1, host.clientWidth),
        height: Math.max(1, host.clientHeight),
        backgroundAlpha: 0,
        antialias: true,
        autoDensity: true,
        autoStart: false,
        clearBeforeRender: true,
        preference: 'webgl',
        resolution: Math.min(globalThis.devicePixelRatio || 1, 2),
      });
      if (this.destroyed) {
        app.destroy({ removeView: false }, { children: true });
        return;
      }
      this.app = app;
      app.stage.addChild(this.staticGraphics, this.dynamicGraphics);
      const canvas = app.canvas;
      // Pixi creates this element outside Angular's scoped template styles.
      // Absolute positioning prevents its backing size from growing the grid.
      canvas.style.position = 'absolute';
      canvas.style.inset = '0';
      canvas.style.touchAction = 'none';
      canvas.style.display = 'block';
      canvas.style.width = '100%';
      canvas.style.height = '100%';
      canvas.style.outline = 'none';
      canvas.setAttribute('role', 'application');
      canvas.setAttribute('tabindex', '0');
      canvas.setAttribute('aria-keyshortcuts', 'ArrowLeft ArrowRight ArrowUp ArrowDown Enter Control+A Control+Z Control+Y Shift+T Delete');
      host.appendChild(canvas);
      canvas.addEventListener('click', this.onCanvasClick);
      canvas.addEventListener('contextmenu', this.onContextMenu);
      canvas.addEventListener('pointerdown', this.onPointerDown);
      canvas.addEventListener('pointerup', this.onPointerUp);
      canvas.addEventListener('pointercancel', this.onPointerCancel);
      canvas.addEventListener('auxclick', this.onAuxClick);
      canvas.addEventListener('dblclick', this.onDoubleClick);
      canvas.addEventListener('pointermove', this.onPointerMove);
      canvas.addEventListener('pointerleave', this.onPointerLeave);
      canvas.addEventListener('keydown', this.onCanvasKeydown);
      canvas.addEventListener('focus', this.onCanvasFocus);
      // A non-passive listener is required to suppress browser Ctrl+wheel zoom.
      canvas.addEventListener('wheel', this.onCanvasWheel, { passive: false });
      this.resizeRenderer();
    } catch (error: unknown) {
      if (app?.renderer) app.destroy({ removeView: false }, { children: true });
      console.error('Could not initialize the score renderer.', error);
    }
  }

  private readonly onCanvasWheel = (event: WheelEvent): void => {
    this.noteTooltip.set(null);
    event.preventDefault();
    event.stopPropagation();
    if (!this.app || this.width <= 0) return;
    const horizontal = event.ctrlKey || event.shiftKey;
    const unit = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? (horizontal ? this.width : this.scoreHeight) : 1;
    const delta = (event.deltaY || event.deltaX) * unit;
    if (event.ctrlKey) {
      const bounds = this.app.canvas.getBoundingClientRect();
      const cursor = Math.min(1, Math.max(0, (event.clientX - bounds.left) / Math.max(1, bounds.width)));
      const anchor = this.horizontalOffset + cursor / this.horizontalScale;
      this.horizontalScale = Math.min(128, Math.max(1, this.horizontalScale * Math.exp(-delta * 0.002)));
      this.horizontalOffset = anchor - cursor / this.horizontalScale;
    } else if (event.altKey) {
      if (this.harmonicHeight <= 0) return;
      const bounds = this.app.canvas.getBoundingClientRect();
      const cursorY = Math.min(this.harmonicHeight, Math.max(0, (event.clientY - bounds.top) * this.height / Math.max(1, bounds.height)));
      const anchor = (this.verticalOffset + cursorY) / this.laneHeight;
      this.verticalScale = Math.min(this.maxVerticalScale, Math.max(1, this.verticalScale * Math.exp(-delta * 0.002)));
      this.verticalOffset = anchor * this.laneHeight - cursorY;
    } else if (event.shiftKey) {
      this.horizontalOffset += delta / (this.width * this.horizontalScale);
    } else {
      this.verticalOffset += event.deltaY * unit;
      this.horizontalOffset += event.deltaX * unit / (this.width * this.horizontalScale);
    }
    this.updateViewport();
    this.drawStatic();
  };

  private updateViewport(): void {
    this.horizontalOffset = Math.min(1 - 1 / this.horizontalScale, Math.max(0, this.horizontalOffset));
    this.verticalOffset = Math.min(Math.max(0, this.contentHeight - this.scoreHeight), Math.max(0, this.verticalOffset));
    const horizontalScroll = this.horizontalScroll()?.nativeElement;
    const horizontalContent = this.horizontalContent()?.nativeElement;
    const verticalScroll = this.verticalScroll()?.nativeElement;
    const verticalContent = this.verticalContent()?.nativeElement;
    if (horizontalScroll && horizontalContent) {
      horizontalContent.style.width = `${this.width * this.horizontalScale}px`;
      horizontalScroll.scrollLeft = this.horizontalOffset * this.width * this.horizontalScale;
    }
    if (verticalScroll && verticalContent) {
      verticalContent.style.height = `${this.contentHeight}px`;
      verticalScroll.scrollTop = this.verticalOffset;
    }
    this.viewportChanged.emit({ scale: this.horizontalScale, offsetPercent: this.horizontalOffset * 100,
      width: this.width, laneHeight: this.laneHeight, verticalOffset: this.verticalOffset, scoreHeight: this.scoreHeight });
  }

  private get scoreHeight(): number { return Math.max(1, this.height - (this.velocityVisible() ? VELOCITY_HEIGHT : 0)); }
  private get maxVerticalScale(): number {
    return Math.min(this.editable() ? 128 : 8,
      Math.max(1, MAX_PITCH_ROW_HEIGHT * (this.pitchMax - this.pitchMin + 1) / Math.max(1, this.harmonicHeight)));
  }
  private get contentHeight(): number { return this.laneHeight * (this.pitchMax - this.pitchMin + 1) + this.drumHeight; }
  private get laneHeight(): number {
    return Math.min(MAX_PITCH_ROW_HEIGHT, Math.max(1, this.harmonicHeight) * this.verticalScale / (this.pitchMax - this.pitchMin + 1));
  }

  onHorizontalScroll(): void {
    const offset = (this.horizontalScroll()?.nativeElement.scrollLeft ?? 0) / Math.max(1, this.width * this.horizontalScale);
    if (Math.abs(offset - this.horizontalOffset) * this.width * this.horizontalScale < 1) return;
    this.horizontalOffset = offset;
    this.drawStatic();
  }

  onVerticalScroll(): void {
    const offset = this.verticalScroll()?.nativeElement.scrollTop ?? 0;
    if (Math.abs(offset - this.verticalOffset) < 1) return;
    this.verticalOffset = offset;
    this.drawStatic();
  }

  private scoreX(beat: number): number {
    return (beat / Math.max(1, this.model?.totalBeats ?? 1) - this.horizontalOffset) * this.width * this.horizontalScale;
  }

  private readonly onCanvasClick = (event: MouseEvent): void => {
    if (this.suppressClick) { this.suppressClick = false; return; }
    if (this.editable()) return;
    if (event.button && event.button !== 0) return;
    const area = this.hitAreaAt(event);
    this.focusedNoteIndex = area ? this.hitAreas.indexOf(area) : -1;
    this.describeCanvas(area?.note);
    this.drawDynamic();
    if (area) this.noteSelected.emit(area.note);
    else if (this.app && this.model && this.width > 0) {
      const bounds = this.app.canvas.getBoundingClientRect();
      const y = (event.clientY - bounds.top) * this.height / Math.max(1, bounds.height);
      if (y >= 0 && y < this.scoreHeight) {
        const track = this.model.tracks.find(track => track.color === (y >= this.harmonicHeight ? 'drums' : this.model?.tracks.find(item => item.id === this.model?.selectedTrackId)?.color)) ?? this.model.tracks[0];
        if (track) this.trackSelected.emit(track);
      }
      const fraction = (event.clientX - bounds.left) / Math.max(1, bounds.width);
      const beat = (this.horizontalOffset + fraction / this.horizontalScale) * this.model.totalBeats;
      this.seekRequested.emit(Math.max(0, Math.min(this.model.totalBeats, beat)));
    }
  };

  private readonly onPointerMove = (event: PointerEvent): void => {
    if (this.touches.has(event.pointerId)) {
      this.touches.set(event.pointerId, { x: event.clientX, y: event.clientY });
      if (this.touchTap && Math.hypot(event.clientX - this.touchTap.x, event.clientY - this.touchTap.y) > 8) this.touchTap = null;
      const next = this.pinchPosition();
      if (next && this.pinch && this.app) {
        const bounds = this.app.canvas.getBoundingClientRect();
        const previousX = (this.pinch.x - bounds.left) / Math.max(1, bounds.width);
        const nextX = (next.x - bounds.left) / Math.max(1, bounds.width);
        const anchor = this.horizontalOffset + previousX / this.horizontalScale;
        const ratio = next.distance / Math.max(1, this.pinch.distance);
        const previousY = (this.pinch.y - bounds.top) * this.height / Math.max(1, bounds.height);
        const nextY = (next.y - bounds.top) * this.height / Math.max(1, bounds.height);
        const pitchAnchor = (this.verticalOffset + previousY) / this.laneHeight;
        this.horizontalScale = Math.min(128, Math.max(1, this.horizontalScale * ratio));
        this.horizontalOffset = anchor - nextX / this.horizontalScale;
        if (this.harmonicHeight > 0) {
          this.verticalScale = Math.min(this.maxVerticalScale, Math.max(1, this.verticalScale * ratio));
          this.verticalOffset = pitchAnchor * this.laneHeight - nextY;
        }
        this.pinch = next;
        this.updateViewport();
        this.drawStatic();
      }
      if (this.touchGesture) return;
    }
    const canvas = this.app?.canvas;
    if (!canvas) return;
    const bounds = canvas.getBoundingClientRect();
    this.hoverX = Math.min(this.width, Math.max(0, (event.clientX - bounds.left) * this.width / Math.max(1, bounds.width)));
    if (this.drag) {
      const drag = this.drag;
      const dx = (event.clientX - drag.x) * this.width / Math.max(1, bounds.width);
      const dy = (event.clientY - drag.y) * this.height / Math.max(1, bounds.height);
      const rawDelta = dx / (this.width * this.horizontalScale) * (this.model?.totalBeats ?? 1);
      const grid = event.shiftKey ? 0 : this.snapBeats();
      const anchor = drag.mode === 'duration' ? drag.note.durationBeats : drag.note.startBeat;
      const beatDelta = grid ? Math.round((anchor + rawDelta) / grid) * grid - anchor : rawDelta;
      const pitchDelta = drag.lockPitch || (event.shiftKey && drag.mode === 'move') ? 0 : -Math.round(dy / this.laneHeight);
      const previousPreview = drag.preview;
      drag.preview = dragPhraseNotes(drag.originals, drag.mode, beatDelta, pitchDelta, -Math.round(dy),
        this.editBounds()?.startBeat ?? 0, this.editBounds()?.endBeat ?? this.model?.totalBeats ?? 1);
      if ((drag.mode === 'pitch' || drag.mode === 'move') && pitchDelta !== 0 && !drag.lockPitch) {
        drag.preview = drag.preview.map(note => ({ ...note, midi: this.snapPitch(note.midi) }));
      }
      drag.current = drag.preview.find(note => note.id === drag.note.id) ?? drag.note;
      const pitchChanged = drag.preview.some((note, index) => note.midi !== previousPreview[index]?.midi);
      if (pitchChanged || (drag.mode === 'velocity' && performance.now() - this.lastAudition > 300)) {
        this.auditionRequested.emit(drag.preview);
        this.lastAudition = performance.now();
      }
      this.suppressClick ||= Math.abs(dx) + Math.abs(dy) > 3;
    }
    if (this.box) {
      this.box.endX = this.hoverX;
      this.box.endY = (event.clientY - bounds.top) * this.height / Math.max(1, bounds.height);
    }
    if (this.shape) {
      const beat = this.beatAt(event);
      const y = (event.clientY - bounds.top) * this.height / Math.max(1, bounds.height);
      for (const note of this.model?.notes ?? []) {
        if (note.startBeat <= beat && beat < note.startBeat + note.durationBeats) {
          const midi = note.color === 'drums' ? note.midi : this.snapPitch(this.pitchMax - Math.floor((y + this.verticalOffset) / this.laneHeight));
          const velocity = Math.max(1, Math.min(127, Math.round(127 * (1 - y / this.scoreHeight))));
          this.shape.notes.set(note.id, { ...note, ...(this.shape.mode === 'pitch' ? { midi } : { velocity }) });
        }
      }
    }
    this.drawDynamic();
    const area = this.hitAreaAt(event);
    canvas.style.cursor = area ? 'pointer' : 'default';
    this.showNoteTooltip(this.drag || this.box || this.shape || event.pointerType === 'touch' ? undefined : area,
      this.hoverX, (event.clientY - bounds.top) * this.height / Math.max(1, bounds.height));
  };

  private showNoteTooltip(area: NoteHitArea | undefined, x: number, y: number): void {
    if (!area) { this.noteTooltip.set(null); return; }
    const note = area.note;
    const name = note.trackName ?? this.model?.tracks.find(track => track.id === note.part)?.name ?? note.color;
    const order = note.trackOrder;
    const defaultName = `${note.color[0].toUpperCase()}${order}`;
    const sources: Record<PhraseSource, string> = {
      'global-generated': 'Global generated', 'global-edited': 'Global edited phrase',
      'section-generated': 'Section generated', 'section-edited': 'Section edited phrase',
    };
    this.noteTooltip.set({
      track: order && name !== defaultName ? `${name} (#${order})` : name,
      color: `var(--role-${note.color})`,
      pitch: `${['C', 'C♯', 'D', 'D♯', 'E', 'F', 'F♯', 'G', 'G♯', 'A', 'A♯', 'B'][note.midi % 12]}${Math.floor(note.midi / 12) - 1} [${note.midi}]`,
      velocity: note.velocity, source: sources[note.phraseSource ?? 'global-generated'],
      x: Math.max(8, Math.min(x + 14, this.width - 248)),
      y: y + 90 <= this.height ? y + 14 : Math.max(8, y - 82),
    });
  }

  private readonly onPointerLeave = (): void => {
    this.noteTooltip.set(null);
    this.hoverX = null;
    this.drawDynamic();
  };

  private readonly onCanvasFocus = (): void => {
    if (this.focusedNoteIndex < 0 && this.hitAreas.length > 0) {
      this.focusedNoteIndex = 0;
      this.describeCanvas(this.hitAreas[0].note);
      this.drawDynamic();
    }
  };

  private readonly onCanvasKeydown = (event: KeyboardEvent): void => {
    if (this.hitAreas.length === 0) return;
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
      event.preventDefault();
      this.focusedNoteIndex = (this.focusedNoteIndex + 1 + this.hitAreas.length) % this.hitAreas.length;
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.focusedNoteIndex = this.focusedNoteIndex <= 0 ? this.hitAreas.length - 1 : this.focusedNoteIndex - 1;
    } else if (event.key === 'Enter' && this.focusedNoteIndex >= 0 && this.focusedNoteIndex < this.hitAreas.length) {
      event.preventDefault();
      this.noteSelected.emit(this.hitAreas[this.focusedNoteIndex].note);
      return;
    } else {
      return;
    }
    const note = this.hitAreas[this.focusedNoteIndex].note;
    this.describeCanvas(note);
    this.drawDynamic();
  };

  private describeCanvas(note?: ScoreCanvasNote): void {
    const canvas = this.app?.canvas;
    if (!canvas) return;
    canvas.setAttribute('aria-label', note
      ? `Song score. Selected ${note.color} note, pitch ${note.midi}, velocity ${note.velocity}. Use arrow keys to choose a note and Enter to edit.`
      : `Song score with ${this.hitAreas.length} notes. Use arrow keys to choose a note and Enter to edit.`);
  }

  private hitAreaAt(event: MouseEvent | PointerEvent): NoteHitArea | undefined {
    const canvas = this.app?.canvas;
    if (!canvas) return undefined;
    const bounds = canvas.getBoundingClientRect();
    const x = (event.clientX - bounds.left) * this.width / Math.max(1, bounds.width);
    const y = (event.clientY - bounds.top) * this.height / Math.max(1, bounds.height);
    const trackCount = this.model?.tracks.length ?? 0;
    if (trackCount === 0 || y < 0 || x < 0 || x >= this.width) return undefined;
    if (y >= this.scoreHeight) {
      if (this.editable() && this.velocityVisible() && y < this.height) return this.hitAreas.find(area => Math.abs(x - area.x) <= 5);
      return undefined;
    }
    const trackIndex = y >= this.harmonicHeight && this.drumHeight > 0 ? 1 : 0;
    const column = Math.min(this.hitColumnCount - 1, Math.floor(x / HIT_BUCKET_WIDTH));
    const candidates = this.hitAreaBuckets.get(trackIndex * this.hitColumnCount + column) ?? [];
    for (let index = candidates.length - 1; index >= 0; index--) {
      const area = candidates[index];
      if (x >= area.x && x <= area.x + area.width && y >= area.y && y <= area.y + area.height) return area;
    }
    return undefined;
  }

  private beatAt(event: MouseEvent): number {
    const bounds = this.app!.canvas.getBoundingClientRect();
    return (this.horizontalOffset + (event.clientX - bounds.left) / Math.max(1, bounds.width) / this.horizontalScale) * (this.model?.totalBeats ?? 1);
  }
  private snapPitch(midi: number): number {
    return snapMidiToScale(midi, this.snapToScale() ? this.scalePitchClasses() : []);
  }
  private inScale(midi: number): boolean {
    return this.editable() && this.snapToScale() && this.scalePitchClasses().includes(midi % 12);
  }
  scaleColoringActive(): boolean {
    return this.editable() && this.snapToScale() && this.scalePitchClasses().length > 0;
  }
  private emptyCanvasPoint(event: MouseEvent): boolean {
    const bounds = this.app?.canvas.getBoundingClientRect();
    return !!bounds && event.clientX >= bounds.left && event.clientX < bounds.left + bounds.width
      && event.clientY >= bounds.top && event.clientY < bounds.top + bounds.height && !this.hitAreaAt(event);
  }
  private readonly onPointerDown = (event: PointerEvent): void => {
    this.noteTooltip.set(null);
    if (event.pointerType === 'touch' && this.app) {
      event.preventDefault();
      this.touches.set(event.pointerId, { x: event.clientX, y: event.clientY });
      this.app.canvas.setPointerCapture(event.pointerId);
      if (this.touches.size > 1) {
        this.touchGesture = true;
        this.suppressClick = true;
        this.touchTap = null;
        this.drag = null;
        this.box = null;
        this.shape = null;
        this.pinch = this.pinchPosition();
        this.drawDynamic();
        return;
      }
      if (this.touchGesture) return;
      // Defer empty-space note creation until release so a pinch cannot add a note.
      if (this.editable() && !this.hitAreaAt(event)) {
        this.touchTap = { x: event.clientX, y: event.clientY };
        return;
      }
    }
    if (event.button === 0) this.suppressClick = false;
    if (event.button === 1) {
      event.preventDefault();
      if (!this.editable()) {
        const area = this.hitAreaAt(event);
        if (area) this.noteTrackSelected.emit(area.note);
        return;
      }
    }
    if (!this.editable() || !this.app) return;
    event.preventDefault();
    this.app.canvas.focus?.();
    const area = this.hitAreaAt(event);
    if (event.button === 1) this.middlePress = { pointerId: event.pointerId, startedEmpty: this.emptyCanvasPoint(event), canApply: false };
    const bounds = this.app.canvas.getBoundingClientRect();
    const x = (event.clientX - bounds.left) * this.width / Math.max(1, bounds.width);
    const y = (event.clientY - bounds.top) * this.height / Math.max(1, bounds.height);
    if (event.button === 2) {
      this.box = { x, y, endX: x, endY: y, pointerId: event.pointerId, previous: event.ctrlKey || event.metaKey ? this.selectedNoteIds() : [] };
    } else if ((event.ctrlKey || event.metaKey) && (!area || !this.selectedNoteIds().includes(area.note.id) || event.button === 1)) {
      this.shape = { mode: event.button === 1 ? 'velocity' : 'pitch', pointerId: event.pointerId, notes: new Map() };
      this.onPointerMove(event);
    } else if (area && (event.button === 0 || event.button === 1)) {
      const ids = this.selectedNoteIds().includes(area.note.id) ? this.selectedNoteIds() : [area.note.id];
      this.selectionChanged.emit(ids);
      const resize = x >= area.x + area.width - Math.min(7, area.width / 3);
      const mode: PhraseDragMode = y >= this.scoreHeight ? 'velocity' : event.button === 1 ? (event.altKey ? 'duration' : event.shiftKey ? 'pitch' : 'velocity')
        : event.altKey || x <= area.x + Math.min(5, area.width / 3) ? 'start' : resize ? 'duration' : 'move';
      const originals = (this.model?.notes ?? []).filter(note => ids.includes(note.id));
      this.drag = { note: area.note, x: event.clientX, y: event.clientY, pointerId: event.pointerId,
        current: area.note, mode, originals, preview: originals, duplicate: event.button === 0 && !!(event.ctrlKey || event.metaKey), lockPitch: area.note.color === 'drums' };
      this.auditionRequested.emit(originals);
      this.lastAudition = performance.now();
    } else if (event.button === 0) {
      this.selectionChanged.emit([]);
      this.onDoubleClick(event);
    }
    this.app.canvas.setPointerCapture(event.pointerId);
  };

  private readonly onPointerUp = (event: PointerEvent): void => {
    if (this.touches.delete(event.pointerId)) {
      this.pinch = null;
      if (this.app?.canvas.hasPointerCapture(event.pointerId)) this.app.canvas.releasePointerCapture(event.pointerId);
      if (this.touchGesture) {
        if (!this.touches.size) this.touchGesture = false;
        return;
      }
      if (this.touchTap) {
        this.touchTap = null;
        this.onDoubleClick(event);
      }
    }
    if (this.middlePress?.pointerId === event.pointerId) {
      this.middlePress.canApply = this.middlePress.startedEmpty && this.emptyCanvasPoint(event) && !this.drag && !this.shape;
    }
    if (this.box?.pointerId === event.pointerId) {
      const box = this.box;
      this.box = null;
      const area = this.hitAreaAt(event);
      if (Math.abs(box.endX - box.x) + Math.abs(box.endY - box.y) < 3 && area) {
        if (event.altKey) {
          const grid = event.shiftKey ? 0 : this.snapBeats();
          const beat = this.beatAt(event);
          this.splitRequested.emit({ id: area.note.id, beat: grid ? Math.round(beat / grid) * grid : beat });
        } else this.deleteRequested.emit(area.note.id);
      } else {
        const ids = this.hitAreas.filter(area => area.x + area.width >= Math.min(box.x, box.endX) && area.x <= Math.max(box.x, box.endX)
          && area.y + area.height >= Math.min(box.y, box.endY) && area.y <= Math.max(box.y, box.endY)).map(area => area.note.id);
        this.selectionChanged.emit([...new Set([...box.previous, ...ids])]);
      }
    }
    if (this.shape?.pointerId === event.pointerId) {
      this.notesChanged.emit({ notes: [...this.shape.notes.values()], duplicate: false });
      this.shape = null;
    }
    if (this.drag?.pointerId === event.pointerId) {
      const { note, current, preview, duplicate } = this.drag;
      this.drag = null;
      if (duplicate || current.startBeat !== note.startBeat || current.durationBeats !== note.durationBeats || current.midi !== note.midi || current.velocity !== note.velocity) {
        if (preview.length === 1 && !duplicate) this.noteChanged.emit(current);
        else this.notesChanged.emit({ notes: preview, duplicate });
      }
    }
    if (this.app?.canvas.hasPointerCapture(event.pointerId)) this.app.canvas.releasePointerCapture(event.pointerId);
    this.drawDynamic();
  };
  private pinchPosition(): { distance: number; x: number; y: number } | null {
    const [first, second] = [...this.touches.values()];
    return first && second ? { distance: Math.hypot(second.x - first.x, second.y - first.y),
      x: (first.x + second.x) / 2, y: (first.y + second.y) / 2 } : null;
  }
  private readonly onPointerCancel = (event?: PointerEvent): void => {
    if (event) this.touches.delete(event.pointerId);
    else this.touches.clear();
    this.pinch = null;
    this.touchTap = null;
    this.touchGesture = this.touches.size > 0;
    this.drag = null; this.box = null; this.shape = null; this.middlePress = null;
    this.drawDynamic();
  };
  private readonly onAuxClick = (event: MouseEvent): void => {
    if (event.button !== 1) return;
    event.preventDefault();
    event.stopPropagation();
    if (!this.editable()) return;
    const canApply = this.middlePress?.canApply;
    this.middlePress = null;
    if (canApply && this.emptyCanvasPoint(event)) this.applyRequested.emit();
  };
  private readonly onDoubleClick = (event: MouseEvent): void => {
    if (event.detail > 1) return;
    if (!this.editable() || this.hitAreaAt(event) || !this.app || !this.model) return;
    const bounds = this.app.canvas.getBoundingClientRect();
    const x = (event.clientX - bounds.left) * this.width / bounds.width;
    const y = (event.clientY - bounds.top) * this.height / bounds.height;
    if (y >= this.scoreHeight) return;
    const midi = y >= this.harmonicHeight && this.drumPitches.length
      ? this.drumPitches[Math.min(this.drumPitches.length - 1, Math.floor((y - this.harmonicHeight) / this.drumHeight * this.drumPitches.length))]
      : this.pitchMax - Math.floor((y + this.verticalOffset) / this.laneHeight);
    const grid = event.shiftKey ? 0 : this.snapBeats();
    const rawBeat = (this.horizontalOffset + x / this.width / this.horizontalScale) * this.model.totalBeats;
    const startBeat = grid ? Math.round(rawBeat / grid) * grid : rawBeat;
    const range = this.editBounds();
    if (range && (startBeat < range.startBeat || startBeat >= range.endBeat)) return;
    const drum = y >= this.harmonicHeight && this.drumPitches.length > 0;
    const ownsPitch = (track: ScoreCanvasTrack) => track.color === 'drums'
      && (track.drumPitch === midi || this.model?.notes.some(note => note.part === track.id && note.midi === midi));
    const track = drum ? this.model.tracks.find(track => track.id === this.selectedTrackId() && ownsPitch(track))
      ?? this.model.tracks.find(ownsPitch) : undefined;
    this.noteAdded.emit({ ...(track ? { trackId: track.id } : {}), midi: drum ? midi : this.snapPitch(midi), startBeat });
  };

  private resizeRenderer(): void {
    const host = this.rendererHost().nativeElement;
    const width = Math.floor(host.clientWidth);
    const height = Math.floor(host.clientHeight);
    if (width <= 0 || height <= 0) return;
    this.width = width;
    this.height = height;
    this.app?.renderer.resize(width, height);
    this.drawStatic(true);
  }

  private drawStatic(preservePitchPosition = false): void {
    this.noteTooltip.set(null);
    const model = this.model;
    if (!model || !this.app || this.width <= 0 || this.height <= 0) return;
    const graphics = this.staticGraphics;
    const Color = this.colorClass;
    if (!graphics || !Color) return;
    graphics.clear();
    const focusedNote = this.hitAreas[this.focusedNoteIndex]?.note;
    this.hitAreas = [];
    this.hitAreaBuckets.clear();
    const { tracks, notes, sections, selectedTrackId, hiddenTrackIds } = model;
    const totalBeats = Math.max(1, model.totalBeats);
    const focus = this.pendingSectionFocus;
    if (focus) {
      this.pendingSectionFocus = null;
      const length = Math.max(0.001, focus.endBeat - focus.startBeat);
      this.horizontalScale = Math.max(1, totalBeats / length);
      this.horizontalOffset = (focus.startBeat + length / 2) / totalBeats - 0.5 / this.horizontalScale;
    }
    const scoreHeight = this.scoreHeight;
    const previousLaneHeight = this.laneHeight;
    this.hitColumnCount = Math.max(1, Math.ceil(this.width / HIT_BUCKET_WIDTH));
    const rootStyle = getComputedStyle(document.documentElement);
    const colorCache = new Map<string, { value: number; alpha: number }>();
    const color = (name: string): { value: number; alpha: number } => {
      const cached = colorCache.get(name);
      if (cached) return cached;
      const parsed = new Color(rootStyle.getPropertyValue(name).trim() || '#000000');
      const value = { value: parsed.toNumber(), alpha: parsed.alpha };
      colorCache.set(name, value);
      return value;
    };
    const fill = (x: number, y: number, width: number, height: number, name: string, alpha = 1): void => {
      const bottom = Math.min(scoreHeight, y + height);
      y = Math.max(0, y);
      height = bottom - y;
      if (width <= 0 || height <= 0) return;
      const parsed = color(name);
      graphics.rect(x, y, width, height).fill({ color: parsed.value, alpha: parsed.alpha * alpha });
    };
    const line = (x1: number, y1: number, x2: number, y2: number, name: string, alpha = 1): void => {
      const parsed = color(name);
      graphics.moveTo(x1, y1).lineTo(x2, y2).stroke({ color: parsed.value, alpha: parsed.alpha * alpha, width: 1 });
    };

    graphics.rect(0, 0, this.width, this.height).fill({ color: color('--surface-score').value });
    const visibleNotes = notes.filter(note => !hiddenTrackIds.has(note.part));
    const harmonic = visibleNotes.filter(note => note.color !== 'drums');
    const phrasePitchMin = Math.max(0, (harmonic.length ? harmonic.reduce((min, note) => Math.min(min, note.midi), 127) : 48) - 2);
    const phrasePitchMax = Math.min(127, (harmonic.length ? harmonic.reduce((max, note) => Math.max(max, note.midi), 0) : 72) + 2);
    this.pitchMin = this.editable() ? 0 : phrasePitchMin;
    this.pitchMax = this.editable() ? 127 : phrasePitchMax;
    this.drumPitches = [...new Set([...visibleNotes.filter(note => note.color === 'drums').map(note => note.midi), ...tracks.filter(track => track.color === 'drums' && !hiddenTrackIds.has(track.id)).map(track => track.drumPitch ?? 36)])].sort((a, b) => b - a);
    const hasDrums = tracks.some(track => track.color === 'drums' && !(track.trackIds ?? [track.id]).every(id => hiddenTrackIds.has(id)));
    this.drumHeight = hasDrums ? Math.min(scoreHeight * 0.27, Math.max(36, this.drumPitches.length * 14)) : 0;
    this.harmonicHeight = scoreHeight - this.drumHeight;
    if (this.pendingEditPitchFocus && this.notesReady()) {
      this.pendingEditPitchFocus = false;
      // Fit the phrase initially, but keep every MIDI pitch reachable by zooming and scrolling.
      this.verticalScale = Math.min(this.maxVerticalScale,
        this.harmonicHeight > 0 ? 128 / (phrasePitchMax - phrasePitchMin + 1) : 1);
      this.verticalOffset = (127 - phrasePitchMax) * this.laneHeight;
    } else {
      this.verticalScale = Math.min(this.maxVerticalScale, this.verticalScale);
      if (preservePitchPosition) {
        // Layout changes resize pitch rows; retain the same top pitch rather than a pixel offset.
        this.verticalOffset *= this.laneHeight / previousLaneHeight;
      }
    }
    this.updateViewport();
    const rows: { midi: number; label: string; y: number; height: number; drum: boolean; black: boolean; inScale: boolean }[] = [];
    for (let midi = this.pitchMax; midi >= this.pitchMin && this.harmonicHeight > 0; midi--) {
      const y = (this.pitchMax - midi) * this.laneHeight - this.verticalOffset;
      if (y + this.laneHeight <= 0 || y >= this.harmonicHeight) continue;
      const black = [1, 3, 6, 8, 10].includes(midi % 12);
      const h = Math.min(this.harmonicHeight, y + this.laneHeight) - Math.max(0, y);
      fill(0, y, this.width, h, black ? '--score-pitch-band' : '--score-row-even');
      if (this.scaleColoringActive() && !this.inScale(midi)) fill(0, y, this.width, h, '--score-out-of-scale');
      if (midi % 12 !== 11) line(0, Math.max(0, y), this.width, Math.max(0, y), '--grid-subdivision');
      rows.push({ midi, label: midi % 12 === 0 ? `C${Math.floor(midi / 12) - 1}` : '', y: Math.max(0, y), height: h, drum: false, black, inScale: this.inScale(midi) });
    }
    const drumRowHeight = this.drumHeight / Math.max(1, this.drumPitches.length);
    this.drumPitches.forEach((midi, index) => {
      const y = this.harmonicHeight + index * drumRowHeight;
      fill(0, y, this.width, drumRowHeight, index % 2 ? '--score-row-odd' : '--score-row-even');
      line(0, y, this.width, y, '--border-subtle');
      rows.push({ midi, label: drumInstrumentName(midi).toUpperCase(), y, height: drumRowHeight, drum: true, black: false, inScale: false });
    });
    if (this.drumHeight > 0) line(0, this.harmonicHeight + 0.5, this.width, this.harmonicHeight + 0.5, '--border-strong');
    this.gutterRows.set(rows);
    const beatWidth = this.width * this.horizontalScale / totalBeats;
    // Reveal successively finer divisions only when they have room in CSS
    // pixels. At overview scale, continue thinning beyond individual bars.
    let gridStep = this.editable() ? this.snapBeats() * 4 : 1;
    while (beatWidth * gridStep / 4 < MIN_TIME_GRID_SPACING) gridStep *= 2;
    // Fade smoothly with zoom rather than jumping in brightness at each
    // subdivision threshold. Keep a faint floor for orientation at song scale.
    const timeGridAlpha = Math.max(0.22, Math.min(1, Math.sqrt(beatWidth / (MIN_TIME_GRID_SPACING * 4))));
    const firstStep = Math.max(0, Math.ceil(this.horizontalOffset * totalBeats * 4 / gridStep) * gridStep);
    const lastStep = Math.min(totalBeats * 4, Math.ceil((this.horizontalOffset + 1 / this.horizontalScale) * totalBeats * 4));
    for (let sixteenth = firstStep; sixteenth <= lastStep; sixteenth += gridStep) {
      const x = Math.floor(this.scoreX(sixteenth / 4)) + 0.5;
      const beat = sixteenth / 4;
      const gridColor = sixteenth % 16 === 0 ? '--grid-measure'
        : sixteenth % 4 === 0 ? '--grid-beat'
          : sixteenth % 2 === 0 ? '--grid-eighth' : '--grid-step';
      line(x, 0, x, this.height, gridColor, timeGridAlpha * (sixteenth % 16 === 0 ? 1 : 0.92));
      if (beat === totalBeats) break;
    }
    for (const section of sections) {
      const x = Math.floor(this.scoreX(section.startBeat)) + 0.5;
      if (x < 0 || x > this.width) continue;
      line(x, 0, x, scoreHeight, '--grid-measure', timeGridAlpha);
    }
    // Pitch increases upward: the B/C boundary sits below C, not above it.
    // Use absolute MIDI octaves so brightness stays consistent while zooming or scrolling.
    const resolution = this.app.renderer?.resolution ?? 1;
    const octaveLineHeight = Math.max(1, Math.round(resolution)) / resolution;
    const octaveLines: { midi: number; y: number; height: number; opacity: number }[] = [];
    for (let midi = 12; midi <= 120; midi += 12) {
      const y = (this.pitchMax - midi + 1) * this.laneHeight - this.verticalOffset;
      if (midi <= this.pitchMin || midi > this.pitchMax || y <= 0 || y >= this.harmonicHeight) continue;
      // Filled, pixel-aligned strips avoid antialiasing different octaves to different brightnesses.
      const top = Math.round(y * resolution) / resolution;
      const height = Math.min(octaveLineHeight, this.harmonicHeight - top);
      if (height <= 0) continue;
      const opacity = 0.2 + 0.8 * midi / 120;
      fill(0, top, this.width, height, '--score-octave-line', opacity);
      octaveLines.push({ midi, y: top, height, opacity });
    }
    this.octaveLines.set(octaveLines);
    line(0, scoreHeight + 0.5, this.width, scoreHeight + 0.5, '--border-default');
    line(0, 0.5, this.width, 0.5, '--border-default');
    line(0.5, 0, 0.5, this.height, '--border-default');
    line(this.width - 0.5, 0, this.width - 0.5, this.height, '--border-default');

    const trackIndexes = new Map(tracks.flatMap((track, index) => (track.trackIds ?? [track.id]).map(id => [id, index] as const)));
    for (const note of notes) {
      const sourceIndex = trackIndexes.get(note.part);
      const trackIndex = note.color === 'drums' ? 1 : 0;
      if (sourceIndex === undefined || hiddenTrackIds.has(note.part)) continue;
      const x = this.scoreX(note.startBeat);
      const noteWidth = Math.max(3, note.durationBeats * beatWidth);
      if (x + noteWidth < 0 || x > this.width) continue;
      const centerY = note.color === 'drums'
        ? this.harmonicHeight + (this.drumPitches.indexOf(note.midi) + 0.5) * drumRowHeight
        : (this.pitchMax - note.midi + 0.5) * this.laneHeight - this.verticalOffset;
      const top = note.color === 'drums' ? this.harmonicHeight : 0;
      const bottom = note.color === 'drums' ? scoreHeight : this.harmonicHeight;
      if (centerY < top || centerY >= bottom) continue;
      const rowHeight = note.color === 'drums' ? drumRowHeight : this.laneHeight;
      const y = Math.max(top, centerY - rowHeight * 0.375);
      const noteHeight = Math.min(bottom, centerY + rowHeight * 0.375) - y;
      const selectionOpacity = selectedTrackId === note.part ? 1 : 0.8;
      const opacity = selectedTrackId === note.part ? note.opacity : this.editable() ? 0.3 : 0.3 + note.velocity / 127 * 0.5;
      const noteColors = COLORS[note.color];
      if (noteHeight > 0 && selectedTrackId === note.part && (!this.editable() || note.color === 'drums')) {
        for (let radius = 6; radius >= 1; radius--) {
          fill(x - radius, y - radius, noteWidth + radius * 2, noteHeight + radius * 2,
            noteColors.glow, (7 - radius) / 15);
        }
      }
      const parsed = color(noteColors.note);
      if (noteHeight > 0) graphics.roundRect(x, y, noteWidth, noteHeight, Math.min(3, noteHeight / 2)).fill({ color: parsed.value, alpha: opacity * parsed.alpha });
      if (this.velocityVisible()) {
        const velocityColor = color(noteColors.velocity);
        const velocityHeight = Math.max(2, note.velocity / 127 * VELOCITY_HEIGHT);
        graphics.rect(x, this.height - velocityHeight, 2, velocityHeight)
          .fill({ color: velocityColor.value, alpha: (selectedTrackId === note.part ? 0.9 : 0.32) * velocityColor.alpha * selectionOpacity });
      }
      if (noteHeight <= 0) continue;
      const hitHalfHeight = Math.max(3, rowHeight * 0.375);
      const hitY = Math.max(top, centerY - hitHalfHeight);
      const hitArea: NoteHitArea = {
        note,
        trackIndex,
        x: Math.max(0, x - 1),
        y: hitY,
        width: Math.min(this.width, x + Math.max(5, noteWidth + 2)) - Math.max(0, x - 1),
        height: Math.min(bottom, centerY + hitHalfHeight) - hitY,
      };
      this.hitAreas.push(hitArea);
      const firstColumn = Math.floor(hitArea.x / HIT_BUCKET_WIDTH);
      const lastColumn = Math.min(this.hitColumnCount - 1, Math.floor((hitArea.x + hitArea.width) / HIT_BUCKET_WIDTH));
      for (let column = firstColumn; column <= lastColumn; column++) {
        const bucketId = trackIndex * this.hitColumnCount + column;
        const bucket = this.hitAreaBuckets.get(bucketId);
        if (bucket) bucket.push(hitArea);
        else this.hitAreaBuckets.set(bucketId, [hitArea]);
      }
    }

    this.focusedNoteIndex = focusedNote ? this.hitAreas.findIndex((area) => area.note.id === focusedNote.id && area.note.part === focusedNote.part && area.note.startBeat === focusedNote.startBeat) : -1;
    this.describeCanvas(this.hitAreas[this.focusedNoteIndex]?.note);
    this.drawDynamic();
    this.app.render();
  }

  private drawDynamic(): void {
    if (!this.app || this.width <= 0 || this.height <= 0) return;
    const graphics = this.dynamicGraphics;
    const Color = this.colorClass;
    if (!graphics || !Color) return;
    graphics.clear();
    const accent = new Color(getComputedStyle(document.documentElement).getPropertyValue('--accent-primary').trim() || '#32d7f4');
    if (this.hoverX !== null) {
      const hoverX = Math.floor(this.hoverX) + 0.5;
      graphics.moveTo(hoverX, 0).lineTo(hoverX, this.height)
        .stroke({ color: accent.toNumber(), alpha: accent.alpha * 0.25, width: 1 });
    }
    const x = (this.playhead / 100 - this.horizontalOffset) * this.width * this.horizontalScale;
    if (x >= 0 && x <= this.width) {
      const position = Math.min(this.width - 1, x);
      const left = Math.floor(position);
      const fraction = position - left;
      // Approximate gamma correction keeps the split line from dimming between columns.
      for (const [column, weight] of [[left, 1 - fraction], [left + 1, fraction]]) {
        if (weight === 0) continue;
        graphics.moveTo(column + 0.5, 0).lineTo(column + 0.5, this.height)
          .stroke({ color: accent.toNumber(), alpha: accent.alpha * Math.pow(weight, 1 / 2.2), width: 1 });
      }
    }
    const style = getComputedStyle(document.documentElement);
    const beat = this.playhead / 100 * (this.model?.totalBeats ?? 1);
    for (const area of this.hitAreas) {
      if (beat < area.note.startBeat || beat >= area.note.startBeat + area.note.durationBeats) continue;
      const glowStrength = 1 - (beat - area.note.startBeat) / area.note.durationBeats;
      const glow = new Color(style.getPropertyValue(COLORS[area.note.color].note).trim() || '#32d7f4');
      graphics.roundRect(area.x - 3, area.y - 2, area.width + 6, area.height + 4, 3)
        .stroke({ color: glow.toNumber(), alpha: 0.325 * glowStrength, width: 3 });
      graphics.roundRect(area.x, area.y + area.height / 4, area.width, area.height / 2, 2)
        .fill({ color: glow.toNumber(), alpha: 0.4 * glowStrength });
    }
    const preview = this.drag?.preview ?? (this.shape ? [...this.shape.notes.values()] : []);
    for (const note of preview) {
      const area = this.hitAreas.find(area => area.note.id === note.id);
      if (area) {
        const rowHeight = area.note.color === 'drums' ? this.drumHeight / Math.max(1, this.drumPitches.length) : this.laneHeight;
        const centerY = area.note.color === 'drums'
          ? this.harmonicHeight + (this.drumPitches.indexOf(note.midi) + 0.5) * rowHeight
          : (this.pitchMax - note.midi + 0.5) * rowHeight - this.verticalOffset;
        const noteHeight = rowHeight * 0.75;
        graphics.roundRect(this.scoreX(note.startBeat), centerY - noteHeight / 2, Math.max(3, note.durationBeats / (this.model?.totalBeats ?? 1) * this.width * this.horizontalScale), noteHeight, Math.min(3, noteHeight / 2))
          .fill({ color: accent.toNumber(), alpha: 0.9 });
        if (this.velocityVisible()) {
          const velocityHeight = note.velocity / 127 * (VELOCITY_HEIGHT - 6);
          graphics.rect(this.scoreX(note.startBeat), this.height - velocityHeight, 3, velocityHeight)
            .fill({ color: accent.toNumber(), alpha: 0.9 });
        }
      }
    }
    for (const area of this.hitAreas) {
      if (this.selectedNoteIds().includes(area.note.id)) graphics.roundRect(area.x - 1, area.y - 1, area.width + 2, area.height + 2, 3)
        .stroke({ color: accent.toNumber(), alpha: 1, width: 2 });
    }
    if (this.box) {
      const b = this.box;
      graphics.rect(Math.min(b.x, b.endX), Math.min(b.y, b.endY), Math.abs(b.endX - b.x), Math.abs(b.endY - b.y))
        .fill({ color: accent.toNumber(), alpha: 0.12 }).stroke({ color: accent.toNumber(), alpha: 0.8, width: 1 });
    }
    const focused = this.hitAreas[this.focusedNoteIndex];
    if (focused) {
      graphics.roundRect(focused.x - 1, focused.y - 1, focused.width + 2, focused.height + 2, 3)
        .stroke({ color: accent.toNumber(), alpha: 0.95, width: 1 });
    }
    this.app.render();
  }
}
