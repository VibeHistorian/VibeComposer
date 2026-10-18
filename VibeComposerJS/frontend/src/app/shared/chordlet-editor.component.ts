import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input, signal } from '@angular/core';
import { ProjectService } from '../core/project/project.service';
import { CHORD_SPICES, KEYS, chordKeyOverlap, chordRootHue, effectiveKey, progressionChords, type ProgressionChord } from '../core/music/harmony';
import type { ArrangementSection } from '../core/project/project.model';
import { baseGenerationHarmony } from '../core/music/project-harmony';
import { sectionProject } from '../core/music/section-context';
import { SCALE_DEFINITIONS } from '../core/music/scales';

@Component({
  selector: 'vc-chordlet-editor', changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chordlet-editor.component.html', styleUrl: './chordlet-editor.component.css',
  host: { '[attr.data-section-id]': "section()?.id ?? 'primary'", '(document:click)': 'outside($event)', '(document:keydown.escape)': 'escape()' },
})
export class ChordletEditorComponent {
  readonly projects = inject(ProjectService);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  readonly project = this.projects.project;
  readonly section = input<ArrangementSection>();
  readonly chords = computed(() => {
    const project = sectionProject(this.project(), this.section()), harmony = baseGenerationHarmony(this.project(), this.section());
    return progressionChords(project.key, project.scale, harmony.degrees, harmony.customChords);
  });
  readonly roots = KEYS;
  readonly spices = CHORD_SPICES;
  readonly menu = signal<{ index: number; kind: 'root' | 'spice'; x: number; y: number; project: unknown; sectionId?: string } | null>(null);
  readonly colorContext = computed(() => {
    const project = sectionProject(this.project(), this.section());
    return { key: effectiveKey(project.key, this.displayTranspose()), scale: project.scale };
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
        title: `${KEYS[candidate.root]}${spice}: ${overlap.matching} of ${overlap.total} notes in ${context.key} ${SCALE_DEFINITIONS[context.scale].label}` };
    });
  });
  readonly dragging = signal<number | null>(null);
  readonly dropIndex = signal<number | null>(null);
  private drag: { pointerId: number; x: number; y: number; index: number; project: unknown; sectionId?: string } | null = null;
  readonly help = 'Drag to move · Right-click: root · Middle-click: spice · Scroll: inversion · Shift+scroll: root · Ctrl+scroll: spice · Double-click: base chord. Keyboard: Enter root, Shift+Enter spice, arrows adjust, Alt+arrows move, Delete remove.';

  private displayTranspose(): number { return (this.project().transposeSemitones ?? 0) + (this.section()?.context?.keyChange ?? 0); }
  private displayedPitch(root: number): number { return ((root + this.displayTranspose()) % 12 + 12) % 12; }
  displayedRoot(root: number): string { return KEYS[this.displayedPitch(root)]; }
  rootHue(pitch: number): number { return chordRootHue(pitch, this.colorContext().key); }
  chordHue(chord: ProgressionChord): number { return this.rootHue(this.displayedPitch(chord.root)); }
  symbol(chord: ProgressionChord): string { return this.displayedRoot(chord.root)
    + (chord.intervals ? `[${chord.intervals.join(',')}]` : chord.spice) + (chord.inversion ? '.' + chord.inversion : ''); }
  add(): void { this.closeMenu(); const section = this.section(); if (section) this.projects.editSectionChordList(section.id, 'add'); else this.projects.addChord(); }
  remove(index: number): void { this.closeMenu(); const section = this.section(); if (section) this.projects.editSectionChordList(section.id, 'remove', index); else this.projects.removeChord(index); }
  generate(): void {
    this.closeMenu();
    this.projects.generateProgression(this.section()?.id);
  }
  private setChord(index: number, chord: ProgressionChord): void {
    const section = this.section(); if (section) this.projects.setSectionChord(section.id, index, chord);
    else this.projects.setProgressionChord(index, chord);
  }
  private moveChord(from: number, to: number): void {
    const section = this.section(); if (section) this.projects.editSectionChordList(section.id, 'move', from, to);
    else this.projects.moveProgressionChord(from, to);
  }

  openMenu(index: number, kind: 'root' | 'spice', event: Event): void {
    event.preventDefault(); event.stopPropagation();
    const box = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.menu.set({ index, kind, x: Math.max(8, Math.min(innerWidth - 228, box.left)), y: Math.max(8, Math.min(innerHeight - 220, innerHeight - box.top + 6)), project: this.project(), sectionId: this.section()?.id });
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
    return menu.kind === 'root' ? this.displayedRoot(chord.root) === KEYS[value as number] : chord.spice === value;
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
        : { ...chord, inversion: Math.abs(chord.inversion + movement) > 5 ? 0 : chord.inversion + movement };
    this.closeMenu(); this.setChord(index, next);
  }
  wheel(index: number, event: WheelEvent): void {
    if (!event.deltaY || this.drag) return;
    event.preventDefault(); event.stopPropagation();
    this.adjust(index, event.deltaY < 0 ? 1 : -1, event.shiftKey, event.ctrlKey || event.metaKey);
  }
  key(index: number, event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ' ') { this.openMenu(index, event.shiftKey ? 'spice' : 'root', event); return; }
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
    if (event.button === 1) { this.openMenu(index, 'spice', event); return; }
    if (event.button !== 0) return;
    event.preventDefault(); (event.currentTarget as HTMLElement).focus(); this.closeMenu();
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    this.drag = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, index, project: this.project(), sectionId: this.section()?.id };
  }
  move(event: PointerEvent): void {
    if (event.pointerId !== this.drag?.pointerId) return;
    if (this.drag.project !== this.project() || this.drag.sectionId !== this.section()?.id) { this.cancel(); return; }
    if (Math.hypot(event.clientX - this.drag.x, event.clientY - this.drag.y) < 4) return;
    this.dragging.set(this.drag.index);
    const target = document.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-chord-index]');
    if (target?.closest('vc-chordlet-editor') === (event.currentTarget as HTMLElement).closest('vc-chordlet-editor')) this.dropIndex.set(Number(target!.dataset['chordIndex']));
  }
  finish(event: PointerEvent): void {
    if (event.pointerId !== this.drag?.pointerId) return;
    const drag = this.drag, to = this.dropIndex();
    if (drag.project === this.project() && drag.sectionId === this.section()?.id && to !== null && this.dragging() !== null) this.moveChord(drag.index, to);
    this.cancel();
    if (to !== null) this.focusChord(to);
  }
  cancel(): void { this.drag = null; this.dragging.set(null); this.dropIndex.set(null); }
  private focusChord(index: number): void { setTimeout(() => this.element.nativeElement.querySelector<HTMLElement>(`[data-chord-index="${index}"] .chordlet-label`)?.focus(), 0); }
}
