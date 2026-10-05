# VibeComposerJS

The starting point for the Angular rewrite of VibeComposer. The desktop window is provided by Wails v2; the UI lives in `frontend/` and Go methods exposed to Angular live in `App`.

This project is separate from the existing Java application. The rewrite can move feature by feature while the Java app remains available as a reference and working product.

## Requirements

- Go 1.23 or later
- Node.js 20.19 or later and npm
- Wails v2.10.2 and the platform build tools listed in the [Wails installation guide](https://wails.io/docs/gettingstarted/installation)

Install the Wails CLI once:

```powershell
go install github.com/wailsapp/wails/v2/cmd/wails@v2.10.2
```

Then start the desktop app from this directory:

```powershell
wails dev
```

Wails runs the Angular dev server and refreshes the desktop window as the UI changes. Its frontend install and build commands are configured in `wails.json`.

To work on the UI in a browser without starting Wails:

```powershell
cd frontend
npm install
npm start
```

The Angular shell reports when it is running outside Wails. To make a desktop build, run `wails build`; the generated application is written under `build/bin/`.

## Project layout

```text
VibeComposerJS/
  App.go                 Go methods bound to the web UI
  main.go                Wails desktop entry point
  frontend/
    src/app/core/        Wails bridge and app-wide services
    src/app/features/    Angular feature areas for the rewrite
    src/app/shared/      Reusable UI pieces
    src/styles.css       Global visual system
  wails.json             Wails and Angular development/build commands
```

The starter bridge is intentionally small. Add generator operations to Go only when they need native or long-running work; keep presentation and interaction in Angular. Wails generates its JavaScript bindings during `wails dev` / `wails build`.
