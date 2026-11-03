// The reader's own calls stall the same way the API does, until Cloudflare gives up after 30 seconds:
// abort a call that hasn't finished in 8 seconds and make it again, up to 4 times.
(() => {
  const fetch = window.fetch;
  const reader = /\/chapter-(deferred-media|map-proxy-plan)\//;
  window.fetch = async function (input, init) {
    const url = input instanceof Request ? input.url : String(input);
    if (!reader.test(url)) return fetch.call(this, input, init);
    for (let attempt = 1; ; attempt++) {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 8000);
      try {
        const request = input instanceof Request ? input.clone() : input;
        const response = await fetch.call(this, request, { ...init, signal: controller.signal });
        await response.clone().arrayBuffer();
        if (response.status < 500 || attempt === 4) return response;
      } catch (e) {
        if (attempt === 4 || init?.signal?.aborted) throw e;
      } finally {
        clearTimeout(timer);
      }
    }
  };
})()
