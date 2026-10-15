/// <reference lib="webworker" />
import opus from '@audio/encode-opus';

addEventListener('message', async ({ data }: MessageEvent<{ channels: Float32Array[]; sampleRate: number }>) => {
  try {
    const encoder = await opus({ sampleRate: data.sampleRate, channels: 2, bitrate: 192 });
    try {
      const chunks: Uint8Array<ArrayBuffer>[] = [];
      for (let start = 0; start < data.channels[0].length; start += 48000) {
        chunks.push(new Uint8Array(encoder.encode(data.channels.map(channel => channel.subarray(start, start + 48000)))));
      }
      chunks.push(new Uint8Array(encoder.flush()));
      postMessage({ blob: new Blob(chunks, { type: 'audio/ogg; codecs=opus' }) });
    } finally {
      encoder.free();
    }
  } catch (error) {
    postMessage({ error: error instanceof Error ? error.message : 'Audio encoding failed.' });
  }
});
