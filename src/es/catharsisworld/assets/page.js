(read) => () => {
    if ([...document.scripts].some((script) => script.textContent.includes('_cf_chl_opt'))) return { challenge: true };
    // The whole document is parsed; images and late scripts may still load
    if (document.readyState === 'loading') return null;
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
    const consent = [...document.querySelectorAll('.fc-cta-do-not-consent, .fc-cta-consent')].filter(visible);
    const decline = consent.find((button) => button.matches('.fc-cta-do-not-consent')) ?? consent[0];
    if (decline && spot(decline)) return { obstacle: spot(decline) };
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
