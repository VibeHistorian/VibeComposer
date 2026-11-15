# Chiptune harmonic benchmark

Measured 2026-11-15 on this Windows host using headless Chromium 156 and the protected browser broker. Run `pnpm run benchmark:chiptune` with `VIBE_BROWSER_BROKER=1` to repeat. It reads source, not `dist/browser`. Timing is diagnostic, not a test threshold.

The initial 64-harmonic baseline was measured before regeneration. The final comparison ran 64 and 400 harmonics in the same browser, alternating order each round, with no concurrent builds, calibration or tests. The 64-harmonic copy truncates the final bank's arrays; all 19 truncated waves were checked against the original data and match exactly. Both banks use identical patches, envelopes, velocity and routing, with normalization off to isolate the wave-table change.

| Measurement | 64 harmonics | 400 harmonics |
| --- | ---: | ---: |
| Engine construction, warmed median | 60.8 ms | 61.8 ms |
| Engine construction, warmed p95 | 86.0 ms | 93.7 ms |
| Periodic-wave creation within construction, median | 10.1 ms | 12.0 ms |
| Schedule 1,024 notes offline, median | 31.5 ms | 31.1 ms |
| Render six seconds of dense audio, median | 1,816.0 ms | 1,842.2 ms |
| Scheduling work per three-second realtime run, median | 19.8 ms | 20.3 ms |
| Maximum realtime scheduler tick work | 2.1 ms | 2.1 ms |
| Realtime timer gap, p95 (25 ms requested) | 27.5 ms | 27.6 ms |
| Late realtime onset batches, total | 0 | 0 |

Engine construction excludes module fetch/parse and AudioContext construction, and includes TinySynth initialization and creation of all 19 chip waves. Two warm-up rounds precede 16 timed constructions per bank. First constructions are retained separately in the raw report, but are not a reliable cold-start comparison because browser/JIT state differs by order.

Offline runs use 48 kHz stereo, 16 tracks, four-note chords, 16 onsets spaced 250 ms apart and 750 ms holds: 1,024 notes, 256 notes/second, 192 held voices plus release tails. Each bank has one warm-up and five measured renders of the same six-second buffer. Render times vary substantially with JIT/host load; the approximately 1.4% median difference is small relative to that variation. Both variants render faster than realtime in this workload.

Realtime runs use the same chord density, a 25 ms scheduler timer and 125 ms lookahead. Each bank has three three-second runs (768 scheduled notes per run). An analyser confirms nonzero PCM, the audio clock must advance, and the script records late onset batches, tick work and timer gaps. Chromium uses a silent audio sink. These measurements exercise live rendering and scheduling but do **not** measure hardware underruns, prove absence of audible glitches on a physical device, or measure OS-wide audio-thread CPU usage.

The 400-harmonic bank adds about 1 ms to median engine construction in this run, with little measured change in dense scheduling or render throughput. Its two Float32 coefficient arrays per waveform total 60,952 bytes, compared with 9,880 bytes at 64 harmonics (49.9 KiB more). This excludes parsed JavaScript arrays and browser-internal wavetables. Serialized asset growth is separate from the construction timings.

Raw reports are `.angular/chiptune-benchmark-64.json` (initial baseline) and `.angular/chiptune-benchmark-400.json` (paired comparison). The benchmark is intentionally outside the normal test suite and adds no startup instrumentation to the application.
