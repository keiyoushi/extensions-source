(() => {
    "use strict";

    const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
    const waitFor = async (check, { timeoutMs = 30000, intervalMs = 250 } = {}) => {
        const deadline = Date.now() + timeoutMs;
        for (;;) {
            let value = null;
            try { value = check(); } catch (e) { value = null; }
            if (value) return value;
            if (Date.now() >= deadline) return null;
            await sleep(Math.min(intervalMs, Math.max(1, deadline - Date.now())));
        }
    };
    const waitForStable = async (probe, { timeoutMs = 30000, intervalMs = 300, stableRounds = 2 } = {}) => {
        const deadline = Date.now() + timeoutMs;
        let lastSignature = null;
        let stable = 0;
        let value = null;
        while (Date.now() < deadline) {
            value = probe();
            const signature = JSON.stringify(value);
            if (signature === lastSignature) {
                if (++stable >= stableRounds) return value;
            } else {
                stable = 0;
                lastSignature = signature;
            }
            await sleep(intervalMs);
        }
        return value;
    };
    const readInitialData = (doc) => {
        const candidates = [];
        const byId = doc.querySelector("script#initial-data");
        if (byId) candidates.push(byId);
        for (const script of doc.querySelectorAll('script[type="application/json"]')) {
            if (script !== byId) candidates.push(script);
        }
        for (const script of candidates) {
            try {
                const data = JSON.parse(script.textContent || "null");
                if (data && typeof data === "object") return data;
            } catch (e) {  }
        }
        return null;
    };
    const walkJson = (data, visit, maxDepth = 12) => {
        const stack = [[data, 0]];
        while (stack.length) {
            const [value, depth] = stack.pop();
            visit(value, depth);
            if (depth >= maxDepth || !value || typeof value !== "object") continue;
            const children = Array.isArray(value) ? value : Object.values(value);
            for (let i = children.length - 1; i >= 0; i--) stack.push([children[i], depth + 1]);
        }
    };
    const findObjects = (data, pred) => {
        const found = [];
        walkJson(data, (value) => {
            if (value && typeof value === "object" && !Array.isArray(value) && pred(value)) {
                found.push(value);
            }
        });
        return found;
    };
    const findListParams = (data) => {
        const paramObjects = findObjects(data, (o) =>
            typeof o.page === "number" &&
            (typeof o.limit === "number" || typeof o.perPage === "number"));
        if (!paramObjects.length) return null;
        const params = paramObjects[0];
        const container = findObjects(data, (o) => o.params === params && typeof o.perPage === "number")[0];
        return { params, perPage: container ? container.perPage : null };
    };
    const MANGA_PATH = /^\/title\/([a-z0-9]+)-/i;
    const CHAPTER_PATH = /^\/title\/([a-z0-9]+)-[^/]+\/(\d+)-chapter-(\d+(?:\.\d+)?)/i;
    const pathOf = (href) => {
        if (!href) return "";
        if (/^https?:\/\//i.test(href)) {
            try { return new URL(href).pathname; } catch (e) { return href; }
        }
        return href.split(/[?#]/)[0];
    };
    const parseMangaPath = (path) => {
        const match = MANGA_PATH.exec(path || "");
        return match ? { hid: match[1] } : null;
    };
    const parseChapterPath = (path) => {
        const match = CHAPTER_PATH.exec(path || "");
        return match ? { hid: match[1], id: Number(match[2]), number: parseFloat(match[3]) } : null;
    };
    const textOf = (el) => (el && el.textContent ? el.textContent : "").replace(/\s+/g, " ").trim();
    const imageSrc = (img) => {
        const src = img.getAttribute("src") || "";
        const lazy = img.getAttribute("data-src") || img.getAttribute("data-lazy-src") || "";
        if (lazy && (!src || src.startsWith("data:"))) return lazy;
        return src || lazy || null;
    };

    const OPTIONS = Object.assign(
        { timeoutMs: 30000, pollMs: 250, stableRounds: 2 },
        window.__comixHarvestOptions || {});

    const cardRoot = (anchor) => {
        let el = anchor;
        for (let depth = 0; el && depth < 5; depth++) {
            if (el.querySelector && el.querySelector("img")) return el;
            el = el.parentElement;
        }
        return anchor;
    };

    const bestTitle = (entry) => {
        const candidates = [];
        for (const anchor of entry.anchors) {
            const img = anchor.querySelector("img");
            if (img) candidates.push((img.getAttribute("alt") || "").trim());
            candidates.push(textOf(anchor));
            candidates.push((anchor.getAttribute("aria-label") || "").trim());
            candidates.push((anchor.getAttribute("title") || "").trim());
        }
        const usable = candidates.filter((t) => t && t.length >= 2 && t.length <= 140);
        if (usable.length) return usable[0];
        const slug = (entry.url.split("/").pop() || "").replace(/^[a-z0-9]+-/i, "");
        const words = slug.replace(/-/g, " ").trim();
        return words ? words.replace(/\b\w/g, (c) => c.toUpperCase()) : "";
    };

    const collect = () => {
        const byHid = new Map();
        for (const anchor of Array.from(document.querySelectorAll("a[href]"))) {
            const path = pathOf(anchor.getAttribute("href") || "");
            const manga = parseMangaPath(path);
            if (!manga || parseChapterPath(path)) continue;
            let entry = byHid.get(manga.hid);
            if (!entry) {
                entry = { hid: manga.hid, url: path, anchors: [], cover: null };
                byHid.set(manga.hid, entry);
            }
            entry.anchors.push(anchor);
            if (!entry.cover) {
                const card = cardRoot(anchor);
                const img = card ? card.querySelector("img") : null;
                if (img) entry.cover = imageSrc(img);
            }
        }
        return Array.from(byHid.values()).map((entry) => ({
            hid: entry.hid,
            url: entry.url,
            title: bestTitle(entry),
            cover: entry.cover,
        }));
    };

    const harvest = async () => {
        const data = readInitialData(document);
        const list = data ? findListParams(data) : null;
        let items = collect();
        if (!items.length) {

            await waitFor(() => collect().length > 0 || null,
                { timeoutMs: OPTIONS.timeoutMs, intervalMs: OPTIONS.pollMs });
            items = collect();
        }
        if (items.length) {

            items = await waitForStable(collect,
                { timeoutMs: Math.min(OPTIONS.timeoutMs, 8000), intervalMs: OPTIONS.pollMs, stableRounds: OPTIONS.stableRounds }) || items;
        }
        const lateData = readInitialData(document);
        const lateList = lateData ? findListParams(lateData) : null;
        const finalList = lateList || list;
        return {
            page: "browse",
            listParams: finalList ? finalList.params : null,
            perPage: finalList ? finalList.perPage : null,
            count: items.length,
            items,
        };
    };

    const done = (result) => {
        window.__comixHarvest = result;
        console.log("[comix-harvest:browse] " + JSON.stringify(result));
    };
    window.__comixHarvestPromise = (async () => {
        try {
            const result = await harvest();
            done(result);
            return result;
        } catch (e) {
            const result = { page: "browse", error: String((e && e.message) || e), items: [], count: 0 };
            done(result);
            return result;
        }
    })();
})();
