import { DOCUMENT } from '@angular/common';
import { inject, Injectable, Injector, signal } from '@angular/core';
import { ProjectService } from '../project/project.service';
import { UiPreferencesService } from './ui-preferences.service';
import { fetchHostedText, HostedContent } from './hosted-content';

@Injectable({ providedIn: 'root' })
export class HostedContentService {
  private readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  readonly demoAvailable = signal(false);
  readonly message = signal('');
  private readonly content = new HostedContent({
    baseUrl: this.document.baseURI,
    pageUrl: this.document.URL,
    fetchText: fetchHostedText,
    report: message => this.report(message),
  });

  report(message: string): void { this.message.update(previous => previous ? `${previous} ${message}` : message); }
  demoProject(): Promise<string | null> { return this.content.demoProject(); }

  async initialize(): Promise<void> {
    const consumed = await this.content.initialize(this.injector.get(UiPreferencesService), () => this.injector.get(ProjectService));
    this.demoAvailable.set(this.content.hasDemo);
    if (!consumed.length) return;
    this.injector.get(ProjectService).flushPersistence();
    // Consume successful imports so refreshing restores the workspace instead of
    // adding another linked tab. Unrelated parameters and the fragment survive.
    const window = this.document.defaultView;
    if (!window) return;
    const url = new URL(window.location.href);
    for (const key of consumed) url.searchParams.delete(key);
    try { window.history.replaceState(window.history.state, '', url.href); }
    catch { /* Import remains usable if the host disallows URL updates. */ }
  }
}
