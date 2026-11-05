// Drive Web Audio lookahead without depending on a background window's timers.
// Keep only one outstanding tick while the main thread is busy.
let timer;
let pending = false;

self.onmessage = event => {
  if (event.data === 'ack') {
    pending = false;
  } else if (event.data?.type === 'start') {
    clearInterval(timer);
    pending = false;
    timer = setInterval(() => {
      if (pending) return;
      pending = true;
      self.postMessage('tick');
    }, event.data.intervalMs);
  }
};
