// Posts the page images the reader shows (at least half the window wide) once the page has loaded and their count holds;
// an empty list when the loaded page shows none. Runs at document start to put the reader in its default layout first.
(bridge) => {
    const settingsKey = 'komiwa-reader-settings';
    const settings = JSON.parse(localStorage.getItem(settingsKey) || '{}');
    const layout = { readingLayout: 'cascade', zoomLevel: 200, imageScaleMode: 'width', doublePage: false };
    localStorage.setItem(settingsKey, JSON.stringify({ ...settings, ...layout }));
    let last = 0;
    let empty = 0;
    const timer = setInterval(() => {
        if (document.readyState !== 'complete') return;
        const pages = new Set(
            [...document.images]
                .filter((image) => image.src.startsWith('http') && image.clientWidth >= innerWidth / 2)
                .map((image) => image.src),
        );
        if ((pages.size > 0 && pages.size === last) || ++empty > 20) {
            clearInterval(timer);
            window[bridge].postMessage(JSON.stringify([...pages]));
        }
        if (pages.size > 0) empty = 0;
        last = pages.size;
    }, 500);
}
