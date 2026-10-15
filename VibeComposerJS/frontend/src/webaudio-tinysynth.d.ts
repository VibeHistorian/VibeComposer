declare module 'webaudio-tinysynth' {
  export default class WebAudioTinySynth {
    constructor(options?: { quality?: number; useReverb?: number; voices?: number; internalcontext?: number });
    getAudioContext(): AudioContext;
    setAudioContext(context: BaseAudioContext, destination?: AudioNode): void;
  }
}
