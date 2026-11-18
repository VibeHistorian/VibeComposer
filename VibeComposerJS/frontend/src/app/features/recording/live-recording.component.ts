import { ChangeDetectionStrategy, Component, input, output, computed, ElementRef, inject, signal } from '@angular/core';
import { AudioPlaybackService, type RecordingBackingContext, type RecordingBackingSession } from '../../core/audio/audio-playback.service';
import { DOCUMENT } from '@angular/common';
import { ProjectService } from '../../core/project/project.service';
import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { MidiInputService, type LiveMidiNote } from '../../core/audio/midi-input.service';
import { LiveRecordingService } from '../../core/audio/live-recording.service';
import { keyboardCode, keyboardMidi, recordingPosition, recordingTickAtOrAfter, nextRecordingTick, type RecordedSpan } from '../../core/audio/live-recording';
import { shortcutLabel } from '../../core/ui/shortcuts';
import { isEditingText } from '../../shared/keyboard-shortcuts';
import { SelectComponent } from '../../shared/select.component';
import { CompactToggleComponent } from '../../shared/compact-toggle.component';
@Component({ selector: 'vc-live-recording', imports: [SelectComponent, CompactToggleComponent], templateUrl: './live-recording.component.html', styleUrl: './live-recording.component.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class LiveRecordingComponent {
  readonly beats = input(4);
  readonly measureBeats = input(4);
  readonly startBeat = input(0);
  readonly countInBeats = computed(() => Math.max(2, this.measureBeats()));
  readonly armed = computed(() => this.notes() && this.preferences.preferences().recordingMode === 'armed');
  readonly waiting = signal(false);
  readonly cancelled = output<void>();
  readonly recordingModes = [{ value: 'count-in', label: 'Count-in' }, { value: 'armed', label: 'Armed count-in' }];
  private offset = 0;
  readonly backingContext = input<RecordingBackingContext | null>(null);
  readonly preparingBacking = signal(false);
  readonly recordingError = signal('');
  private backing?: RecordingBackingSession;
  private readonly playback = inject(AudioPlaybackService);
  readonly keyboardScale = input<readonly number[]>([]);
  readonly keyboardTonic = input<number | undefined>(undefined);
  readonly loopBeats = computed(() => Math.max(1 / 960, this.beats()));
  readonly displayBeat = computed(() => Math.floor(this.beat()) + 1);
  readonly beatCount = computed(() => Math.ceil(this.loopBeats()));
  readonly notes = input(false);
  readonly acceptRecordingPitch = input<((midi: number) => boolean) | null>(null);
  readonly preferActionShortcut = input<((event: KeyboardEvent) => boolean) | null>(null);
  readonly disabled = input(false);
  readonly heldNotes = output<readonly (RecordedSpan & { id: string })[]>();
  readonly noteInput = output<LiveMidiNote>();
  readonly countingIn = signal(0);
  private started = false;
  readonly began = output<void>();
  readonly ended = output<void>();
  readonly span = output<RecordedSpan>();
  readonly cursor = output<{ beat: number; held: boolean }>();
  readonly shortcutLabel = shortcutLabel;
  readonly running = signal(false);
  readonly beat = signal(0);
  readonly preferences = inject(UiPreferencesService);
  private readonly projects = inject(ProjectService);
  private readonly document = inject(DOCUMENT);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private stopInput?: () => void;
  ngOnInit(): void { if (this.notes()) this.attachInput(); }
  private attachInput(): void {
    if (this.stopInput) return;
    this.stopInput = this.midi.listen(this.input);
    window.addEventListener('keydown', this.keydown, true); window.addEventListener('keyup', this.keyup, true);
    window.addEventListener('blur', this.stop); window.addEventListener('pagehide', this.stop);
  }
  private detachInput(): void {
    this.stopInput?.(); this.stopInput = undefined;
    window.removeEventListener('keydown', this.keydown, true); window.removeEventListener('keyup', this.keyup, true);
    window.removeEventListener('blur', this.stop); window.removeEventListener('pagehide', this.stop);
  }
  private activeContext(): boolean {
    const dialogs = this.document?.querySelectorAll?.('[role="dialog"]');
    const topDialog = dialogs?.[dialogs.length - 1];
    return !topDialog || topDialog.contains(this.element.nativeElement);
  }
  private readonly midi = inject(MidiInputService);
  private readonly session = inject(LiveRecordingService);
  private held = new Map<string, { start: number; midi: number; velocity: number }>();
  private frame = 0;
  private lastTime = 0;
  private elapsed = 0;
  private audio?: AudioContext;
  private tickTimer?: ReturnType<typeof setInterval>;
  private nextTick = 0;
  private countInPulses: number[] = [];
  private flashUntil = 0;
  toggle(): void {
    if (this.running()) { this.stop(); return; }
    if (this.disabled()) return;
    this.recordingError.set('');
    if (this.notes()) this.playback?.pause?.();
    this.session.stop?.(); this.releaseAll(0); this.attachInput(); this.session.stop = this.stop;
    const positionedBeat = this.startBeat();
    this.offset = this.armed() && positionedBeat >= 0 && positionedBeat < this.loopBeats() ? positionedBeat : 0;
    if (this.notes()) {
      const context = this.backingContext();
      if (context) this.playback.seek(context.project, context.startBeat + this.offset);
      this.session.phraseBeat.set(this.offset);
    }
    this.waiting.set(this.armed());
    this.running.set(true); this.elapsed = this.notes() || this.preferences.preferences().recordingCountIn ? -this.countInBeats() : 0;
    this.started = false; this.countingIn.set(-this.elapsed); this.beat.set(0); this.lastTime = performance.now(); this.nextTick = this.elapsed;
    {
      try { this.audio = new AudioContext(); void this.audio.resume(); this.tickTimer = setInterval(this.tick, 25); this.tick(); } catch { /* Visual recording remains available. */ }
    }
    if (this.elapsed >= 0) this.startRecording();

    this.frame = requestAnimationFrame(this.advance);
  }
  private startRecording(): void {
    if (this.started || this.preparingBacking() || this.waiting()) return;
    const context = this.backingContext();
    if (this.notes() && context && this.preferences.preferences().recordingOtherTracks) {
      this.preparingBacking.set(true);
      const backing = this.playback.beginRecordingBacking({ ...context, captureBeat: this.armed() ? () => { this.time(); return this.offset + this.elapsed; } : undefined }, this.loopBeats()); this.backing = backing;
      void backing.ready.then(() => {
        if (!this.running() || this.backing !== backing) return;
        this.elapsed = 0; this.lastTime = performance.now(); this.nextTick = this.offset; this.preparingBacking.set(false); if (!this.started) this.beginCapture();
      }).catch(error => {
        if (this.backing !== backing) return;
        this.recordingError.set(error instanceof Error ? error.message : 'Backing playback could not start.'); this.stop();
      });
      return;
    }
    this.beginCapture();
  }
  private beginCapture(): void {
    this.clearCountInFlash();
    this.started = true; this.countingIn.set(0);
    if (this.notes()) this.session.phraseBeat.set(this.offset);
    this.began.emit();
  }
  private publishHeld(beat: number): void {
    this.heldNotes.emit(this.running() ? [...this.held].map(([id, held]) => ({ ...held, id, end: beat })) : []);
  }
  private time(): number {
    if (this.backing && this.started && !this.preparingBacking()) return this.backing.beat();
    const now = performance.now();
    this.elapsed += (now - this.lastTime) / 60000 * this.projects.project().tempoBpm;
    this.lastTime = now;
    if (this.waiting() && this.elapsed >= 0) {
      this.elapsed = -this.countInBeats() + recordingPosition(this.elapsed, this.countInBeats()); this.nextTick = recordingTickAtOrAfter(this.elapsed + this.countInBeats(), Math.max(1 / 960, this.measureBeats())) - this.countInBeats();
    }
    return this.elapsed + (this.started ? this.offset : 0);
  }
  private readonly advance = (): void => {
    if (!this.running()) return;
    if (this.backing && this.started && !this.preparingBacking() && !this.backing.active()) { this.stop(); return; }
    this.updateCountInFlash();
    const beat = this.time();
    if (beat < 0) { this.countingIn.set(Math.ceil(-beat)); this.frame = requestAnimationFrame(this.advance); return; }
    this.startRecording();
    if (!this.started) { this.frame = requestAnimationFrame(this.advance); return; }
    this.beat.set(recordingPosition(beat, this.loopBeats())); this.publishHeld(beat);
    if (this.notes()) this.session.phraseBeat.set(recordingPosition(beat, this.loopBeats()));
    this.cursor.emit({ beat, held: this.held.size > 0 });
    this.frame = requestAnimationFrame(this.advance);
  };
  /** Audio lookahead scheduler runs independently of Angular rendering. */
  private readonly tick = (): void => {
    if (!this.audio || this.preparingBacking()) return;
    const tickLength = Math.max(1 / 960, this.measureBeats());
    const beat = this.time(), bpm = this.projects.project().tempoBpm, ahead = 0.12 * bpm / 60;
    if (beat >= 0 && this.notes() && this.backingContext() && this.preferences.preferences().recordingOtherTracks && !this.started) return;
    const tickOrigin = this.started ? 0 : -this.countInBeats();
    if (this.nextTick < beat - ahead) this.nextTick = recordingTickAtOrAfter(beat - tickOrigin, tickLength) + tickOrigin;
    while (this.nextTick <= beat + ahead) {
      if (this.nextTick >= 0 && !this.waiting() && !this.preferences.preferences().recordingTick) { this.nextTick = this.followingTick(this.nextTick, tickOrigin, tickLength); continue; }
      const at = this.audio.currentTime + Math.max(0, (this.nextTick - beat) * 60 / bpm);
      const osc = this.audio.createOscillator(), gain = this.audio.createGain();
      const phase = !this.started && Math.abs(this.nextTick) < 1e-7 ? 0
        : recordingPosition(this.nextTick + (this.started ? 0 : this.countInBeats()), tickLength);
      osc.frequency.value = Math.min(phase, tickLength - phase) < 1e-7 ? 1200 : 800;
      gain.gain.setValueAtTime(0.12, at); gain.gain.exponentialRampToValueAtTime(0.001, at + 0.04);
      if (this.notes() && !this.started) this.countInPulses.push(at);
      osc.connect(gain); gain.connect(this.audio.destination); osc.start(at); osc.stop(at + 0.05); this.nextTick = this.followingTick(this.nextTick, tickOrigin, tickLength);
    }
  };
  private followingTick(beat: number, origin: number, length: number): number {
    const next = nextRecordingTick(beat - origin, length) + origin;
    // The minimum two-beat count-in can end partway through a very short measure.
    return !this.started && beat < 0 ? Math.min(0, next) : next;
  }
  private updateCountInFlash(): void {
    if (!this.audio || !this.notes()) return;
    const now = this.audio.currentTime;
    while (this.countInPulses.length && this.countInPulses[0] <= now) {
      this.flashUntil = this.countInPulses.shift()! + 0.1;
    }
    this.session.countInFlash.set(now < this.flashUntil);
  }
  private clearCountInFlash(): void {
    this.countInPulses = []; this.flashUntil = 0;
    this.session.countInFlash.set(false);
  }
  private readonly input = (note: LiveMidiNote): void => {
    if (!this.running() && !this.notes()) return;
    if (note.down && this.running() && this.acceptRecordingPitch()?.(note.midi) === false) return;
    let beat = this.running() ? this.time() : 0;
    if (note.down && this.waiting() && this.activeContext()) {
      this.waiting.set(false); this.elapsed = 0; this.lastTime = performance.now(); this.nextTick = this.offset;
      this.startRecording();
      // Keep the triggering note even if backing playback is still preparing.
      if (!this.started) this.beginCapture();
      beat = this.offset;
    }
    if (note.id === '*') { this.releaseAll(beat); return; }
    if (note.down && (!this.activeContext() || beat < 0)) return;
    if (this.running() && beat >= 0) this.startRecording();
    if (this.running() && !this.started) return;
    if (note.down) {
      if (!this.held.has(note.id)) { this.held.set(note.id, { start: beat, midi: note.midi, velocity: note.velocity }); this.noteInput.emit(note); this.publishHeld(beat); if (this.running()) this.cursor.emit({ beat, held: true }); }
    } else {
      const held = this.held.get(note.id);
      if (held) { this.held.delete(note.id); this.noteInput.emit(note); if (this.running()) this.span.emit({ ...held, end: beat }); this.publishHeld(beat); if (this.running()) this.cursor.emit({ beat, held: this.held.size > 0 }); }
    }
  };
  private readonly keydown = (event: KeyboardEvent): void => {
    if (!this.midi.ownsInput(this.input) || !this.activeContext()) return;
    if (event.defaultPrevented || event.isComposing || isEditingText(event) || this.preferences.capturingShortcut()) return;
    if (this.notes() && (this.preferences.matchesShortcut('togglePhraseRecording', event) || this.preferences.matchesShortcut('cancelPhraseRecording', event))) {
      event.preventDefault(); event.stopImmediatePropagation();
      if (!event.repeat) {
        if (this.preferences.matchesShortcut('cancelPhraseRecording', event)) { this.stop(); this.cancelled.emit(); }
        else this.toggle();
      }
      return;
    }
    if (this.notes() && !this.running() && this.preferActionShortcut()?.(event)) return;
    const code = keyboardCode(event.code, event.key);
    const midi = this.notes() && this.preferences.preferences().keyboardNoteInput && !event.ctrlKey && !event.metaKey && !event.altKey ? keyboardMidi(code, event.shiftKey, this.preferences.preferences().keyboardMatchScale ? this.keyboardScale() : [], this.keyboardTonic()) : null;
    if (this.notes() ? midi === null : !this.preferences.matchesShortcut('recordRhythm', event)) return;
    event.preventDefault(); event.stopImmediatePropagation();
    if (!event.repeat) {
      // A fresh press is also a recovery point if the host lost the preceding release.
      if (this.held.has(code)) this.input({ id: code, midi: 0, velocity: 0, down: false });
      this.input({ id: code, midi: midi ?? 60, velocity: 90, down: true });
    }
  };
  private readonly keyup = (event: KeyboardEvent): void => {
    const code = keyboardCode(event.code, event.key);
    if (!this.held.has(code)) return;
    event.preventDefault(); event.stopImmediatePropagation(); this.input({ id: code, midi: 0, velocity: 0, down: false });
  };
  private releaseAll(beat: number): void {
    for (const [id, held] of this.held) { this.noteInput.emit({ id, midi: held.midi, velocity: 0, down: false }); if (this.running()) this.span.emit({ ...held, end: beat }); }
    this.held.clear(); this.publishHeld(beat);
    if (this.started) this.cursor.emit({ beat, held: false });
  }
  readonly stop = (): void => {
    if (!this.running()) { this.releaseAll(0); return; }
    this.releaseAll(this.time()); this.running.set(false); this.waiting.set(false); this.countingIn.set(0); cancelAnimationFrame(this.frame); clearInterval(this.tickTimer);
    this.backing?.stop(); this.backing = undefined; this.preparingBacking.set(false);
    if (!this.notes()) this.detachInput();
    if (this.session.stop === this.stop) { this.session.stop = undefined; this.session.phraseBeat.set(null); }
    this.clearCountInFlash();
    void this.audio?.close(); this.audio = undefined; if (this.started) this.ended.emit(); this.started = false;
  };
  ngOnDestroy(): void { this.stop(); this.detachInput(); }
}
