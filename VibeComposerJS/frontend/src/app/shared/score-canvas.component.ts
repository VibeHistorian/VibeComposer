import {
  AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, effect, input, output, viewChild,
} from '@angular/core';
import type { Application as PixiApplication, Color as PixiColor, Graphics as PixiGraphics } from 'pixi.js';
import type { ArrangedPart, PhraseNote } from '../core/project/project.model';

export interface ScoreCanvasTrack {
  readonly id: string;
  readonly name: string;
  readonly color: ArrangedPart;
}

export interface ScoreCanvasNote extends PhraseNote {
  readonly part: string;
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
const COLORS: Readonly<Record<ArrangedPart, { note: string; velocity: string; glow: string }>> = {
  bass: { note: '--role-bass-strong', velocity: '--role-bass-strong', glow: '--role-bass-glow' },
  chords: { note: '--role-chords-strong', velocity: '--role-chords-strong', glow: '--role-chords-glow' },
  arpeggio: { note: '--role-arpeggio-strong', velocity: '--role-arpeggio-strong', glow: '--role-arpeggio-glow' },
  drums: { note: '--role-snare', velocity: '--role-drums', glow: '--role-snare-glow' },
};

@Component({
  selector: 'vc-score-canvas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="score-canvas-host" #rendererHost></div>
    <div class="horizontal-scroll" #horizontalScroll (scroll)="onHorizontalScroll()" tabindex="0" aria-label="Score horizontal scroll">
      <div #horizontalContent></div>
    </div>
    <div class="vertical-scroll" #verticalScroll (scroll)="onVerticalScroll()" tabindex="0" aria-label="Score vertical scroll">
      <div #verticalContent></div>
    </div>
  `,
  styles: `
    :host { display: grid; grid-column: 2; min-width: 0; min-height: 0; overflow: hidden; grid-template-columns: minmax(0, 1fr) 18px; grid-template-rows: minmax(0, 1fr) 18px; }
    .score-canvas-host { min-width: 0; min-height: 0; overflow: hidden; }
    .horizontal-scroll { grid-column: 1; grid-row: 2; overflow-x: scroll; overflow-y: hidden; }
    .horizontal-scroll > div { height: 1px; }
    .vertical-scroll { grid-column: 2; grid-row: 1; overflow-y: scroll; overflow-x: hidden; margin-bottom: 34px; }
    .vertical-scroll > div { width: 1px; }
  `,
})
export class ScoreCanvasComponent implements AfterViewInit, OnDestroy {
  readonly tracks = input.required<readonly ScoreCanvasTrack[]>();
  readonly notes = input.required<readonly ScoreCanvasNote[]>();
  readonly sections = input.required<readonly ScoreCanvasSection[]>();
  readonly totalBeats = input.required<number>();
  readonly playheadPercent = input.required<number>();
  readonly selectedTrackId = input.required<string>();
  readonly hiddenTrackIds = input.required<ReadonlySet<string>>();
  readonly sectionFocus = input<ScoreSectionFocus | null>(null);
  readonly noteSelected = output<ScoreCanvasNote>();
  readonly viewportChanged = output<ScoreViewport>();

  private readonly rendererHost = viewChild.required<ElementRef<HTMLDivElement>>('rendererHost');
  private readonly horizontalScroll = viewChild<ElementRef<HTMLDivElement>>('horizontalScroll');
  private readonly horizontalContent = viewChild<ElementRef<HTMLDivElement>>('horizontalContent');
  private readonly verticalScroll = viewChild<ElementRef<HTMLDivElement>>('verticalScroll');
  private readonly verticalContent = viewChild<ElementRef<HTMLDivElement>>('verticalContent');
  private staticGraphics?: PixiGraphics;
  private dynamicGraphics?: PixiGraphics;
  private colorClass?: typeof PixiColor;
  private readonly modelEffect = effect(() => {
    this.model = {
      tracks: this.tracks(),
      notes: this.notes(),
      sections: this.sections(),
      totalBeats: this.totalBeats(),
      selectedTrackId: this.selectedTrackId(),
      hiddenTrackIds: this.hiddenTrackIds(),
    };
    this.drawStatic();
  });
  private readonly playheadEffect = effect(() => {
    this.playhead = this.playheadPercent();
    this.drawDynamic();
  });
  private readonly sectionFocusEffect = effect(() => {
    this.pendingSectionFocus = this.sectionFocus();
    this.drawStatic();
  });

  private app?: PixiApplication;
  private resizeObserver?: ResizeObserver;
  private model?: ScoreCanvasModel;
  private hitAreas: NoteHitArea[] = [];
  private hitAreaBuckets = new Map<number, NoteHitArea[]>();
  private hitColumnCount = 0;
  private playhead = 0;
  private focusedNoteIndex = -1;
  private destroyed = false;
  private width = 0;
  private height = 0;
  private horizontalScale = 1;
  // Offset in fractions of the full song, independent of canvas size.
  private horizontalOffset = 0;
  private verticalScale = 1;
  private verticalOffset = 0;
  private pendingSectionFocus: ScoreSectionFocus | null = null;

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
    canvas?.removeEventListener('pointermove', this.onPointerMove);
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
      this.staticGraphics = new pixi.Graphics({ roundPixels: true });
      this.dynamicGraphics = new pixi.Graphics({ roundPixels: true });
      app = new pixi.Application();
      await app.init({
        width: Math.max(1, host.clientWidth),
        height: Math.max(1, host.clientHeight),
        backgroundAlpha: 0,
        antialias: false,
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
      canvas.style.display = 'block';
      canvas.style.width = '100%';
      canvas.style.height = '100%';
      canvas.style.outline = 'none';
      canvas.setAttribute('role', 'application');
      canvas.setAttribute('tabindex', '0');
      canvas.setAttribute('aria-keyshortcuts', 'ArrowLeft ArrowRight ArrowUp ArrowDown Enter');
      host.appendChild(canvas);
      canvas.addEventListener('click', this.onCanvasClick);
      canvas.addEventListener('pointermove', this.onPointerMove);
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
      const bounds = this.app.canvas.getBoundingClientRect();
      const cursorY = Math.min(this.scoreHeight, Math.max(0, (event.clientY - bounds.top) * this.height / Math.max(1, bounds.height)));
      const anchor = (this.verticalOffset + cursorY) / this.contentHeight;
      this.verticalScale = Math.min(8, Math.max(1, this.verticalScale * Math.exp(-delta * 0.002)));
      this.verticalOffset = anchor * this.contentHeight - cursorY;
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

  private get scoreHeight(): number { return Math.max(1, this.height - VELOCITY_HEIGHT); }
  private get contentHeight(): number { return Math.max(this.scoreHeight, (this.model?.tracks.length ?? 0) * 40) * this.verticalScale; }
  private get laneHeight(): number { return this.contentHeight / Math.max(1, this.model?.tracks.length ?? 0); }

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
    const area = this.hitAreaAt(event);
    this.focusedNoteIndex = area ? this.hitAreas.indexOf(area) : -1;
    this.describeCanvas(area?.note);
    this.drawDynamic();
    if (area) this.noteSelected.emit(area.note);
  };

  private readonly onPointerMove = (event: PointerEvent): void => {
    const canvas = this.app?.canvas;
    if (!canvas) return;
    const area = this.hitAreaAt(event);
    canvas.style.cursor = area ? 'pointer' : 'default';
    const trackName = area && this.model?.tracks.find((track) => track.id === area.note.part)?.name;
    const title = area ? `${trackName ?? area.note.color} · MIDI ${area.note.midi} · velocity ${area.note.velocity}` : '';
    if (canvas.title !== title) canvas.title = title;
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
    } else if (event.key === 'Enter' && this.focusedNoteIndex >= 0) {
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
    if (trackCount === 0 || y < 0 || y >= this.scoreHeight || x < 0 || x >= this.width) return undefined;
    const trackIndex = Math.floor((y + this.verticalOffset) / this.laneHeight);
    const column = Math.min(this.hitColumnCount - 1, Math.floor(x / HIT_BUCKET_WIDTH));
    const candidates = this.hitAreaBuckets.get(trackIndex * this.hitColumnCount + column) ?? [];
    for (let index = candidates.length - 1; index >= 0; index--) {
      const area = candidates[index];
      if (x >= area.x && x <= area.x + area.width && y >= area.y && y <= area.y + area.height) return area;
    }
    return undefined;
  }

  private resizeRenderer(): void {
    const host = this.rendererHost().nativeElement;
    const width = Math.floor(host.clientWidth);
    const height = Math.floor(host.clientHeight);
    if (width <= 0 || height <= 0) return;
    this.width = width;
    this.height = height;
    this.app?.renderer.resize(width, height);
    this.drawStatic();
  }

  private drawStatic(): void {
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
    this.updateViewport();
    const scoreHeight = this.scoreHeight;
    const laneHeight = this.laneHeight;
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
    for (let index = 0; index < tracks.length; index++) {
      const track = tracks[index];
      const y = index * laneHeight - this.verticalOffset;
      if (y + laneHeight <= 0 || y >= scoreHeight) continue;
      fill(0, y, this.width, laneHeight, track.id === selectedTrackId
        ? '--surface-low' : index % 2 === 0 ? '--score-row-odd' : '--score-row-even');
      const pitchStep = 12 * this.verticalScale;
      for (let bandY = y + pitchStep; bandY < Math.min(scoreHeight, y + laneHeight); bandY += pitchStep * 2) {
        if (bandY + pitchStep > 0) fill(0, bandY, this.width, Math.min(pitchStep, y + laneHeight - bandY), '--score-pitch-band');
      }
      for (let gridY = y + pitchStep; gridY < Math.min(scoreHeight, y + laneHeight); gridY += pitchStep) {
        if (gridY >= 0) line(0, Math.floor(gridY) + 0.5, this.width, Math.floor(gridY) + 0.5, '--grid-subdivision');
      }
      if (y + laneHeight <= scoreHeight) line(0, Math.floor(y + laneHeight) + 0.5, this.width, Math.floor(y + laneHeight) + 0.5, '--border-subtle');
    }

    const beatWidth = this.width * this.horizontalScale / totalBeats;
    const firstStep = Math.max(0, Math.floor(this.horizontalOffset * totalBeats * 4));
    const lastStep = Math.min(totalBeats * 4, Math.ceil((this.horizontalOffset + 1 / this.horizontalScale) * totalBeats * 4));
    for (let sixteenth = firstStep; sixteenth <= lastStep; sixteenth++) {
      const x = Math.floor(this.scoreX(sixteenth / 4)) + 0.5;
      const beat = sixteenth / 4;
      const gridColor = sixteenth % 16 === 0 ? '--grid-measure'
        : sixteenth % 4 === 0 ? '--grid-beat'
          : sixteenth % 2 === 0 ? '--grid-eighth' : '--grid-step';
      line(x, 0, x, this.height, gridColor, sixteenth % 16 === 0 ? 1 : 0.92);
      if (beat === totalBeats) break;
    }
    for (const section of sections) {
      const x = Math.floor(this.scoreX(section.startBeat)) + 0.5;
      if (x < 0 || x > this.width) continue;
      line(x, 0, x, scoreHeight, '--grid-measure');
    }
    line(0, scoreHeight + 0.5, this.width, scoreHeight + 0.5, '--border-default');
    line(0, 0.5, this.width, 0.5, '--border-default');
    line(0.5, 0, 0.5, this.height, '--border-default');
    line(this.width - 0.5, 0, this.width - 0.5, this.height, '--border-default');

    const trackIndexes = new Map(tracks.map((track, index) => [track.id, index]));
    for (const note of notes) {
      const trackIndex = trackIndexes.get(note.part);
      if (trackIndex === undefined || hiddenTrackIds.has(note.part)) continue;
      const x = this.scoreX(note.startBeat);
      const noteWidth = Math.max(3, note.durationBeats * beatWidth);
      if (x + noteWidth < 0 || x > this.width) continue;
      const centerY = trackIndex * laneHeight + note.topPercent / 100 * laneHeight - this.verticalOffset;
      const y = Math.max(0, centerY - 3);
      const noteHeight = Math.min(scoreHeight, centerY + 3) - y;
      const opacity = note.opacity * (selectedTrackId === note.part ? 1 : 0.72);
      const noteColors = COLORS[note.color];
      if (noteHeight > 0 && selectedTrackId === note.part) fill(x - 2, y - 2, noteWidth + 4, 10, noteColors.glow);
      const parsed = color(noteColors.note);
      if (noteHeight > 0) graphics.roundRect(x, y, noteWidth, noteHeight, Math.min(3, noteHeight / 2)).fill({ color: parsed.value, alpha: opacity * parsed.alpha });
      const velocityColor = color(noteColors.velocity);
      const velocityHeight = Math.max(2, note.velocity / 127 * VELOCITY_HEIGHT);
      graphics.rect(x, this.height - velocityHeight, 2, velocityHeight)
        .fill({ color: velocityColor.value, alpha: (selectedTrackId === note.part ? 0.9 : 0.32) * velocityColor.alpha });
      if (noteHeight <= 0) continue;
      const hitY = Math.max(0, trackIndex * laneHeight - this.verticalOffset, centerY - 6);
      const hitArea: NoteHitArea = {
        note,
        trackIndex,
        x: Math.max(0, x - 1),
        y: hitY,
        width: Math.min(this.width, x + Math.max(5, noteWidth + 2)) - Math.max(0, x - 1),
        height: Math.min(scoreHeight, (trackIndex + 1) * laneHeight - this.verticalOffset, centerY + 6) - hitY,
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

    this.focusedNoteIndex = focusedNote ? this.hitAreas.findIndex((area) => area.note.id === focusedNote.id) : -1;
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
    const x = (this.playhead / 100 - this.horizontalOffset) * this.width * this.horizontalScale;
    if (x >= 0 && x <= this.width) {
      graphics.moveTo(Math.floor(x) + 0.5, 0).lineTo(Math.floor(x) + 0.5, this.height)
        .stroke({ color: accent.toNumber(), alpha: accent.alpha, width: 1 });
    }
    const focused = this.hitAreas[this.focusedNoteIndex];
    if (focused) {
      graphics.roundRect(focused.x - 1, focused.y - 1, focused.width + 2, focused.height + 2, 3)
        .stroke({ color: accent.toNumber(), alpha: 0.95, width: 1 });
    }
    this.app.render();
  }
}
