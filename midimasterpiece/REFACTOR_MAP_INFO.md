# Refactor Session Notes

## Useful files

- `REFACTOR_MAP.md` is the module plan and progress checklist. Update it as modules are completed.
- `src/main/java/org/vibehistorian/vibecomposer/VibeComposerGUI.java` is the legacy UI owner. Search for a feature's fields and methods with `rg -n "Melody|melody" <file>` and inspect focused line ranges; avoid repeatedly dumping the whole class.
- `VibeComposerGUI_MigrationBackup.java` is a read-only reference. Do not edit it.
- `MelodyGUI.java` is the first extracted UI module. It owns melody controls and melody-specific UI setup, initial panel creation, panel randomization, and seed randomization. Its `Context` interface receives shared window operations so this class does not depend on `VibeComposerGUI`.
- `ChordGUI.java` owns chord UI state, chord settings construction, the chord tab, progression controls, custom chord editing, and randomized chord panel creation. Cross-instrument workflows stay in `VibeComposerGUI` and read chord controls through `ChordGUI`; its `Context` interface supplies shared window operations.
- `ArpGUI.java` owns arp UI state, settings and tab construction, and randomized arp panel creation. Its `Context` interface supplies shared window operations.
- `ArrangementGUI.java` owns arrangement UI state, controls and table initialization, action dispatch, and popup/model helpers. Its `Context` supplies shared window operations and delegates cross-instrument or table-editing work that still belongs to `VibeComposerGUI`.
- `SwingUtils.java` holds shared Swing helpers. Put generally useful UI helpers here instead of making a feature module call back into the main window.
- Other prepared module files (`ScoreGUI`, `ExtraSettingsGUI`, `GenerationGUI`, and `VibeComposerCoreGUI`) may still be skeletons; inspect each before relying on it.
- Cross-cutting models and components include `GUIConfig.java`, `Panels/MelodyPanel.java`, and `Components/MelodyMidiDropPane.java`.

## Conventions and boundaries

- Migrate one module at a time. Move its fields and cohesive UI methods, then update references across the source tree with `rg`.
- Keep obsolete members in `VibeComposerGUI` during the migration by prefixing their names with `__`; remove them only after the full migration is ready for cleanup.
- Melody state is currently exposed as static fields on `MelodyGUI` to support existing application-wide callers. Use `MelodyGUI.fieldName` at call sites so ownership stays visible.
- Arrangement state is exposed as static fields on `ArrangementGUI` while callers across the application are being migrated. Keep new arrangement references on `ArrangementGUI`.
- Keep workflows that coordinate multiple instrument types in `VibeComposerGUI`; pass only the shared operations a module needs through its context interface.
- Update component and popup callers when a moved field changes owner. Search the whole `src/main/java` tree, excluding the migration backup when checking active references.

## Effective commands

Run from the project root (`D:\Production\IdeaProjects\VibeComposer\midimasterpiece`):

Prefer simple file operations through IDEA MCP.

```powershell
rg -n "Melody|melody" src/main/java/org/vibehistorian/vibecomposer/VibeComposerGUI.java
Get-Content src/main/java/org/vibehistorian/vibecomposer/VibeComposerGUI.java | Select-Object -Skip 1700 -First 100
mvnd -DskipTests compile
```

`rg -n` is effective for locating scattered references; `Get-Content` with `Select-Object` is useful for reviewing a bounded source section. `mvn -DskipTests compile` successfully checked the MelodyGUI migration without running tests.
