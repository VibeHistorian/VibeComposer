/** Hosting configuration and startup policy, independent of Angular and browser storage. */
export interface HostedConfiguration {
  readonly demoProject?: string | null;
  readonly recommendedSettings?: string | null;
}

export interface HostedPreferences {
  hasStoredPreferences(): boolean;
  importJson(json: string): boolean;
  importThemeJson(json: string): boolean;
}

export interface HostedProjects {
  readonly hasSavedWorkspace: boolean;
  importProjectJson(json: string, destination: 'new-tab' | 'replace'): boolean;
}

export interface HostedEnvironment {
  readonly baseUrl: string;
  readonly pageUrl: string;
  readonly fetchText: (url: string) => Promise<string>;
  readonly report: (message: string) => void;
}

export class HostedContent {
  private configuration: HostedConfiguration = {};
  private demo: Promise<string | null> | undefined;
  get hasDemo(): boolean { return !!this.configuration.demoProject; }

  constructor(private readonly environment: HostedEnvironment) {}

  private resourceUrl(path: string): string {
    if (!path.trim()) throw new Error('Missing resource URL');
    const base = new URL(this.environment.baseUrl);
    const url = new URL(path, base);
    // Also support relative assets on Wails' custom origin, without allowing
    // arbitrary data/file/script URLs from shared links.
    if (!['http:', 'https:'].includes(url.protocol)
        && !(url.protocol === base.protocol && url.host === base.host && base.protocol === 'wails:')) {
      throw new Error('Unsupported resource URL');
    }
    return url.href;
  }

  private async resource(path: string, label: string): Promise<string | null> {
    try { return await this.environment.fetchText(this.resourceUrl(path)); }
    catch { this.environment.report(`Could not load ${label}.`); return null; }
  }

  async demoProject(): Promise<string | null> {
    const path = this.configuration.demoProject;
    if (!path) return null;
    this.demo ??= this.resource(path, 'the demo project');
    const result = await this.demo;
    if (result === null) this.demo = undefined; // A failed request can be retried from the menu.
    return result;
  }

  async initialize(preferences: HostedPreferences, getProjects: () => HostedProjects): Promise<string[]> {
    const config = await this.resource('startup.json', 'startup configuration');
    if (config !== null) {
      try {
        const value: unknown = JSON.parse(config);
        if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error();
        const candidate = value as Record<string, unknown>;
        for (const key of ['demoProject', 'recommendedSettings']) {
          if (candidate[key] != null && (typeof candidate[key] !== 'string' || !(candidate[key] as string).trim())) throw new Error();
        }
        this.configuration = candidate as HostedConfiguration;
      } catch { this.environment.report('Startup configuration is invalid.'); }
    }

    const parameters = new URL(this.environment.pageUrl).searchParams;
    const linked = (key: string) => parameters.has(key)
      ? this.resource(parameters.get(key) || '', `linked ${key}`) : Promise.resolve(null);
    const recommended = !preferences.hasStoredPreferences() && this.configuration.recommendedSettings
      ? this.resource(this.configuration.recommendedSettings, 'recommended settings') : Promise.resolve(null);
    const [defaults, settings, theme, project] = await Promise.all([
      recommended, linked('settings'), linked('theme'), linked('project'),
    ]);
    const consumed: string[] = [];
    const apply = (json: string | null, label: string, importer: (json: string) => boolean, parameter?: string) => {
      if (json === null) return;
      if (importer(json)) { if (parameter) consumed.push(parameter); }
      else this.environment.report(`Could not open ${label}: invalid file.`);
    };
    // Explicit settings override recommendations; theme overrides only appearance.
    apply(defaults, 'recommended settings', json => preferences.importJson(json));
    apply(settings, 'linked settings', json => preferences.importJson(json), 'settings');
    apply(theme, 'linked theme', json => preferences.importThemeJson(json), 'theme');

    // Create project state after preferences are applied. Existing session/local
    // workspaces win over the hosted demo; an explicit link always adds a tab.
    const projects = getProjects();
    if (!projects.hasSavedWorkspace) {
      apply(await this.demoProject(), 'the demo project', json => projects.importProjectJson(json, 'replace'));
    }
    apply(project, 'linked project', json => projects.importProjectJson(json, 'new-tab'), 'project');
    return consumed;
  }
}

export async function fetchHostedText(url: string): Promise<string> {
  const response = await fetch(url, { signal: AbortSignal.timeout(6000), cache: 'no-cache' });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.text();
}
