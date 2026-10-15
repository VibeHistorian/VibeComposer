TinySynth 1.1.3 defines `internalcontext` but its constructor does not read that
option. The patch honors it so the adapter can bind an OfflineAudioContext without
opening a realtime audio device. It also exposes the existing sequencer timer as
`timerId` so disposing an offline engine clears it. The MIDI send path only
auto-resumes realtime contexts: offline contexts start via `startRendering()`.
This prevents rejected resume promises from the two initialization MIDI messages.
Synthesis is unchanged.
