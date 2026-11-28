(startSize) => {
    const fingerprint = (image) =>
        fetch(image.src)
            .then((response) => response.blob())
            .then((blob) => blob.slice(0, startSize).arrayBuffer())
            .then((buffer) =>
                crypto.subtle.digest('SHA-256', buffer).then((hash) => ({
                    length: buffer.byteLength,
                    hash: [...new Uint8Array(hash)].map((byte) => byte.toString(16).padStart(2, '0')).join(''),
                })),
            );

    let pages = null;
    let state = '';
    let changedAt = 0;
    let solvedAt = 0;
    return () => {
        if (document.readyState !== 'complete') return null;
        const snapshot = (list) => ({
            url: location.href,
            html: document.documentElement.outerHTML,
            session: { ...sessionStorage },
            pages: list,
        });
        if (document.querySelector('iframe[src*="player"]')) return snapshot([]);
        if (pages) return snapshot(pages);
        const shown = [...document.images].filter((image) => image.src.startsWith('blob:'));
        const slots = shown.length ? [...shown[0].parentElement.parentElement.querySelectorAll('img')] : [];
        const pending = slots.filter((image) => !image.src.startsWith('blob:'));
        const loaded = slots.length && !pending.length;
        if (loaded && state !== 'ordering') {
            Promise.all(slots.map(fingerprint)).then(
                (list) => {
                    pages = list;
                },
                () => {
                    pages = [];
                },
            );
        }
        pending[0]?.scrollIntoView({ block: 'center' });
        const tokens = [...document.querySelectorAll('[name="cf-turnstile-response"]')];
        const gate = tokens.some((input) => !input.value) ? 'gate' : '';
        const now = loaded ? 'ordering' : `${shown.length}/${slots.length}${gate}`;
        if (now !== state) {
            state = now;
            changedAt = Date.now();
        }
        if (!gate && Date.now() - changedAt > 20000) return snapshot([]);
        if (!slots.length && tokens.some((input) => input.value)) {
            solvedAt ||= Date.now();
            if (Date.now() - solvedAt > 3000) return { ...snapshot([]), reload: true };
        }
        return null;
    };
}
