// The reader page sets a path-scoped cookie on its final url after redirects, the page list needs it
(path, bridge) => fetch(path, { cache: 'no-store' })
    .then((r) => fetch(`${new URL(r.url).pathname}/pages`, { cache: 'no-store' }))
    .then(async (r) => ({ status: r.status, body: await r.text() }))
    .catch(() => ({ status: 0, body: '' }))
    .then((r) => window[bridge].postMessage(JSON.stringify(r)))
