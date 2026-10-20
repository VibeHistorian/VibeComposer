# Project state ownership

`ProjectService` owns Angular signals, session storage, generation error reporting, and the history/persistence commit boundary. UI consumers continue to use its public commands. Candidate-building helpers do not publish state or create history entries.

Independent tracks own generator settings and mix values. Global role edits update their member tracks; there is no duplicated project-level generator settings or role mix layer. Adding a track to an empty role uses the corresponding `DEFAULT_TRACKS` entry. Imports read track values and omit obsolete top-level role settings and mix when constructing project state.

| Module | Responsibility |
| --- | --- |
| `project-codec.ts` | Decode imported/session data; validate complete command candidates. |
| `project-json.ts` | Format project exports with inline numeric arrays and one manual note per line. |
| `project-part-decoder.ts` | Decode and validate each role's full generator settings. |
| `project-track-decoder.ts` | Decode tracks, mix values, and manual phrases; validate phrase bounds. |
| `project-arrangement-decoder.ts` | Decode sections, scoped overrides, track references, and presence; define arrangement limits. |
| `project-load-diagnostics.ts` | Return field diagnostics after a rejected load; diagnostics do not define acceptance rules. |
| `project-defaults.ts` | Construct a fresh default project. |
| `section-type-chances.ts` | Framework-independent section chance defaults and their type. |
| `project-arrangement-commands.ts` | Build immutable candidates for section structure, degree overrides, and presence edits. |
| `project-history.ts` | Compare history content and preserve audition state during restoration. |

Codecs, defaults, and arrangement commands receive section chances and any presence random source as explicit inputs. They do not access Angular, storage, the DOM, or Wails. `ProjectService` supplies current preferences and `Math.random` for presence work; seeded music generation retains its existing Java random streams and draw order.

Keep imported-data decoding distinct from complete-candidate validation: imports retain the existing optional-field defaults and recovery behavior, while commands must supply a complete valid project. Treat `schemaVersion` as informational; do not add migrations or version gates during buildout.

The service validates candidates before committing. It owns no-op detection, history compaction, redo invalidation, persistence, and atomic rejection of invalid batches. A command helper may return `undefined` for a rejected request; a returned candidate still passes through the service's validation boundary.

Run `npm run test:parts` for project workflow coverage and the framework-independent codec/command checks in `scripts/project-codec.test.cjs`. Import, arrangement, and generation changes also use the relevant suites listed in `package.json`; production browser suites require a fresh `npm run build`. See [the test guide](../../../../scripts/README.md).
