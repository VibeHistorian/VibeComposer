import { TooltipDirective } from '../../shared/tooltip.directive';
import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { ProjectService } from '../../core/project/project.service';
import {
  BASE_KEY, CHORD_SPICES, CHORD_SPICE_GROUPS, KEYS, MAX_CHORD_INTERVAL, MAX_CHORD_TONES, SCALE_INTERVALS,
  chordIntervals, chordKeyOverlap, chordRomanNumeral, chordRootHue, effectiveKey, inversionSuffix, progressionChords,
  scaleIntervalChord, stepChordInversion, type ProgressionChord,
} from '../../core/music/harmony';
import type { ArrangementSection } from '../../core/project/arrangement.model';
import { baseGenerationHarmony, sectionGenerationHarmony } from '../../core/music/project-harmony';
import { sectionProject } from '../../core/music/section-context';
import { SCALE_DEFINITIONS } from '../../core/music/scales';
import { NumericSequenceDialogComponent, type NumericSequenceDialogData } from '../../shared/numeric-sequence-dialog.component';

@Component({
  selector: 'vc-chordlet-editor', changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TooltipDirective],
  templateUrl: './chordlet-editor.component.html', styleUrl: './chordlet-editor.component.css',
  host: { '[attr.data-section-id]': "section()?.id ?? 'primary'", '(document:click)': 'outside($event)', '(document:keydown.escape)': 'escape()' },
})
export class ChordletEditorComponent {
  readonly projects = inject(ProjectService);
  private readonly dialogs = inject(Dialog);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  readonly project = this.projects.project;
  readonly section = input<ArrangementSection>();
  readonly allSections = input(false);
  readonly disabled = computed(() => !!this.section()?.useMelodyHarmony);
  private readonly selectionScope = computed(() => JSON.stringify([
    this.projects.projectActivation(), this.section()?.id, this.allSections(), this.disabled(),
  ]));
  readonly selectedIndex = linkedSignal({ source: this.selectionScope, computation: (): number | null => null });
  readonly mixed = computed(() => this.allSections() && this.project().arrangement.some(section => {
    const harmony = section.useMelodyHarmony ? sectionGenerationHarmony(this.project(), section, 'chords')
      : baseGenerationHarmony(this.project(), section);
    const context = sectionProject(this.project(), section);
    return JSON.stringify(progressionChords(BASE_KEY, context.scale, harmony.degrees, harmony.customChords)) !== JSON.stringify(this.chords());
  }));
  private applyAll(chords: (ProgressionChord | null)[]): void {
    this.projects.editAllSectionMusic(section => ({ ...section, customChords: chords,
      chordDegrees: chords.map(() => 1), customDurations: section.customDurations
        ? chords.map((_, index) => section.customDurations?.[index] ?? 4) : undefined }));
  }
  readonly chords = computed(() => {
    const section = this.section();
    const project = sectionProject(this.project(), section), harmony = section?.useMelodyHarmony
      ? sectionGenerationHarmony(this.project(), section, 'chords') : baseGenerationHarmony(this.project(), section);
    return progressionChords(BASE_KEY, project.scale, harmony.degrees, harmony.customChords);
  });
  readonly roots = KEYS;
  readonly spices = CHORD_SPICES;
  readonly menu = signal<{ index: number; kind: 'root' | 'spice'; x: number; y: number; project: unknown; sectionId?: string } | null>(null);
  readonly colorContext = computed(() => {
    const project = sectionProject(this.project(), this.section());
    return { key: effectiveKey(BASE_KEY, this.displayTranspose()), scale: project.scale };
  });
  readonly spiceChoices = computed(() => {
    const menu = this.menu(), chord = menu && this.chords()[menu.index];
    if (!chord) return [];
    const context = this.colorContext();
    return this.spices.map(spice => {
      const candidate = { ...chord, root: this.displayedPitch(chord.root), spice, intervals: undefined };
      const overlap = chordKeyOverlap(candidate, context.key, context.scale);
      return { spice, hue: chordRootHue(candidate.root, context.key), overlap,
        highlight: `${Math.round(6 + 56 * overlap.ratio ** 3)}%`,
        border: `${Math.round(20 + 60 * overlap.ratio ** 2)}%`,
        tooltip: `${KEYS[candidate.root]}${spice}: ${overlap.matching} of ${overlap.total} notes in ${context.key} ${SCALE_DEFINITIONS[context.scale].label}` };
    });
  });
  readonly spiceGroups = computed(() => CHORD_SPICE_GROUPS.map(group => ({
    label: group.label,
    choices: group.spices.map(spice => this.spiceChoices().find(choice => choice.spice === spice)!),
  })));
  readonly intervalChoices = computed(() => {
    const menu = this.menu(), chord = menu && this.chords()[menu.index];
    if (!chord) return [];
    const context = this.colorContext();
    return SCALE_INTERVALS.map(degree => {
      const candidate = scaleIntervalChord({ ...chord, root: this.displayedPitch(chord.root) }, degree, context.key, context.scale);
      const overlap = chordKeyOverlap(candidate, context.key, context.scale);
      return { degree, intervals: candidate.intervals!, overlap, hue: chordRootHue(candidate.root, context.key),
        highlight: `${Math.round(6 + 56 * overlap.ratio ** 3)}%`, border: `${Math.round(20 + 60 * overlap.ratio ** 2)}%`,
        label: degree === 2 ? '2nd' : degree === 3 ? '3rd' : `${degree}th`,
        tooltip: `Root + scale-relative ${degree}: ${candidate.intervals![1]} semitones (${context.key} ${SCALE_DEFINITIONS[context.scale].label}). Saved as two fixed pitches; chromatic roots borrow the nearest scale degree.` };
    });
  });
  intervalSelected(intervals: readonly number[]): boolean {
    const chord = this.chords()[this.menu()?.index ?? 0];
    return !!chord.intervals && chord.intervals.length === 2 && chord.intervals.every((pitch, i) => pitch === intervals[i]);
  }
  chooseInterval(degree: typeof SCALE_INTERVALS[number]): void {
    const menu = this.menu();
    if (!menu || menu.project !== this.project() || menu.sectionId !== this.section()?.id) { this.closeMenu(); return; }
    const chord = this.chords()[menu.index], context = this.colorContext();
    const resolved = scaleIntervalChord({ ...chord, root: this.displayedPitch(chord.root) }, degree, context.key, context.scale);
    this.setChord(menu.index, { ...resolved, root: chord.root });
    this.closeMenu(); this.focusChord(menu.index);
  }
  readonly rootsWithoutScaleSpice = computed(() => {
    const context = this.colorContext();
    return this.roots.map((_, root) => !this.spices.some(spice => {
      const overlap = chordKeyOverlap({ root, spice, inversion: 0 }, context.key, context.scale);
      return overlap.matching === overlap.total;
    }));
  });
  readonly dragging = signal<number | null>(null);
  readonly dropIndex = signal<number | null>(null);
  private drag: { pointerId: number; x: number; y: number; index: number; project: unknown; scope: string } | null = null;
  private suppressPointerClick = false;
  readonly help = 'Click to select · Click again to edit notes · Drag to move · Right-click: root · Middle-click: spice'
    + ' · Scroll: inversion · Shift+scroll: root · Ctrl+scroll: spice · Shift+click: base chord.'
    + ' Keyboard: Space selects or edits, Enter root, Shift+Enter spice, arrows adjust, Alt+arrows move, Delete remove.';

  private displayTranspose(): number { return (this.project().transposeSemitones ?? 0) + (this.section()?.context?.keyChange ?? 0); }
  private displayedPitch(root: number): number { return ((root + this.displayTranspose()) % 12 + 12) % 12; }
  displayedRoot(root: number): string { return KEYS[this.displayedPitch(root)]; }
  rootHue(pitch: number): number { return chordRootHue(pitch, this.colorContext().key); }
  rootFunction(pitch: number): string {
    const chord = this.chords()[this.menu()?.index ?? 0];
    return chordRomanNumeral({ ...chord, root: pitch }, this.colorContext().key);
  }
  chordHue(chord: ProgressionChord): number { return this.rootHue(this.displayedPitch(chord.root)); }
  chordFunction(chord: ProgressionChord): string {
    return chordRomanNumeral({ ...chord, root: this.displayedPitch(chord.root) }, this.colorContext().key);
  }
  symbol(chord: ProgressionChord): string { return this.displayedRoot(chord.root)
    + (chord.intervals ? `[${chord.intervals.join(',')}]` : chord.spice) + inversionSuffix(chord); }
  activate(index: number, event: MouseEvent): void {
    if (this.disabled() || (event.detail > 0 && this.suppressPointerClick)) return;
    const chord = this.chords()[index];
    if (!chord) return;
    this.closeMenu();
    if (event.shiftKey) this.reset(index);
    else if (this.selectedIndex() === index) this.openNotes(index, chord);
    else this.selectedIndex.set(index);
  }
  private openNotes(index: number, chord: ProgressionChord): void {
    if (this.dialogs.openDialogs.length) return;
    const project = this.project(), scope = this.selectionScope();
    const intervals = chordIntervals(chord);
    const title = `Chord ${index + 1} notes`;
    const data: NumericSequenceDialogData = {
      editor: {
        title, description: `Enter semitones above ${this.displayedRoot(chord.root)}. Include 0 for the root.`,
        rows: [{ id: String(index), name: this.symbol(chord), values: intervals }],
        minimum: 0, maximum: MAX_CHORD_INTERVAL, minimumSteps: 1, maximumSteps: MAX_CHORD_TONES,
        reorderable: true, saveLabel: 'Save chord',
        saveHint: this.allSections() ? 'Saving updates this chord slot in sections that have it.' : undefined,
        validate: values => values.includes(0) ? undefined : 'Include 0 for the root.',
      },
      stale: () => this.project() !== project || this.selectionScope() !== scope,
      save: values => {
        if (values.length === intervals.length && values.every((value, i) => value === intervals[i]) && !this.mixed()) return undefined;
        this.projects.dismissGenerationError();
        const result = this.setChord(index, { ...chord, spice: '', intervals: [...values] });
        return this.projects.generationError() ?? (result === 'invalid' ? 'This chord could not be saved.' : undefined);
      },
    };
    this.dialogs.open(NumericSequenceDialogComponent, { ariaLabel: title, autoFocus: '.steps input', data });
  }
  add(): void { if (this.disabled()) return; this.closeMenu(); if (this.allSections()) { if (this.chords().length < 32) this.applyAll([...this.chords(), null]); return; } const section = this.section(); if (section) this.projects.editSectionChordList(section.id, 'add'); else this.projects.addChord(); }
  remove(index: number): void {
    if (this.disabled()) return;
    this.closeMenu();
    this.selectedIndex.set(null);
    if (this.allSections()) {
      if (this.chords().length > 1) this.applyAll(this.chords().filter((_, i) => i !== index));
      return;
    }
    const section = this.section();
    if (section) this.projects.editSectionChordList(section.id, 'remove', index);
    else this.projects.removeChord(index);
  }
  generate(event?: MouseEvent): void {
    if (this.disabled()) return;
    this.closeMenu();
    this.selectedIndex.set(null);
    this.projects.generateProgression(this.section()?.id, this.allSections(), event?.shiftKey === true);
  }
  private setChord(index: number, chord: ProgressionChord) {
    if (this.disabled()) return 'invalid';
    if (this.allSections()) {
      return this.projects.editAllSectionMusic(section => {
        const context = sectionProject(this.project(), section), harmony = baseGenerationHarmony(this.project(), section);
        const chords = progressionChords(BASE_KEY, context.scale, harmony.degrees, harmony.customChords);
        if (index >= chords.length) return section;
        chords[index] = chord;
        return { ...section, customChords: chords, chordDegrees: harmony.degrees };
      });
    }
    const section = this.section();
    return section ? this.projects.setSectionChord(section.id, index, chord) : this.projects.setProgressionChord(index, chord);
  }
  private moveChord(from: number, to: number): void {
    if (from === to) return;
    if (this.allSections()) {
      const chords = [...this.chords()];
      chords.splice(to, 0, chords.splice(from, 1)[0]);
      this.applyAll(chords);
    } else {
      const section = this.section();
      if (section) this.projects.editSectionChordList(section.id, 'move', from, to);
      else this.projects.moveProgressionChord(from, to);
    }
    this.selectedIndex.set(to);
  }

  openMenu(index: number, kind: 'root' | 'spice', event: Event): void {
    event.preventDefault(); event.stopPropagation();
    if (this.disabled()) return;
    const box = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.menu.set({ index, kind, x: Math.max(8, Math.min(innerWidth - 228, box.left)), y: Math.max(8, Math.min(innerHeight - 80, innerHeight - box.top + 6)), project: this.project(), sectionId: this.section()?.id });
    setTimeout(() => this.element.nativeElement.querySelector<HTMLElement>('.chordlet-menu button[aria-checked="true"]')?.focus(), 0);
  }
  choose(value: number | string): void {
    const menu = this.menu();
    if (!menu || menu.project !== this.project() || menu.sectionId !== this.section()?.id) { this.closeMenu(); return; }
    const chord = this.chords()[menu.index];
    const next = menu.kind === 'root' ? { ...chord, root: (((value as number) - (this.project().transposeSemitones ?? 0) - (this.section()?.context?.keyChange ?? 0)) % 12 + 12) % 12 }
      : { ...chord, spice: value as ProgressionChord['spice'], intervals: undefined };
    this.setChord(menu.index, next); this.closeMenu(); this.focusChord(menu.index);
  }
  menuSelected(value: number | string): boolean {
    const menu = this.menu(); if (!menu) return false;
    const chord = this.chords()[menu.index];
    return menu.kind === 'root' ? this.displayedRoot(chord.root) === KEYS[value as number] : !chord.intervals && chord.spice === value;
  }
  closeMenu(): void { this.menu.set(null); }
  escape(): void { const index = this.menu()?.index; this.closeMenu(); this.cancel(); if (index !== undefined) this.focusChord(index); }
  menuKey(event: KeyboardEvent): void {
    const buttons = [...(event.currentTarget as HTMLElement).querySelectorAll<HTMLButtonElement>('button')];
    const index = buttons.indexOf(document.activeElement as HTMLButtonElement);
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1
      : event.key === 'ArrowDown' ? 4 : event.key === 'ArrowUp' ? -4 : 0;
    if (!step && !['Home','End'].includes(event.key)) return;
    event.preventDefault(); event.stopPropagation();
    buttons[event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1 : (index + step + buttons.length) % buttons.length]?.focus();
  }
  outside(event: Event): void { if (!this.element.nativeElement.contains(event.target as Node)) this.closeMenu(); }
  reset(index: number): void {
    const chord = this.chords()[index];
    this.setChord(index, { root: chord.root, inversion: 0,
      spice: [2,4,9].includes(chord.root) ? 'm' : chord.root === 11 ? 'dim' : '' });
  }
  private adjust(index: number, movement: number, root: boolean, spice: boolean): void {
    const chord = this.chords()[index];
    const next = spice ? { ...chord, intervals: undefined, spice: CHORD_SPICES[(CHORD_SPICES.indexOf(chord.spice) + movement + CHORD_SPICES.length) % CHORD_SPICES.length] }
      : root ? { ...chord, root: (chord.root + movement + 12) % 12 }
        : stepChordInversion(chord, movement);
    this.closeMenu(); this.setChord(index, next);
  }
  wheel(index: number, event: WheelEvent): void {
    if (this.disabled() || !event.deltaY || this.drag) return;
    event.preventDefault(); event.stopPropagation();
    this.adjust(index, event.deltaY < 0 ? 1 : -1, event.shiftKey, event.ctrlKey || event.metaKey);
  }
  key(index: number, event: KeyboardEvent): void {
    if (this.disabled()) return;
    if (event.key === 'Enter') { this.openMenu(index, event.shiftKey ? 'spice' : 'root', event); return; }
    if (event.key === 'Delete' || event.key === 'Backspace') { event.preventDefault(); this.remove(index); this.focusChord(Math.min(index, this.chords().length - 1)); return; }
    if (!['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(event.key)) return;
    event.preventDefault(); event.stopPropagation();
    const direction = ['ArrowUp','ArrowRight'].includes(event.key) ? 1 : -1;
    if (event.altKey) {
      const to = Math.max(0, Math.min(this.chords().length - 1, index + direction));
      this.moveChord(index, to); this.focusChord(to);
    } else this.adjust(index, direction, event.shiftKey, event.ctrlKey || event.metaKey);
  }
  start(index: number, event: PointerEvent): void {
    if (this.disabled()) return;
    if (event.button === 1) { this.openMenu(index, 'spice', event); return; }
    if (event.button !== 0 || this.drag) return;
    this.suppressPointerClick = false;
    event.preventDefault(); (event.currentTarget as HTMLElement).focus(); this.closeMenu();
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    this.drag = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, index, project: this.project(), scope: this.selectionScope() };
  }
  move(event: PointerEvent): void {
    if (event.pointerId !== this.drag?.pointerId) return;
    if (this.drag.project !== this.project() || this.drag.scope !== this.selectionScope()) { this.cancel(); return; }
    if (this.dragging() === null && Math.hypot(event.clientX - this.drag.x, event.clientY - this.drag.y) < 4) return;
    this.dragging.set(this.drag.index);
    const target = document.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-chord-index]');
    if (target?.closest('vc-chordlet-editor') === (event.currentTarget as HTMLElement).closest('vc-chordlet-editor')) this.dropIndex.set(Number(target!.dataset['chordIndex']));
  }
  finish(event: PointerEvent): void {
    if (event.pointerId !== this.drag?.pointerId) return;
    const drag = this.drag, to = this.dropIndex();
    const moved = this.dragging() !== null;
    const stale = drag.project !== this.project() || drag.scope !== this.selectionScope();
    this.cancel();
    // Pointer capture can still dispatch a click on the source button after a drag.
    this.suppressPointerClick = moved || stale;
    if (!stale && moved && to !== null) {
      this.moveChord(drag.index, to);
      this.focusChord(to);
    }
  }
  cancel(): void {
    if (this.drag) this.suppressPointerClick = true;
    this.drag = null; this.dragging.set(null); this.dropIndex.set(null);
  }
  private focusChord(index: number): void { setTimeout(() => this.element.nativeElement.querySelector<HTMLElement>(`[data-chord-index="${index}"] .chordlet-label`)?.focus(), 0); }
}
