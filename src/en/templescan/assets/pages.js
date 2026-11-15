// Collects the reader's page urls. It mounts images only around the reading position, so the page is zoomed out and
// scrolled to the first page still missing; each url is read as its image mounts, since the reader unmounts images again.
(_, state) => {
    if (!document.body) return null;
    if (/This chapter is early access/.test(document.body.innerText)) return { error: 'This chapter is early access and needs a subscription' };

    // Each page is a box with its zero-based `data-index`
    const readImage = image => {
        const box = image.closest('[data-index]');
        if (box && image.src) state.urls[box.dataset.index] = image.src;
    };
    if (!state.urls) {
        state.urls = {};
        new MutationObserver(records => {
            for (const record of records) {
                for (const node of record.addedNodes) {
                    if (node.nodeType !== Node.ELEMENT_NODE) continue;
                    if (node.tagName === 'IMG') readImage(node);
                    node.querySelectorAll('img').forEach(readImage);
                }
                if (record.type === 'attributes') readImage(record.target);
            }
        }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['src'] });
    }
    document.querySelectorAll('[data-index] img').forEach(readImage);

    // The site asks for an age confirmation on mature series and hides the pages behind it
    Array.from(document.querySelectorAll('button')).find(b => b.textContent.trim() === 'Yes, I am an adult')?.click();
    document.documentElement.style.zoom = '0.01';

    const boxes = Array.from(document.querySelectorAll('[data-index]'));
    if (boxes.length === 0) {
        // A chapter without pages shows none within a few seconds of loading
        if (document.readyState !== 'complete') return null;
        state.loadedAt = state.loadedAt || Date.now();
        return Date.now() - state.loadedAt > 8000 ? { pages: [] } : null;
    }
    const missing = boxes.find(box => !state.urls[box.dataset.index]);
    if (!missing) return { pages: boxes.map(box => state.urls[box.dataset.index]) };
    missing.scrollIntoView({ block: 'center' });
    return null;
}
