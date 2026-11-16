import { Injectable, signal } from '@angular/core';
@Injectable({ providedIn: 'root' })
export class LiveRecordingService {
  readonly phraseBeat = signal<number | null>(null);
  stop?: () => void;
}
