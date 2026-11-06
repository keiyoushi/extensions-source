// Reads one page slot of the reader: its image, or the image pieces it lays out over a grid in percent of the page.
// `count` is the number of slots once the chapter's media has arrived; a slot not loaded yet is scrolled to.
(index) => {
    if (/\b5\d\d:/.test(document.title)) return { error: performance.timeOrigin };
    const fetched = performance
        .getEntriesByType('resource')
        .some((entry) => entry.name.includes('/chapter-deferred-media/') && entry.responseEnd > 0);
    const row = document.querySelector('[data-reader-image-row]');
    if (!row) return {};
    const list = row.parentElement;
    // The reader's status row: its security check before the remaining pages, "loading the remaining pages" after,
    // with a reload button when that failed.
    const status = [...list.children].find((child) => !child.querySelector('img') && child.textContent.trim());
    if (!fetched) {
        if (!status || status.querySelector('button')) return {};
        status.scrollIntoView({ block: 'center' });
        return { check: true };
    }
    if (status) return status.querySelector('button') ? { error: performance.timeOrigin } : {};
    const count = list.children.length;
    const slot = list.children[index];
    if (!slot) return { count };
    const images = [...slot.querySelectorAll('img')].filter(
        (image) =>
            /\.procomic\.im\/(i\/|chapters\/|api\/cdn-image\?)/.test(image.src) &&
            image.checkVisibility({ visibilityProperty: true, opacityProperty: true }),
    );
    // The reader loads images lazily, as they come near the screen.
    if (!images.length || images.some((image) => !image.complete)) {
        slot.scrollIntoView({ block: 'center' });
        return { count };
    }
    const pieces = images.filter((image) => image.style.left);
    if (!pieces.length) return { count, origin: location.origin, url: images[0].src };
    return {
        count,
        origin: location.origin,
        pieces: pieces.map((piece) => ({
            url: piece.src,
            left: parseFloat(piece.style.left) / 100,
            top: parseFloat(piece.style.top) / 100,
            width: parseFloat(piece.style.width) / 100,
            height: parseFloat(piece.style.height) / 100,
        })),
    };
}
