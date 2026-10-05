import { Component, OnInit, signal } from '@angular/core';
import { AppInfo, getAppInfo } from './core/wails/wails-api';

@Component({
  selector: 'vc-root',
  standalone: true,
  template: `
    <main class="app-frame">
      <header class="topbar">
        <a class="brand" href="/" aria-label="VibeComposer home">
          <span class="brand-mark" aria-hidden="true">V</span>
          <span>vibe<span class="brand-light">composer</span></span>
        </a>
        <div class="host-pill" [class.connected]="appInfo()">
          <span class="status-dot" aria-hidden="true"></span>
          {{ appInfo() ? 'DESKTOP CONNECTED' : 'BROWSER PREVIEW' }}
        </div>
      </header>

      <section class="intro" aria-labelledby="page-title">
        <p class="eyebrow"><span class="eyebrow-line"></span> MUSIC GENERATION STUDIO</p>
        <h1 id="page-title">Make something<br><span>unexpected.</span></h1>
        <p class="intro-copy">
          Your next idea starts here. The new VibeComposer studio is taking shape.
        </p>
      </section>

      <section class="workspace" aria-labelledby="workspace-title">
        <div class="workspace-heading">
          <div>
            <p class="eyebrow">YOUR WORKSPACE</p>
            <h2 id="workspace-title">Generator</h2>
          </div>
          <span class="phase-tag">FOUNDATION · 01</span>
        </div>

        <div class="empty-state">
          <div class="waveform" aria-hidden="true">
            @for (bar of waveform; track $index) {
              <span [style.height.%]="bar"></span>
            }
          </div>
          <h3>A blank canvas, full of possibility.</h3>
          <p>
            Melody, harmony, rhythm and arrangement tools will find their home here.
          </p>
          <button type="button" class="start-button" disabled>
            <span aria-hidden="true">＋</span> Start a composition
          </button>
        </div>

        <footer class="workspace-footer">
          <span>ANGULAR UI <b>·</b> WAILS DESKTOP</span>
          <span>{{ appInfo() ? appInfo()!.version : 'READY FOR THE REWRITE' }}</span>
        </footer>
      </section>

      <p class="footer-note">
        The original VibeComposer remains available while this studio grows.
      </p>
    </main>
  `,
})
export class AppComponent implements OnInit {
  readonly waveform = [18, 31, 23, 48, 35, 67, 43, 27, 56, 78, 49, 34, 61, 40, 24, 52, 72, 38, 21, 45, 63, 32, 19, 42, 57, 29, 47, 68, 36, 22, 51, 33, 60, 26, 43, 70, 38, 20, 54, 31, 46, 64, 28, 50, 35, 72, 42, 24, 58, 32, 47, 66, 27, 40, 55, 34, 74, 44, 23, 51, 37, 62, 29, 46];
  readonly appInfo = signal<AppInfo | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      this.appInfo.set(await getAppInfo());
    } catch (error: unknown) {
      console.error('Could not connect to the Wails application bridge.', error);
    }
  }
}
