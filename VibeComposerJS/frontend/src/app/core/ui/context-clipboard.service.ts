import { Injectable, signal } from '@angular/core';
import type { NoteHarmony } from '../music/midi-import-options';
import type { PhraseNote } from '../music/phrase-note';

interface ClipboardContents {
  phraseNotes: {
    readonly notes: readonly (PhraseNote & { readonly trackId: string })[];
    readonly harmony: NoteHarmony | null;
  };
}

/** In-memory snapshots owned by the app, independent of individual editors. */
@Injectable({ providedIn: 'root' })
export class ContextClipboardService {
  private readonly contents = signal<Partial<ClipboardContents>>({});

  has(context: keyof ClipboardContents): boolean {
    return this.contents()[context] !== undefined;
  }

  copy<K extends keyof ClipboardContents>(context: K, value: ClipboardContents[K]): void {
    this.contents.update(contents => ({ ...contents, [context]: structuredClone(value) }));
  }

  read<K extends keyof ClipboardContents>(context: K): ClipboardContents[K] | undefined {
    return structuredClone(this.contents()[context]);
  }
}
