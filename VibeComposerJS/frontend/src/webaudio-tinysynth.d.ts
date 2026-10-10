declare module 'webaudio-tinysynth' {
  export default class WebAudioTinySynth {
    constructor(options?: { quality?: number; useReverb?: number; voices?: number });
    getAudioContext(): AudioContext;
    setAudioContext(context: AudioContext, destination?: AudioNode): void;
  }
}
