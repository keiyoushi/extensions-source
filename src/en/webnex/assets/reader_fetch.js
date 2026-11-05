(path, bridge) =>
    fetch(`${path}/pages`, { cache: 'no-store' })
        .then(async (r) => ({ status: r.status, body: await r.text() }))
        .catch(() => ({ status: 0, body: '' }))
        .then((r) => window[bridge].postMessage(JSON.stringify(r)))
