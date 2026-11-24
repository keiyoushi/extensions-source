() => {
    let count = 0;
    let countedAt = 0;
    return () => {
        if (document.readyState !== 'complete') return null;
        const snapshot = () => ({ url: location.href, html: document.documentElement.outerHTML, session: { ...sessionStorage } });
        if (document.querySelector('iframe[src*="player"]')) return snapshot();
        const pages = [...document.images].filter((image) =>
            [...image.attributes].some((attribute) => attribute.value.includes('/WP-manga/data/')),
        );
        if (!countedAt || pages.length !== count) {
            count = pages.length;
            countedAt = Date.now();
            return null;
        }
        return Date.now() - countedAt > (count > 0 ? 600 : 20000) ? snapshot() : null;
    };
}
