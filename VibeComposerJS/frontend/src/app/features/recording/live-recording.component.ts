import { ChangeDetectionStrategy, Component, input, output, computed, inject, signal } from '@angular/core';
import { ProjectService } from '../../core/project/project.service';
import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { MidiInputService, type LiveMidiNote } from '../../core/audio/midi-input.service';
import { LiveRecordingService } from '../../core/audio/live-recording.service';
import { keyboardMidi, recordingPosition, recordingTickAtOrAfter, nextRecordingTick, type RecordedSpan } from '../../core/audio/live-recording';
import { shortcutLabel } from '../../core/ui/shortcuts';
import { isEditingText } from '../../shared/keyboard-shortcuts';
@Component({ selector: 'vc-live-recording', templateUrl: './live-recording.component.html', styleUrl: './live-recording.component.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class LiveRecordingComponent {
  readonly beats = input(4);
  readonly keyboardScale = input<readonly number[]>([]);
  readonly keyboardTonic = input<number | undefined>(undefined);
  readonly loopBeats = computed(() => Math.max(1 / 960, this.beats()));
  readonly displayBeat = computed(() => Math.floor(this.beat()) + 1);
  readonly beatCount = computed(() => Math.ceil(this.loopBeats()));
  readonly notes = input(false);
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
  private readonly midi = inject(MidiInputService);
  private readonly session = inject(LiveRecordingService);
  private held = new Map<string, { start: number; midi: number; velocity: number }>();
  private frame = 0;
  private lastTime = 0;
  private elapsed = 0;
  private audio?: AudioContext;
  private tickTimer?: ReturnType<typeof setInterval>;
  private nextTick = 0;
  toggle(): void {
    if (this.running()) { this.stop(); return; }
    if (this.disabled()) return;
    this.session.stop?.(); this.session.stop = this.stop;
    this.running.set(true); this.elapsed = this.preferences.preferences().recordingCountIn ? -this.loopBeats() : 0;
    this.started = false; this.countingIn.set(-this.elapsed); this.beat.set(0); this.lastTime = performance.now(); this.nextTick = this.elapsed;
    {
      try { this.audio = new AudioContext(); void this.audio.resume(); this.tickTimer = setInterval(this.tick, 25); this.tick(); } catch { /* Visual recording remains available. */ }
    }
    if (this.elapsed >= 0) this.startRecording();
    this.midi.listener = this.input;
    window.addEventListener('keydown', this.keydown, true); window.addEventListener('keyup', this.keyup, true);
    window.addEventListener('blur', this.stop); window.addEventListener('pagehide', this.stop);
    this.frame = requestAnimationFrame(this.advance);
  }
  private startRecording(): void {
    if (this.started) return;
    this.started = true; this.countingIn.set(0);
    if (this.notes()) this.session.phraseBeat.set(0);
    this.began.emit();
  }
  private publishHeld(beat: number): void {
    this.heldNotes.emit([...this.held].map(([id, held]) => ({ ...held, id, end: beat })));
  }
  private time(): number {
    const now = performance.now();
    this.elapsed += (now - this.lastTime) / 60000 * this.projects.project().tempoBpm;
    this.lastTime = now; return this.elapsed;
  }
  private readonly advance = (): void => {
    if (!this.running()) return;
    const beat = this.time();
    if (beat < 0) { this.countingIn.set(Math.ceil(-beat)); this.frame = requestAnimationFrame(this.advance); return; }
    this.startRecording(); this.beat.set(recordingPosition(beat, this.loopBeats())); this.publishHeld(beat);
    if (this.notes()) this.session.phraseBeat.set(recordingPosition(beat, this.loopBeats()));
    this.cursor.emit({ beat, held: this.held.size > 0 });
    this.frame = requestAnimationFrame(this.advance);
  };
  /** Audio lookahead scheduler runs independently of Angular rendering. */
  private readonly tick = (): void => {
    if (!this.audio) return;
    const beat = this.time(), bpm = this.projects.project().tempoBpm, ahead = 0.12 * bpm / 60;
    if (this.nextTick < beat - ahead) this.nextTick = recordingTickAtOrAfter(beat, this.loopBeats());
    while (this.nextTick <= beat + ahead) {
      if (this.nextTick >= 0 && !this.preferences.preferences().recordingTick) { this.nextTick = nextRecordingTick(this.nextTick, this.loopBeats()); continue; }
      const at = this.audio.currentTime + Math.max(0, (this.nextTick - beat) * 60 / bpm);
      const osc = this.audio.createOscillator(), gain = this.audio.createGain();
      const phase = recordingPosition(this.nextTick, this.loopBeats());
      osc.frequency.value = Math.min(phase, this.loopBeats() - phase) < 1e-7 ? 1200 : 800;
      gain.gain.setValueAtTime(0.12, at); gain.gain.exponentialRampToValueAtTime(0.001, at + 0.04);
      osc.connect(gain); gain.connect(this.audio.destination); osc.start(at); osc.stop(at + 0.05); this.nextTick = nextRecordingTick(this.nextTick, this.loopBeats());
    }
  };
  private readonly input = (note: LiveMidiNote): void => {
    if (!this.running()) return;
    const beat = this.time();
    if (beat < 0) return;
    this.startRecording();
    if (note.id === '*') { this.releaseAll(beat); return; }
    if (note.down) {
      if (!this.held.has(note.id)) { this.held.set(note.id, { start: beat, midi: note.midi, velocity: note.velocity }); this.noteInput.emit(note); this.publishHeld(beat); this.cursor.emit({ beat, held: true }); }
    } else {
      const held = this.held.get(note.id);
      if (held) { this.held.delete(note.id); this.noteInput.emit(note); this.span.emit({ ...held, end: beat }); this.publishHeld(beat); this.cursor.emit({ beat, held: this.held.size > 0 }); }
    }
  };
  private readonly keydown = (event: KeyboardEvent): void => {
    if (event.defaultPrevented || event.isComposing || isEditingText(event) || this.preferences.capturingShortcut()) return;
    const midi = this.notes() && this.preferences.preferences().keyboardNoteInput && !event.ctrlKey && !event.metaKey && !event.altKey ? keyboardMidi(event.code, event.shiftKey, this.preferences.preferences().keyboardMatchScale ? this.keyboardScale() : [], this.keyboardTonic()) : null;
    if (this.notes() ? midi === null : !this.preferences.matchesShortcut('recordRhythm', event)) return;
    event.preventDefault(); event.stopImmediatePropagation();
    if (!event.repeat) this.input({ id: event.code, midi: midi ?? 60, velocity: 90, down: true });
  };
  private readonly keyup = (event: KeyboardEvent): void => {
    if (!this.held.has(event.code)) return;
    event.preventDefault(); event.stopImmediatePropagation(); this.input({ id: event.code, midi: 0, velocity: 0, down: false });
  };
  private releaseAll(beat: number): void {
    for (const [id, held] of this.held) { this.noteInput.emit({ id, midi: held.midi, velocity: 0, down: false }); this.span.emit({ ...held, end: beat }); }
    this.held.clear(); this.publishHeld(beat);
    if (this.started) this.cursor.emit({ beat, held: false });
  }
  readonly stop = (): void => {
    if (!this.running()) return;
    this.releaseAll(this.time()); this.running.set(false); this.countingIn.set(0); cancelAnimationFrame(this.frame); clearInterval(this.tickTimer);
    if (this.midi.listener === this.input) this.midi.listener = undefined;
    if (this.session.stop === this.stop) { this.session.stop = undefined; this.session.phraseBeat.set(null); }
    window.removeEventListener('keydown', this.keydown, true); window.removeEventListener('keyup', this.keyup, true);
    window.removeEventListener('blur', this.stop); window.removeEventListener('pagehide', this.stop);
    void this.audio?.close(); this.audio = undefined; if (this.started) this.ended.emit(); this.started = false;
  };
  ngOnDestroy(): void { this.stop(); }
}
