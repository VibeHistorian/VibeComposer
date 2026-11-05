// Install before fixture/application scripts. Realtime tests render full-scale
// audio to Chromium's silent sink; offline rendering remains untouched.
function silenceBrowserAudio() {
  const NativeContext = globalThis.AudioContext ?? globalThis.webkitAudioContext;
  if (!NativeContext || !globalThis.AudioNode) return;
  const connect = AudioNode.prototype.connect;
  const disconnect = AudioNode.prototype.disconnect;
  const mutedDestinations = new WeakMap();

  AudioNode.prototype.connect = function (destination, ...args) {
    const safeDestination = mutedDestinations.get(destination) ?? destination;
    const result = connect.call(this, safeDestination, ...args);
    return result === safeDestination ? destination : result;
  };
  AudioNode.prototype.disconnect = function (...args) {
    if (args.length && typeof args[0] !== 'number') args[0] = mutedDestinations.get(args[0]) ?? args[0];
    return disconnect.apply(this, args);
  };

  function SilentAudioContext(options) {
    if (!new.target) throw new TypeError('AudioContext must be constructed with new.');
    let context;
    try {
      context = Reflect.construct(NativeContext, [{ ...options, sinkId: { type: 'none' } }], new.target);
    } catch {
      // Older browsers can reject the sink option instead of ignoring it.
      context = Reflect.construct(NativeContext, [options], new.target);
    }
    if (context.sinkId?.type !== 'none') {
      // Mute only the hardware boundary, keeping analyser data and exported
      // buffers at their original levels. Configure before returning to callers.
      const mute = context.createGain();
      mute.gain.value = 0;
      connect.call(mute, context.destination);
      mutedDestinations.set(context.destination, mute);
    }
    return context;
  }
  Object.setPrototypeOf(SilentAudioContext, NativeContext);
  SilentAudioContext.prototype = NativeContext.prototype;
  globalThis.AudioContext = SilentAudioContext;
  if (globalThis.webkitAudioContext) globalThis.webkitAudioContext = SilentAudioContext;
}

const audioSupport = `<script>(${silenceBrowserAudio.toString()})();</script>`;
module.exports = { audioSupport, silenceBrowserAudio };
