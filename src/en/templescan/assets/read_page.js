// The page's status, rendered HTML and the site's own decoded data, found in its React state by shape.
(kind, settled) => {
    const status = performance.getEntriesByType('navigation')[0]?.responseStatus || 200;
    if (window._cf_chl_opt) return { status, cloudflareChallenge: true, html: '', data: null };
    if (status >= 400) return { status, cloudflareChallenge: false, html: '', data: null };

    const isObjects = (v, ...keys) => Array.isArray(v) && v.length > 0 && v[0] !== null && typeof v[0] === 'object' && keys.every(k => k in v[0]);
    const shapes = {
        catalog: v => isObjects(v, 'series_slug', 'title', 'total_views'),
        chapters: v => isObjects(v, 'slug', 'createdAt'),
        pages: v => Array.isArray(v) && v.length > 0 && typeof v[0] === 'string' && v[0].includes('/uploads/'),
    };
    const matches = shapes[kind];
    const html = () => (kind === 'chapters' ? document.documentElement.outerHTML : '');
    // Shallowest depth each object was searched at; a deeper first visit must not hide it
    const seen = new Map();
    const search = (value, depth) => {
        if (value === null || typeof value !== 'object' || value.$$typeof || seen.get(value) <= depth) return null;
        seen.set(value, depth);
        if (matches(value)) return value;
        // Long arrays are data of another shape; short ones include React's [value, deps] memo pairs
        if (depth >= 2 || (Array.isArray(value) && value.length > 4)) return null;
        for (const child of Object.values(value)) {
            const found = search(child, depth + 1);
            if (found) return found;
        }
        return null;
    };

    for (const element of document.querySelectorAll('body *')) {
        const fiberKey = Object.keys(element).find(k => k.startsWith('__reactFiber$'));
        let fiber = fiberKey && element[fiberKey];
        for (let level = 0; fiber && level < 6; level++, fiber = fiber.return) {
            let found = search(fiber.memoizedProps, 0);
            for (let hook = fiber.memoizedState; !found && hook; hook = hook.next) {
                found = search(hook.memoizedState, 0);
            }
            if (found) return { status, cloudflareChallenge: false, html: html(), data: found };
        }
    }
    // A page that has settled without the data has none, like a locked chapter
    return settled ? { status, cloudflareChallenge: false, html: html(), data: null } : null;
}
