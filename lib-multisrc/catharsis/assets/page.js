// Wraps a page reader so it runs only once the page has loaded and isn't a challenge or an error page, and nothing the
// site puts over it (its ads' consent dialog, a floating ad) is in the way.
(read) => () => {
    // Cloudflare's challenge page sets up its options in an inline script
    if ([...document.scripts].some((script) => script.textContent.includes('_cf_chl_opt'))) return { challenge: true };
    // The whole document is parsed; images and late scripts may still load
    if (document.readyState === 'loading') return null;
    // Google's full-page ad between pages, which closes with its address marker
    if (location.hash === '#google_vignette') {
        location.hash = '';
        return null;
    }
    const status = performance.getEntriesByType('navigation')[0]?.responseStatus ?? 0;
    if (status >= 400) return { status };

    const visible = (el) => el && el.getClientRects().length > 0;
    const spot = (el) => {
        const box = el.getBoundingClientRect();
        const x = box.left + box.width / 2;
        const y = box.top + box.height / 2;
        return y > 0 && y < innerHeight && el.contains(document.elementFromPoint(x, y)) ? { x, y, scale: devicePixelRatio } : null;
    };
    // Google's consent dialog for the site's ads, on visitors from some countries: declined where it offers to
    const consent = [...document.querySelectorAll('.fc-cta-do-not-consent, .fc-cta-consent')].filter(visible);
    const decline = consent.find((button) => button.matches('.fc-cta-do-not-consent')) ?? consent[0];
    if (decline && spot(decline)) return { obstacle: spot(decline) };
    // A floating ad: a fixed layer around an iframe, closed by its own button
    for (const frame of document.querySelectorAll('iframe')) {
        let layer = frame.parentElement;
        while (layer && layer !== document.body && getComputedStyle(layer).position !== 'fixed') layer = layer.parentElement;
        if (!layer || layer === document.body || !visible(frame)) continue;
        const close = [...layer.querySelectorAll('button')].find((button) => visible(button) && !button.contains(frame));
        if (close && spot(close)) return { obstacle: spot(close) };
    }

    const result = read();
    return result === null ? null : { result };
}
