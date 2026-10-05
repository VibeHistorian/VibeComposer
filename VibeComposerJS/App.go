package main

// App exposes the Go methods that the Angular frontend can call through Wails.
type App struct{}

// AppInfo is the small handshake used by the starter UI to detect the desktop host.
type AppInfo struct {
	Name    string `json:"name"`
	Version string `json:"version"`
}

func NewApp() *App {
	return &App{}
}

func (a *App) GetAppInfo() AppInfo {
	return AppInfo{
		Name:    "VibeComposer",
		Version: "rewrite-in-progress",
	}
}
