import { Injectable, signal } from '@angular/core';
@Injectable({ providedIn: 'root' })
export class LiveRecordingService {
  readonly countInFlash = signal(false);
  readonly phraseBeat = signal<number | null>(null);
  stop?: () => void;
}
