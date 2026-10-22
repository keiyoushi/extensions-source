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
    const anchoredNumber = (data, anchor) => {
        let best = null;
        let bestCount = Infinity;
        for (const obj of findObjects(data, (o) => Object.values(o).some((v) => v === anchor))) {
            const numbers = Object.values(obj).filter((v) => typeof v === "number" && isFinite(v));
            if (numbers.length && numbers.length < bestCount) {
                best = numbers[0];
                bestCount = numbers.length;
            }
        }
        return best;
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

    const OPTIONS = Object.assign(
        { timeoutMs: 45000, settleMs: 5000, pollMs: 250, maxRounds: 40, stopAtIds: [] },
        window.__comixHarvestOptions || {});
    const stopAt = new Set((OPTIONS.stopAtIds || []).map(Number));

    const rowRoot = (anchor) => {
        let el = anchor;
        for (let depth = 0; depth < 4 && el.parentElement; depth++) {
            const parent = el.parentElement;
            const links = Array.from(parent.querySelectorAll("a[href]"))
                .filter((x) => parseChapterPath(pathOf(x.getAttribute("href") || "")));
            if (links.length !== 1) break;
            el = parent;
        }
        return el;
    };

    const groupOf = (row, anchor, number) => {
        if (!row) return null;
        let rest = textOf(row);
        const own = textOf(anchor);
        if (own) rest = rest.split(own).join(" ");
        rest = rest.replace(new RegExp("chapter\\s*" + number, "i"), " ");
        rest = rest.replace(/\b\d+\s*(?:seconds?|mins?|minutes?|hours?|days?|weeks?|months?|years?)\s*ago\b/gi, " ");
        rest = rest.replace(/\b\d{4}-\d{2}-\d{2}\b/g, " ");
        rest = rest.replace(/\s+/g, " ").trim();
        return rest.length >= 2 && rest.length <= 60 && /[a-z]/i.test(rest) ? rest : null;
    };

    const findMoreControl = () => {
        const controls = Array.from(document.querySelectorAll("button, a[href], [role='button']"));
        const scored = [];
        for (const el of controls) {
            if (el.disabled) continue;
            if (el.getAttribute("aria-current") === "page") continue;
            if (el.getAttribute("aria-disabled") === "true") continue;
            const href = el.getAttribute("href");
            if (href && parseChapterPath(pathOf(href))) continue;
            const label = (textOf(el) || el.getAttribute("aria-label") || "").trim();
            if (!label) continue;
            let score = -1;
            if (/load\s*more|show\s*more|view\s*more|more\s+chapters/i.test(label)) score = 3;
            else if (/^next\b|next\s*(page)?\s*[›»→>]*$/i.test(label)) score = 2;
            else if (/^\d{1,4}$/.test(label)) score = 1;
            if (score >= 0) scored.push({ el, score });
        }
        scored.sort((a, b) => b.score - a.score);
        return scored.length ? scored[0].el : null;
    };

    const harvest = async () => {
        const here = parseMangaPath(location.pathname || pathOf(location.href || ""));
        const hid = here ? here.hid : null;
        const data = readInitialData(document);
        const mangaId = data && hid ? anchoredNumber(data, hid) : null;

        const byId = new Map();
        const absorb = () => {
            let added = 0;
            for (const anchor of Array.from(document.querySelectorAll("a[href]"))) {
                const path = pathOf(anchor.getAttribute("href") || "");
                const chapter = parseChapterPath(path);
                if (!chapter) continue;
                if (hid && chapter.hid !== hid) continue;
                if (byId.has(chapter.id)) continue;
                const row = rowRoot(anchor);
                byId.set(chapter.id, {
                    id: chapter.id,
                    number: chapter.number,
                    url: path,
                    text: row ? textOf(row) : textOf(anchor),
                    group: groupOf(row, anchor, chapter.number),
                });
                added++;
            }
            return added;
        };
        const reachedKnown = () => Array.from(byId.keys()).some((id) => stopAt.has(id));

        await waitFor(() => (absorb(), byId.size > 0) || null,
            { timeoutMs: OPTIONS.timeoutMs, intervalMs: OPTIONS.pollMs });

        let rounds = 0;
        let complete = false;
        let stoppedAtKnown = reachedKnown();
        while (!stoppedAtKnown && rounds < OPTIONS.maxRounds) {
            const control = findMoreControl();
            if (!control) {
                complete = byId.size > 0;
                break;
            }
            const before = byId.size;
            try { control.click(); } catch (e) { break; }
            rounds++;
            const grew = await waitFor(() => (absorb(), byId.size > before) || null,
                { timeoutMs: OPTIONS.settleMs, intervalMs: OPTIONS.pollMs });
            if (reachedKnown()) {
                stoppedAtKnown = true;
                break;
            }
            if (!grew) {

                complete = byId.size > 0;
                break;
            }
        }

        const chapters = Array.from(byId.values())
            .sort((a, b) => (b.number - a.number) || (b.id - a.id));
        return {
            page: "manga",
            hid,
            mangaId,
            count: chapters.length,
            complete,
            stoppedAtKnown,
            rounds,
            chapters,
        };
    };

    const done = (result) => {
        window.__comixHarvest = result;
        console.log("[comix-harvest:chapters] " + JSON.stringify(result));
    };
    window.__comixHarvestPromise = (async () => {
        try {
            const result = await harvest();
            done(result);
            return result;
        } catch (e) {
            const result = { page: "manga", error: String((e && e.message) || e), chapters: [], count: 0 };
            done(result);
            return result;
        }
    })();
})();
