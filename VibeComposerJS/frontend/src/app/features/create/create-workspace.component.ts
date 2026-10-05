import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { getDiatonicChords, KEYS } from '../../core/music/harmony';
import type { DiatonicChord, ScaleMode } from '../../core/music/harmony';
import { ProjectService } from '../../core/project/project.service';

@Component({
  selector: 'vc-create-workspace',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="create-workspace">
      <section class="page-heading" aria-labelledby="page-title">
        <div>
          <p class="eyebrow"><span class="eyebrow-line"></span> CREATE WORKSPACE</p>
          <h1 id="page-title">Start with a progression.</h1>
          <p class="page-copy">Set a tonal center, shape the chords, and build from there.</p>
        </div>
        <div class="project-actions" aria-label="Project history">
          <button type="button" class="icon-button" [disabled]="!projects.canUndo()" (click)="projects.undo()" aria-label="Undo" title="Undo">↶</button>
          <button type="button" class="icon-button" [disabled]="!projects.canRedo()" (click)="projects.redo()" aria-label="Redo" title="Redo">↷</button>
        </div>
      </section>

      <div class="create-layout">
        <section class="progression-panel" aria-labelledby="progression-title">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">HARMONY</p>
              <h2 id="progression-title">Chord progression</h2>
            </div>
            <span class="chord-count">{{ project().progression.length }} CHORDS</span>
          </div>

          <div class="progression-track">
            @for (degree of project().progression; track $index; let index = $index) {
              <article class="chord-card">
                <div class="chord-card-top">
                  <span class="slot-label">{{ (index + 1).toString().padStart(2, '0') }}</span>
                  <button
                    type="button"
                    class="remove-button"
                    [disabled]="project().progression.length <= 1"
                    (click)="projects.removeChord(index)"
                    [attr.aria-label]="'Remove chord ' + (index + 1)">×</button>
                </div>
                <strong>{{ chordFor(degree)?.symbol }}</strong>
                <span class="roman-label">{{ chordFor(degree)?.romanNumeral }}</span>
                <label class="visually-hidden" [for]="'chord-' + index">Chord {{ index + 1 }}</label>
                <select [id]="'chord-' + index" [value]="degree" (change)="changeChord(index, $event)">
                  @for (chord of chords(); track chord.degree) {
                    <option [value]="chord.degree">{{ chord.romanNumeral }} · {{ chord.symbol }}</option>
                  }
                </select>
              </article>
            }
            <button type="button" class="add-chord" (click)="projects.addChord()">
              <span aria-hidden="true">＋</span>
              <span>Add chord</span>
            </button>
          </div>

          <div class="progression-footer">
            <span>DIATONIC CHORDS <b>·</b> {{ project().key }} {{ project().scale === 'major' ? 'MAJOR' : 'MINOR' }}</span>
            <button type="button" class="text-button" (click)="projects.resetProgression()">Reset to starter</button>
          </div>
        </section>

        <aside class="inspector" aria-labelledby="settings-title">
          <div class="inspector-heading">
            <p class="eyebrow">PROJECT</p>
            <h2 id="settings-title">Composition setup</h2>
          </div>

          <label class="field-label" for="project-name">Name</label>
          <input id="project-name" class="text-input" maxlength="60" [value]="project().name" (change)="updateName($event)" />

          <div class="field-row">
            <div class="field-group">
              <label class="field-label" for="key">Key</label>
              <select id="key" class="select-input" [value]="project().key" (change)="updateKey($event)">
                @for (key of keys; track key) { <option [value]="key">{{ key }}</option> }
              </select>
            </div>
            <div class="field-group">
              <label class="field-label" for="scale">Scale</label>
              <select id="scale" class="select-input" [value]="project().scale" (change)="updateScale($event)">
                <option value="major">Major</option>
                <option value="natural-minor">Natural minor</option>
              </select>
            </div>
          </div>

          <label class="field-label" for="tempo">Tempo</label>
          <div class="tempo-control">
            <input id="tempo" class="text-input tempo-input" type="number" min="40" max="240" step="1"
              [value]="project().tempoBpm" (change)="updateTempo($event)" />
            <span>BPM</span>
          </div>

          <label class="field-label" for="seed">Generation seed</label>
          <input id="seed" class="text-input seed-input" inputmode="text" [value]="project().seed" (change)="updateSeed($event)" />
          <p class="field-help">Stored as decimal text to preserve Java's full 64-bit seed range.</p>
        </aside>
      </div>

      <section class="next-step" aria-label="Next step">
        <div class="next-icon" aria-hidden="true">♪</div>
        <div>
          <span class="eyebrow">NEXT</span>
          <p>Choose a chord, then generation controls for bass, chords, arpeggios, and drums will live here.</p>
        </div>
        <span class="next-status">IN PROGRESS</span>
      </section>
    </main>
  `,
  styles: [`
    :host { display: block; }
    .create-workspace { padding: 46px 0 34px; }
    .page-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 27px; }
    .eyebrow { margin: 0 0 10px; color: #8e9184; font: 9px 'DM Mono', monospace; letter-spacing: .12em; }
    .page-heading .eyebrow { color: var(--accent); }
    h1 { margin: 0; font-size: clamp(29px, 4vw, 42px); line-height: 1.08; letter-spacing: -.06em; font-weight: 500; }
    h2 { margin: 0; font-size: 17px; font-weight: 500; letter-spacing: -.035em; }
    .page-copy { margin: 12px 0 0; color: var(--muted); font-size: 13px; }
    .project-actions { display: flex; gap: 7px; }
    button, input, select { font: inherit; }
    button { color: inherit; cursor: pointer; }
    button:disabled { opacity: .35; cursor: default; }
    .icon-button { width: 37px; height: 35px; border: 1px solid var(--line); border-radius: 7px; background: #191b16; color: #b7b9ad; font-size: 20px; }
    .create-layout { display: grid; grid-template-columns: minmax(0, 1fr) 270px; align-items: start; gap: 14px; }
    .progression-panel, .inspector, .next-step { border: 1px solid #30332b; border-radius: 11px; background: linear-gradient(145deg, #191b16, #151713 62%); }
    .panel-heading { min-height: 80px; padding: 17px 20px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--line); }
    .panel-heading .eyebrow, .inspector-heading .eyebrow { margin-bottom: 6px; }
    .chord-count { color: #777a70; font: 9px 'DM Mono', monospace; letter-spacing: .1em; }
    .progression-track { min-height: 251px; padding: 21px; display: flex; align-items: stretch; gap: 10px; overflow-x: auto; }
    .chord-card { min-width: 130px; flex: 1 1 130px; min-height: 207px; padding: 12px; display: flex; flex-direction: column; align-items: flex-start; border: 1px solid #393d31; border-radius: 8px; background: #20221b; }
    .chord-card-top { width: 100%; display: flex; align-items: center; justify-content: space-between; }
    .slot-label { color: #72756b; font: 9px 'DM Mono', monospace; letter-spacing: .08em; }
    .remove-button { width: 23px; height: 23px; padding: 0; border: 0; border-radius: 5px; background: transparent; color: #84877c; font-size: 19px; }
    .remove-button:hover:not(:disabled) { background: #34372d; color: #f1f1e9; }
    .chord-card strong { margin: auto 0 2px; color: var(--accent); font-size: 27px; font-weight: 500; letter-spacing: -.06em; }
    .roman-label { color: #a2a497; font-size: 11px; }
    .chord-card select { width: 100%; margin-top: 14px; padding: 8px 7px; border: 1px solid #383b32; border-radius: 5px; background: #191b16; color: #d8d9d0; font-size: 10px; }
    .add-chord { min-width: 105px; padding: 12px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; border: 1px dashed #3c4035; border-radius: 8px; background: transparent; color: #96998c; font-size: 11px; }
    .add-chord:hover { border-color: #6c7652; color: var(--accent); }
    .add-chord span:first-child { font-size: 20px; }
    .progression-footer { min-height: 43px; padding: 0 17px; display: flex; justify-content: space-between; align-items: center; gap: 14px; border-top: 1px solid var(--line); color: #777a70; font: 8px 'DM Mono', monospace; letter-spacing: .08em; }
    .progression-footer b { color: var(--accent); font-weight: 400; }
    .text-button { padding: 5px 0; border: 0; background: transparent; color: #aeb0a4; font: 10px 'DM Sans', sans-serif; letter-spacing: 0; }
    .text-button:hover { color: var(--accent); }
    .inspector { padding: 18px; }
    .inspector-heading { margin-bottom: 19px; }
    .field-label { display: block; margin: 15px 0 7px; color: #b6b8ac; font-size: 10px; }
    .text-input, .select-input { width: 100%; height: 36px; padding: 0 10px; border: 1px solid #373a31; border-radius: 6px; outline: none; background: #131510; color: #e6e7de; font-size: 11px; }
    .text-input:focus, .select-input:focus, .chord-card select:focus { border-color: #8fa552; }
    .field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }
    .tempo-control { position: relative; }
    .tempo-input { padding-right: 52px; }
    .tempo-control span { position: absolute; top: 11px; right: 11px; color: #74776d; font: 9px 'DM Mono', monospace; }
    .seed-input { font-family: 'DM Mono', monospace; }
    .field-help { margin: 7px 0 0; color: #777a70; font-size: 9px; line-height: 1.5; }
    .next-step { margin-top: 14px; min-height: 76px; padding: 15px 18px; display: flex; align-items: center; gap: 13px; }
    .next-icon { width: 36px; height: 36px; flex: 0 0 36px; display: grid; place-items: center; border-radius: 9px; background: #292d20; color: var(--accent); font-size: 19px; }
    .next-step .eyebrow { margin-bottom: 4px; color: var(--accent); }
    .next-step p { margin: 0; color: #a0a296; font-size: 10px; line-height: 1.45; }
    .next-status { margin-left: auto; color: #797c71; white-space: nowrap; font: 8px 'DM Mono', monospace; letter-spacing: .08em; }
    .visually-hidden { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
    @media (max-width: 800px) { .create-layout { grid-template-columns: 1fr; } .inspector { display: grid; grid-template-columns: 1fr 1fr; column-gap: 13px; } .inspector-heading { grid-column: 1 / -1; } .inspector .field-label { margin-top: 10px; } }
    @media (max-width: 560px) { .create-workspace { padding-top: 32px; } .progression-track { padding: 13px; } .chord-card { min-width: 115px; } .inspector { display: block; } .next-status { display: none; } .progression-footer { align-items: flex-start; flex-direction: column; justify-content: center; padding: 9px 13px; } }
  `],
})
export class CreateWorkspaceComponent {
  readonly projects = inject(ProjectService);
  readonly project = this.projects.project;
  readonly keys = KEYS;
  readonly chords = computed(() => getDiatonicChords(this.project().key, this.project().scale));

  chordFor(degree: number): DiatonicChord | undefined {
    return this.chords().find((chord) => chord.degree === degree);
  }

  changeChord(index: number, event: Event): void {
    const degree = Number(this.valueFrom(event));
    this.projects.setChordDegree(index, degree);
  }

  updateName(event: Event): void {
    this.projects.updateSettings({ name: this.valueFrom(event).trim() || 'Untitled composition' });
  }

  updateKey(event: Event): void {
    this.projects.updateSettings({ key: this.valueFrom(event) });
  }

  updateScale(event: Event): void {
    const scale = this.valueFrom(event);
    if (scale === 'major' || scale === 'natural-minor') {
      this.projects.updateSettings({ scale: scale as ScaleMode });
    }
  }

  updateTempo(event: Event): void {
    const tempo = Number(this.valueFrom(event));
    if (Number.isInteger(tempo) && tempo >= 40 && tempo <= 240) {
      this.projects.updateSettings({ tempoBpm: tempo });
    } else {
      (event.target as HTMLInputElement).value = String(this.project().tempoBpm);
    }
  }

  updateSeed(event: Event): void {
    const seed = this.valueFrom(event).trim();
    if (/^-?\d+$/.test(seed)) {
      const value = BigInt(seed);
      if (value >= -(1n << 63n) && value <= (1n << 63n) - 1n) {
        this.projects.updateSettings({ seed: value.toString() });
        return;
      }
    }
    (event.target as HTMLInputElement).value = this.project().seed;
  }

  private valueFrom(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }
}
