export interface AppInfo {
  name: string;
  version: string;
}

interface WailsApp {
  GetAppInfo(): Promise<AppInfo>;
}

declare global {
  interface Window {
    runtime?: { BrowserOpenURL(url: string): void };
    go?: {
      main?: {
        App?: WailsApp;
      };
    };
  }
}

export async function getAppInfo(): Promise<AppInfo | null> {
  const app = window.go?.main?.App;
  if (!app) {
    return null;
  }

  return app.GetAppInfo();
}

export function openInSystemBrowser(url: string): boolean {
  const runtime = window.runtime;
  if (!runtime?.BrowserOpenURL) {
    return false;
  }
  runtime.BrowserOpenURL(url);
  return true;
}
