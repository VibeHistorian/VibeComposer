// One final stage per context also protects overlapping previews and mix crossfades.
const outputs = new WeakMap<BaseAudioContext, GainNode>();

/** Soft peak protection: unity below 0.8, smoothly approaches +/-0.98 above it.
 * This is instantaneous saturation, not a lookahead dynamics compressor. It adds
 * harmonics on overloaded peaks, but no attack overshoot, pumping or latency.
 * Keep oversampling off so offline regions retain their exact timing and length.
 */
export function protectedAudioOutput(context: BaseAudioContext): GainNode {
  const existing = outputs.get(context);
  if (existing) return existing;
  const output = context.createWaveShaper();
  const curve = new Float32Array(65537);
  // WaveShaper clamps inputs outside [-1, 1]. Attenuate before it so the knee
  // remains smooth for sums up to 16, with unity gain throughout the quiet range.
  for (let i = 0; i < curve.length; i++) {
    const x = (i * 2 / (curve.length - 1) - 1) * 16;
    const magnitude = Math.abs(x);
    curve[i] = Math.sign(x) * (magnitude <= 0.8 ? magnitude
      : 0.8 + 0.18 * Math.tanh((magnitude - 0.8) / 0.18));
  }
  output.curve = curve;
  const input = context.createGain();
  input.gain.value = 1 / 16;
  input.connect(output);
  output.connect(context.destination);
  // Return the input bus; the context owns both nodes until it is closed/collected.
  outputs.set(context, input);
  return input;
}
