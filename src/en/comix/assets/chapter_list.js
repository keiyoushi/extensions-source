// Chapter list through the site's own API client, newest uploads first, up to a page with a known chapter;
// pages past a failure are left out. Each call follows a swipe, the user input the site's client checks for.
(siteApi, knownIds, mangaId, chapterPath, maxPages, swipe, pass) => {
    const SWIPE_TIMEOUT_MS = 2000;
    const SCROLL_BACK_CHANCE = 0.3;
    const known = new Set(knownIds);
    const chapters = [];
    const isChapter = (item) => item &&
        typeof item.id === "number" &&
        typeof item.number === "number" &&
        typeof item.url === "string" &&
        item.url.includes(chapterPath);
    const findChapterPage = siteApi((route) => typeof route === "function" &&
        /^\(?[\w$]+\)?=>`\/manga\/\$\{[\w$]+\}\/chapters`$/.test(String(route)));
    const finish = (complete, error) => pass(JSON.stringify({ chapters, complete, error }));

    let swiped = null;
    // Resolved once the finger is up and the event has reached every listener, the site's own included
    document.addEventListener("touchend", (event) => {
        if (event.isTrusted && swiped) setTimeout(swiped);
    }, true);
    const scroll = () => new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
            swiped = null;
            reject(new Error("The WebView didn't receive the swipe"));
        }, SWIPE_TIMEOUT_MS);
        swiped = () => {
            clearTimeout(timer);
            swiped = null;
            resolve();
        };
        // A finger's swipe up, sometimes back down once the page has scrolled, in CSS pixels
        const width = window.innerWidth;
        const height = window.innerHeight;
        const between = (from, to) => from + Math.random() * (to - from);
        const back = window.scrollY > height && Math.random() < SCROLL_BACK_CHANCE;
        const x = width * between(0.3, 0.7);
        const y = height * (back ? between(0.25, 0.45) : between(0.55, 0.75));
        const distance = height * between(0.28, 0.36) * (back ? -1 : 1);
        swipe([x, y, x + width * between(-0.03, 0.03), y - distance]);
    });

    const walk = async () => {
        const chapterPage = await findChapterPage();
        if (!chapterPage) return finish(false, "Could not find the site's chapters API");
        for (let page = 1; ; page++) {
            await scroll();
            let result;
            try {
                result = await chapterPage(mangaId, { page, limit: 20, order: { created_at: "desc" } });
            } catch (e) {
                const status = e && e.response && e.response.status;
                return finish(false, status === 502
                    ? `Comix blocked chapter page ${page} (HTTP 502). Try again later, or change the user agent in Advanced settings`
                    : `Chapter page ${page} failed: ${status ? `HTTP ${status}` : e && e.message || e}`);
            }
            const items = result && result.items;
            const meta = result && result.meta;
            if (!Array.isArray(items) || !items.every(isChapter) || !meta ||
                typeof meta.lastPage !== "number" || typeof meta.total !== "number") {
                return finish(false, `Chapter page ${page} came back in a shape the extension doesn't recognise`);
            }
            chapters.push(...items);
            if (items.some((item) => known.has(item.id))) return finish(true);
            if (page >= Math.min(meta.lastPage, maxPages)) {
                const count = new Set(chapters.map((item) => item.id)).size;
                return finish(count === meta.total, `Got ${count} of ${meta.total} chapters`);
            }
        }
    };

    const start = () => walk().catch((e) => finish(false, e && e.message || String(e)));
    if (document.readyState === "complete") start();
    else window.addEventListener("load", start, { once: true });
}
