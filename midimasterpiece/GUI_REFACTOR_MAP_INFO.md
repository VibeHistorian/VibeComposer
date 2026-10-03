# Refactor Session Notes

## Useful files

- `GUI_REFACTOR_MAP.md` is the module plan and progress checklist. Update it as modules are completed.
- `src/main/java/org/vibehistorian/vibecomposer/VibeComposerGUI.java` is the legacy UI owner. Search for a feature's fields and methods with `rg -n "Melody|melody" <file>` and inspect focused line ranges; avoid repeatedly dumping the whole class.

## Conventions and boundaries

- Migrate one module at a time. Move its fields and cohesive UI methods, then update references across the source tree with `rg`.
- Keep obsolete members in `VibeComposerGUI` during the migration by prefixing their names with `__`; remove them only after the full migration is ready for cleanup.
- Mutable controls on `MelodyGUI` and `GenerationGUI` are instance-owned by the composition root; consumers use the owner or a focused callback.
- Arrangement state is exposed as static fields on `ArrangementGUI` while callers across the application are being migrated. Keep new arrangement references on `ArrangementGUI`.
- Global and per-instrument solo/mute controls are owned by `MainWindowControls`; use its getters through `SoloMuteController` or `SoloMuter.Context` rather than introducing another shared holder.
- Playhead drag state belongs to `PlayheadRangeSlider`; do not add a mirrored drag flag to `PlaybackState`.
- Pass active `GUIConfig` through the owning window or a focused supplier as consumers are migrated; keep config persistence DTOs separate from live window configuration state.
- Keep workflows that coordinate multiple instrument types in `VibeComposerGUI`; pass only the shared operations a module needs through its context interface.
- Update component and popup callers when a moved field changes owner. Search the whole `src/main/java` tree, excluding the migration backup when checking active references.
- The original extraction and phase 4 workflow moves are complete. Phase 5 now tracks the remaining ownership work. Treat mutable feature statics and concrete feature-GUI lookups as migration scaffolding to reduce gradually; use explicit instance owners and narrow contexts, and do not add new global state or pass the main window as a general-purpose context.

## Effective commands

Run from the project root (`D:\Production\IdeaProjects\VibeComposer\midimasterpiece`):

Prefer simple file operations through IDEA MCP.

```powershell
rg -n "Melody|melody" src/main/java/org/vibehistorian/vibecomposer/VibeComposerGUI.java
Get-Content src/main/java/org/vibehistorian/vibecomposer/VibeComposerGUI.java | Select-Object -Skip 1700 -First 100
```

`rg -n` is effective for locating scattered references; `Get-Content` with `Select-Object` is useful for reviewing a bounded source section.

# Compilation - a generation test was added `GeneratorRegressionTest` to verify a test XML file still regenerates into the same MIDI
mvn compile
