() => {
    const ORIGIN = `${location.origin}/`;

    const urlIn = (image) =>
        fetch(image.src)
            .then((response) => response.blob())
            .then((blob) => blob.slice(-1024).arrayBuffer())
            .then((buffer) => {
                const text = new TextDecoder('latin1').decode(buffer);
                const start = text.lastIndexOf(ORIGIN);
                if (start < 0) throw new Error();
                return text.slice(start).match(/^[\x21-\x7e]+/)[0];
            });

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
            Promise.all(slots.map(urlIn)).then(
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
