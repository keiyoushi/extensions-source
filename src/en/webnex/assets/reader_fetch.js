// The site reader's fetch of the page list, on the reader page's final url that sets its cookie
(path, bridge) => fetch(path, { cache: 'no-store' })
    .then((r) => fetch(`${new URL(r.url).pathname}/pages`, { cache: 'no-store' }))
    .then(async (r) => ({ status: r.status, body: await r.text() }))
    .catch(() => ({ status: 0, body: '' }))
    .then((r) => window[bridge].postMessage(JSON.stringify(r)))
