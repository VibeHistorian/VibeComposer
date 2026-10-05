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

## UI organization

Build VibeComposerJS as a focused music workspace instead of keeping every generator, arrangement, and playback control visible at once. The main navigation should lead to four workspaces: **Create**, **Arrange**, **Edit**, and **Mix**. Keep project actions and a compact key/tempo summary in the header, and keep playback and loop controls in a persistent bottom dock.

Each workspace should have one clear primary action and a central canvas suited to its task. Put detailed controls in an inspector that responds to the selected part, section, note, or track. Show common settings directly and group less-used generation, variation, and project options under labeled expandable sections. This keeps advanced capabilities available without crowding the working canvas.

Use the feature and shared UI READMEs for the proposed workspace contents and Angular ownership boundaries. Preserve useful density in purpose-built editors such as piano rolls and drum grids; simplify surrounding chrome, repeated buttons, and always-visible settings. Apply role colors consistently to tracks and arrangement blocks, with neutral surfaces for the rest of the interface.
