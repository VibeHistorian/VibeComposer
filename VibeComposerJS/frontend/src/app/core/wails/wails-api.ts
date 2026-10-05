export interface AppInfo {
  name: string;
  version: string;
}

interface WailsApp {
  GetAppInfo(): Promise<AppInfo>;
}

declare global {
  interface Window {
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
