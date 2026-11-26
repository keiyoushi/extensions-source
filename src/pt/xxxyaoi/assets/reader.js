() => {
    const MAX_PADDING = 64;
    const fetched = [];
    new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
            if (entry.name.includes('/WP-manga/data/') && entry.responseStatus === 200) fetched.push([entry.name, entry.decodedBodySize]);
        }
    }).observe({ type: 'resource', buffered: true });

    const inOrder = async (slots) => {
        const sizes = await Promise.all(
            slots.map((image) =>
                fetch(image.src)
                    .then((response) => response.blob())
                    .then((blob) => blob.size),
            ),
        );
        const matches = (padding) => {
            const left = [...fetched];
            const urls = [];
            for (const size of sizes) {
                const index = left.findIndex(([, fetchedSize]) => fetchedSize === size - padding);
                if (index < 0) return null;
                urls.push(left.splice(index, 1)[0][0]);
            }
            return urls;
        };
        for (let padding = 0; padding <= MAX_PADDING; padding++) {
            const urls = matches(padding);
            if (urls) return urls;
        }
        throw new Error();
    };

    let pages = null;
    let state = '';
    let changedAt = 0;
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
        const loaded = slots.length && !pending.length && fetched.length >= slots.length;
        if (loaded && state !== 'ordering') {
            inOrder(slots).then(
                (list) => {
                    pages = list;
                },
                () => {
                    pages = [];
                },
            );
        }
        pending[0]?.scrollIntoView({ block: 'center' });
        const now = loaded ? 'ordering' : `${shown.length}/${slots.length}/${fetched.length}`;
        if (now !== state) {
            state = now;
            changedAt = Date.now();
        }
        return Date.now() - changedAt > 20000 ? snapshot([]) : null;
    };
}
