// Chapter list through the site's own API client, newest uploads first, up to a page with a known chapter;
// pages past a failure are left out. Each call follows a tap in the chapter section.
(siteApi, knownIds, mangaId, chapterPath, maxPages, touch, pass) => {
    const INPUT_TIMEOUT_MS = 2000;
    const SECTION_TIMEOUT_MS = 60000;
    const MAX_TAPS = 3;
    const MAX_SWIPES = 15;
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
    const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
    const between = (from, to) => from + Math.random() * (to - from);
    const section = () => document.querySelector(".mpage__chapters");

    // Resolves with the trusted event once the native input has reached the page
    const afterInput = (type, send) => new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
            document.removeEventListener(type, done, true);
            reject(new Error(`The WebView didn't receive the ${type === "click" ? "tap" : "swipe"}`));
        }, INPUT_TIMEOUT_MS);
        function done(event) {
            if (!event.isTrusted) return;
            document.removeEventListener(type, done, true);
            clearTimeout(timer);
            setTimeout(() => resolve(event));
        }
        document.addEventListener(type, done, true);
        send();
    });

    // Waits for the chapter section to draw
    const waitForSection = async () => {
        const deadline = Date.now() + SECTION_TIMEOUT_MS;
        while (Date.now() < deadline) {
            if (section() && section().querySelector(".npager, .mchap-row__primary, .uempty")) return;
            await sleep(200);
        }
        throw new Error("The chapter list didn't load");
    };

    // The pager's last fully shown button, else the active sort button; narrow screens cut the pager off
    const tapTarget = () => {
        const pager = section().querySelector(".npager");
        if (!pager) return section().querySelector(".useg__btn.is-on");
        const box = pager.getBoundingClientRect();
        const shown = [...pager.querySelectorAll("button")].filter((button) => {
            const rect = button.getBoundingClientRect();
            return rect.width > 0 && rect.left >= Math.max(box.left, 0) && rect.right <= Math.min(box.right, innerWidth);
        });
        return shown[shown.length - 1];
    };

    // The site's first-visit ad: a fixed link over the whole page that takes the first tap
    const isAdOverlay = (element) => element && element.tagName === "A" &&
        getComputedStyle(element).position === "fixed" &&
        element.getBoundingClientRect().width >= document.documentElement.clientWidth &&
        element.getBoundingClientRect().height >= document.documentElement.clientHeight;

    // Swipes until the target is on screen
    const scrollTo = async (target) => {
        let lastTop = null;
        for (let i = 0; i < MAX_SWIPES; i++) {
            const rect = target.getBoundingClientRect();
            if (rect.top > innerHeight * 0.15 && rect.bottom < innerHeight * 0.85) return;
            // The page's end keeps it out of the middle band
            if (rect.top === lastTop && rect.top >= 0 && rect.bottom <= innerHeight) return;
            lastTop = rect.top;
            const x = innerWidth * between(0.3, 0.7);
            const down = rect.top > innerHeight / 2;
            const y = innerHeight * (down ? between(0.6, 0.75) : between(0.25, 0.4));
            const distance = Math.min(Math.abs(rect.top - innerHeight / 2), innerHeight * between(0.3, 0.38)) * (down ? 1 : -1);
            await afterInput("touchend", () => touch([x, y, x + innerWidth * between(-0.03, 0.03), y - distance]));
            await sleep(between(250, 450));
        }
        throw new Error("Couldn't scroll to the chapter list");
    };

    // Taps the chapter section
    const tapSection = async () => {
        const stop = (event) => {
            if (!event.isTrusted) return;
            event.stopPropagation();
            event.preventDefault();
        };
        for (let attempt = 0; attempt < MAX_TAPS; attempt++) {
            const target = tapTarget();
            if (!target) throw new Error("The chapter list has nothing to tap");
            await scrollTo(target);
            const rect = target.getBoundingClientRect();
            const x = rect.left + rect.width * between(0.3, 0.7);
            const y = rect.top + rect.height * between(0.3, 0.7);
            const under = document.elementFromPoint(x, y);
            if (!target.contains(under) && !isAdOverlay(under)) {
                await sleep(between(400, 700));
                continue;
            }
            section().addEventListener("click", stop, true);
            try {
                const click = await afterInput("click", () => touch([x, y]));
                if (target.contains(click.target)) return;
            } catch {
                // A tap that stops a moving scroll gets no click
            } finally {
                section().removeEventListener("click", stop, true);
            }
            await sleep(between(400, 700));
        }
        throw new Error("Couldn't tap the chapter list");
    };

    const walk = async () => {
        const chapterPage = await findChapterPage();
        if (!chapterPage) return finish(false, "Could not find the site's chapters API");
        await waitForSection();
        for (let page = 1; ; page++) {
            await tapSection();
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
