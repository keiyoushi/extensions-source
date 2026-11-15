// Fetches cover images from a blank page on the site's origin and posts each one back as base64.
(bridge, urls) => {
    const post = (message) => window[bridge].postMessage(JSON.stringify(message));
    const base64 = (buffer) => {
        const bytes = new Uint8Array(buffer);
        let binary = '';
        for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
        return btoa(binary);
    };
    for (const url of urls) {
        fetch(url, { credentials: 'include' })
            .then(async (response) => {
                if (!response.ok) throw new Error(`HTTP error ${response.status}`);
                post({ url, type: response.headers.get('Content-Type'), data: base64(await response.arrayBuffer()) });
            })
            .catch((e) => post({ url, error: String(e?.message ?? e) }));
    }
}
