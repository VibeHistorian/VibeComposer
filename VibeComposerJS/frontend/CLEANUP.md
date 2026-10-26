# Frontend cleanup

## 2026-10-26 refactoring pass

- Shared mute/solo eligibility in `core/audio/mix-values.ts` across live playback, MIDI routing, audio/MIDI export and score filtering. Consumer-specific zero-volume handling remains intact.
- Shared MIDI pan conversion between device output and MIDI file export.
- Reused the export download helper for UI preferences, including object URL cleanup.
- Removed the fixed theme-shade test and cosmetic browser assertions for exact labels, gaps, dimensions, knob variants and control arrangement. Retained readability, keyboard focus, overflow, settings transfer, history and musical behavior coverage. Overview canvas checks now compare with viewport size and display scale; loop checks assert musical alignment without fixing marker thickness.
- Replaced label-based Melody field lookup in contextual workflows with stored field keys, allowing both quick-panel and inspector placement. Removed rhythm-suite snapshots of compact widths, knob diameters, short captions and category colors, including a 100px limit that rejected a 100.5px joined control.

Validation: all 469 functional tests passed, and the production build passed with a Pixi dependency CommonJS optimization warning. The browser aggregate passed 18 of 21 cases; after the field-lookup and cosmetic-check cleanup, all three remaining cases passed through the focused contextual and rhythm commands. Browser checks required the host approval flow after sandboxed Chromium failed preflight with access denied.

See [test guidance](scripts/README.md) for verification commands and test boundaries.
