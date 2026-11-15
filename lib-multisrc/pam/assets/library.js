// The series cards and page numbers the library page renders.
(settleMs) => {
    const main = document.querySelector('main');
    if (!main) return null;

    const covers = new Map();
    const titles = new Map();
    for (const link of main.querySelectorAll('a[href^="/serie/"]:not([href*="/chapter/"])')) {
        const path = link.getAttribute('href');
        // Animated covers are videos with a poster
        const img = link.querySelector('img');
        const video = link.querySelector('video[poster]');
        if (img || video) covers.set(path, img ? img.src : video.poster);
        else if (link.textContent.trim()) titles.set(path, link.textContent.trim());
    }
    const items = [...titles].filter(([path]) => covers.has(path)).map(([path, title]) => ({ path, title, cover: covers.get(path) }));

    // Current page in the "go to" field, last page after it as "/ N"
    const goTo = main.querySelector('input[inputmode="numeric"]');
    const last = goTo && Number(goTo.parentElement.parentElement.textContent.match(/\/\s*(\d+)/)?.[1]);
    const page = goTo && Number(goTo.value);

    // No cards and no pager: an empty result once the page has settled
    const settled = performance.now() - performance.getEntriesByType('navigation')[0].loadEventEnd > settleMs;
    if (!items.length && !settled) return null;
    return { items, hasNextPage: Boolean(page && last && page < last) };
}
