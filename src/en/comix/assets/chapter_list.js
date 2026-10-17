// Chapter list through the site's own chapters method, newest uploads first, up to a page with a
// known chapter; pages past a failure are left out. Each call follows a tap in the chapter section.
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
    // The site's method: two arguments with an object default for the second, items mapped through
    // a named mapper, the params forwarded to the GET helper
    const isChapterMethod = (value) => {
        const source = typeof value === "function" && String(value);
        return !!source &&
            /^(?:async [\w$]+ ?|async ?|[\w$]+ ?)?\([\w$]+,[\w$]+=\{\}\)(?:=>|\{)/.test(source) &&
            source.includes(".items.map(") &&
            source.includes(".meta") &&
            source.includes("{params:") &&
            !/\.items\.map\([\w$]+=>/.test(source);
    };
    // The walk reads the raw pages the helper returns to the site's own code
    const findChaptersApi = siteApi((members, isGet) => {
        const method = members.map((member) => member.value).find(isChapterMethod);
        const target = members.find((member) => isGet(member.value));
        if (!method || !target) return null;
        const records = [];
        const original = target.value;
        const isChapterList = (url) => /^\/manga\/[^/]+\/chapters$/.test(String(url));
        target.owner[target.key] = async (url, config) => {
            const params = config && config.params;
            try {
                const result = await original.call(target.owner, url, config);
                if (isChapterList(url)) records.push({ params, result });
                return result;
            } catch (error) {
                if (isChapterList(url)) records.push({ params, error });
                throw error;
            }
        };
        return { method, records };
    });
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

    const readPage = async (api, page) => {
        await tapSection();
        const before = api.records.length;
        try {
            await api.method(mangaId, { page, limit: 20, order: { created_at: "desc" } });
        } catch (e) {
            // The failed request is recorded; its error carries the status
        }
        // The method's return has key names that change every build; the raw page the helper hands
        // it does not. The records can also hold the site's own requests, so ours is matched by params
        const record = api.records.slice(before).find(
            (record) => record.params && record.params.page === page && record.params.order &&
                record.params.order.created_at === "desc",
        );
        if (!record) throw new Error(`Chapter page ${page} didn't come back`);
        if (record.error) {
            const status = record.error && record.error.response && record.error.response.status;
            throw new Error(status === 502
                ? `Comix blocked chapter page ${page} (HTTP 502). Try again later, or change the user agent in Advanced settings`
                : `Chapter page ${page} failed: ${status ? `HTTP ${status}` : record.error && record.error.message || record.error}`);
        }
        const items = record.result && record.result.items;
        const meta = record.result && record.result.meta;
        if (!Array.isArray(items) || !items.every(isChapter) || !meta ||
            typeof meta.lastPage !== "number" || typeof meta.total !== "number") {
            throw new Error(`Chapter page ${page} came back in a shape the extension doesn't recognise`);
        }
        return record.result;
    };

    const walk = async () => {
        const api = await findChaptersApi();
        if (!api) return finish(false, "Could not find the site's chapters API");
        await waitForSection();
        const ids = new Set();
        let meta;
        let removed = false;
        const add = (result) => {
            chapters.push(...result.items);
            result.items.forEach((item) => ids.add(item.id));
            if (meta && result.meta.total < meta.total) removed = true;
            meta = result.meta;
        };
        for (let page = 1; ; page++) {
            const result = await readPage(api, page);
            add(result);
            if (result.items.some((item) => known.has(item.id))) return finish(true);
            if (page >= Math.min(meta.lastPage, maxPages)) break;
        }
        // Chapters uploaded during the walk, from the top of the list
        for (let page = 1; ids.size < meta.total && page <= Math.min(meta.lastPage, maxPages); page++) {
            const result = await readPage(api, page);
            const overlaps = result.items.some((item) => ids.has(item.id));
            add(result);
            if (overlaps) break;
        }
        // A chapter removed during the walk moves the rest up a page boundary, skipping one
        if (removed) return finish(false, "Chapters were removed on the site during the refresh");
        return finish(ids.size === meta.total, `Got ${ids.size} of ${meta.total} chapters`);
    };

    const start = () => walk().catch((e) => finish(false, e && e.message || String(e)));
    if (document.readyState === "complete") start();
    else window.addEventListener("load", start, { once: true });
}
