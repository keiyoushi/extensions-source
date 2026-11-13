// Where the reader's page plates are on screen and which ones it has drawn.
(settleMs) => {
    const section = document.querySelector('section.reader-plates');
    // No reader: the site's own check before reading, or, once the page has settled, a chapter it keeps for accounts
    if (!section) {
        const gate = { scale: devicePixelRatio, height: innerHeight, clear: [], plates: [] };
        if (document.getElementById('captcha-container')) return { ...gate, gate: 'check' };
        const settled = performance.now() - performance.getEntriesByType('navigation')[0].loadEventEnd > settleMs;
        return settled && document.querySelector('main') ? { ...gate, gate: 'locked' } : null;
    }
    // Screen rows where nothing (an ad, a toolbar) covers the reader: the boxes of whatever sits on top of it
    const box = section.getBoundingClientRect();
    const xs = [0.02, 0.2, 0.35, 0.5, 0.65, 0.8, 0.98].map((f) => box.left + box.width * f);
    const covered = [];
    for (let y = Math.max(0, box.top); y < Math.min(innerHeight, box.bottom); y += 8) {
        for (const x of xs) {
            const cover = document.elementFromPoint(x, y);
            if (!cover || section.contains(cover)) continue;
            // The covering element's own box, and at least the sampled row
            const rect = cover.getBoundingClientRect();
            covered.push([Math.min(rect.top, y - 8) - 1, Math.max(rect.bottom, y + 8) + 1]);
        }
    }
    // Fixed or sticky layers above the page too: ads and toolbars that a tap would pass through
    const visible = (el) => {
        for (let node = el; node && node !== document.body; node = node.parentElement) {
            const style = getComputedStyle(node);
            if (style.visibility === 'hidden' || style.display === 'none' || Number(style.opacity) < 0.05) return false;
        }
        return true;
    };
    let dismiss = null;
    for (const el of document.body.getElementsByTagName('*')) {
        const style = getComputedStyle(el);
        if (style.position !== 'fixed' && style.position !== 'sticky') continue;
        if (!(Number(style.zIndex) > 0) || el.contains(section) || section.contains(el)) continue;
        const rect = el.getBoundingClientRect();
        if (rect.width < 1 || rect.height < 1 || rect.right <= box.left || rect.left >= box.right) continue;
        if (rect.bottom <= 0 || rect.top >= innerHeight || !visible(el)) continue;
        // A layer that only frames its children (a toolbar's full-width wrapper) covers where they are
        const parts = style.pointerEvents === 'none' ? [...el.children].map((child) => child.getBoundingClientRect()) : [rect];
        for (const part of parts) if (part.height >= 1 && part.width >= 1) covered.push([part.top - 1, part.bottom + 1]);
        // An ad: a floating box around an iframe, closed by its own button
        const close = el.querySelector('iframe') && el.querySelector('button');
        if (close && !dismiss) {
            const at = close.getBoundingClientRect();
            const x = at.left + at.width / 2;
            const y = at.top + at.height / 2;
            if (close.contains(document.elementFromPoint(x, y))) dismiss = { x, y, clear: true };
        }
    }
    const clear = [];
    let from = 0;
    for (const [top, bottom] of covered.sort((a, b) => a[0] - b[0])) {
        if (top > from) clear.push({ top: from, bottom: Math.min(top, innerHeight) });
        from = Math.max(from, bottom);
    }
    if (from < innerHeight) clear.push({ top: from, bottom: innerHeight });
    return {
        scale: devicePixelRatio,
        height: innerHeight,
        clear,
        dismiss,
        plates: [...section.children].map((plate) => {
            const canvas = plate.querySelector('canvas');
            const rect = (canvas ?? plate).getBoundingClientRect();
            // The plate's own reload button after a failed page
            const button = plate.querySelector('button');
            const retry = button?.getBoundingClientRect();
            return {
                top: rect.top,
                left: rect.left,
                width: rect.width,
                height: rect.height,
                drawn: Boolean(canvas && canvas.hasAttribute('width')),
                canvasWidth: canvas ? canvas.width : 0,
                retry: retry
                    ? (() => {
                          const x = retry.left + retry.width / 2;
                          const y = retry.top + retry.height / 2;
                          return { x, y, clear: y > 0 && y < innerHeight && button.contains(document.elementFromPoint(x, y)) };
                      })()
                    : null,
            };
        }),
    };
}
