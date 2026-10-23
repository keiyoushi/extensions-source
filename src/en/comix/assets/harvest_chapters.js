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
        { timeoutMs: 45000, settleMs: 5000, pollMs: 100, maxRounds: 40, stopAtIds: [] },
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
        rest = rest.replace(/·\s*\d+\s*·/g, " ");
        rest = rest.replace(/\b\d+\s*(?:seconds?|secs?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?|mos?|years?|yrs?|[smhdwy])\s*ago\b/gi, " ");
        rest = rest.replace(/\b\d{4}-\d{2}-\d{2}\b/g, " ");
        rest = rest.replace(/\s+/g, " ").trim();
        return rest.length >= 2 && rest.length <= 60 && /[a-z]/i.test(rest) ? rest : null;
    };

    const findMoreControl = (scope) => {
        const inOrder = Array.from(document.querySelectorAll("a[href], button, [role='button']"));
        const orderOf = new Map(inOrder.map((el, i) => [el, i]));
        let lastAnchorIdx = -1;
        for (const el of inOrder) {
            const href = el.getAttribute("href");
            if (href && parseChapterPath(pathOf(href))) lastAnchorIdx = orderOf.get(el);
        }
        const currents = inOrder.filter((el) => (orderOf.get(el) ?? -1) > lastAnchorIdx &&
            (el.getAttribute("aria-current") === "page" || /\bis-active\b/.test(String(el.className || ""))) &&
            /^\d{1,4}$/.test((textOf(el) || "").trim()));
        if (currents.length) {
            const cur = currents[0];
            const curNum = parseInt((textOf(cur) || "").trim(), 10);
            let box = cur.parentElement;
            for (let d = 0; d < 3 && box && box.tagName !== "NAV"; d++) box = box.parentElement;
            const boxEls = Array.from((box || cur.parentElement || document)
                .querySelectorAll("button, a[href], [role='button']"));
            let nextBtn = null;
            let numbered = null;
            for (const el of boxEls) {
                if (el.disabled || el.getAttribute("aria-disabled") === "true") continue;
                const label = (textOf(el) || el.getAttribute("aria-label") || "").trim();
                if (/next/i.test(label)) nextBtn = el;
                else if (/^\d{1,4}$/.test(label) && parseInt(label, 10) === curNum + 1) numbered = el;
            }
            return nextBtn || numbered || null;
        }
        const controls = Array.from((scope || document).querySelectorAll("button, a[href], [role='button']"));
        const scored = [];
        for (const el of controls) {
            if (el.disabled) continue;
            if (el.getAttribute("aria-current") === "page") continue;
            if (el.getAttribute("aria-disabled") === "true") continue;
            const href = el.getAttribute("href");
            if (href && parseChapterPath(pathOf(href))) continue;
            if (/desc/i.test(String(el.className || ""))) continue;
            const label = (textOf(el) || el.getAttribute("aria-label") || "").trim();
            if (!label) continue;
            const afterList = lastAnchorIdx < 0 || (orderOf.get(el) ?? -1) > lastAnchorIdx;
            let score = -1;
            if (/next/i.test(label)) score = afterList ? 5 : 2;
            else if (/load\s*more|show\s*more|view\s*more|more\s+chapters/i.test(label)) score = afterList ? 4 : -1;
            else if (/^\d{1,4}$/.test(label)) score = 1;
            if (score >= 0) scored.push({ el, score });
        }
        scored.sort((a, b) => b.score - a.score);
        return scored.length ? scored[0].el : null;
    };

    const describe = (el) => el ? {
        tag: el.tagName,
        cls: String(el.className || "").slice(0, 60),
        label: (textOf(el) || el.getAttribute("aria-label") || "").trim().slice(0, 40),
    } : null;

    const reportedTotal = () => {
        if (!document.body) return null;
        const m = /Showing\s+\d+\s+to\s+\d+\s+of\s+([\d,]+)\s+items/i.exec(textOf(document.body));
        return m ? parseInt(m[1].replace(/,/g, ""), 10) : null;
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

        const sectionRoot = () => {
            const first = Array.from(document.querySelectorAll("a[href]"))
                .find((a) => {
                    const c = parseChapterPath(pathOf(a.getAttribute("href") || ""));
                    return c && (!hid || c.hid === hid);
                });
            if (!first) return document.body;
            const rendered = Array.from(document.querySelectorAll("a[href]"))
                .filter((a) => {
                    const c = parseChapterPath(pathOf(a.getAttribute("href") || ""));
                    return c && (!hid || c.hid === hid);
                }).length;
            let root = first;
            for (let depth = 0; depth < 12 && root.parentElement; depth++) {
                const parent = root.parentElement;
                const inside = Array.from(parent.querySelectorAll("a[href]"))
                    .filter((a) => {
                        const c = parseChapterPath(pathOf(a.getAttribute("href") || ""));
                        return c && (!hid || c.hid === hid);
                    }).length;
                if (inside >= rendered) root = parent;
                else break;
            }
            return root;
        };

        const activate = async (el, entry) => {
            const r0 = el.getBoundingClientRect();
            const inView = r0.width > 0 && r0.top >= 0 && r0.bottom <= window.innerHeight &&
                r0.left >= 0 && r0.right <= window.innerWidth;
            if (!inView) {
                try { el.scrollIntoView({ block: "center" }); } catch (e) {}
                await sleep(350);
            } else {
                await sleep(60);
            }
            let tapped = false;
            if (typeof window.__comixNativeTap === "function") {
                const r = el.getBoundingClientRect();
                if (entry) {
                    entry.rect = [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)];
                    const at = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
                    entry.atPoint = describe(at);
                }
                if (r.width > 0 && r.height > 0) {
                    try {
                        window.__comixNativeTap(r.left + r.width / 2, r.top + r.height / 2);
                        tapped = true;
                    } catch (e) {}
                }
            }
            if (!tapped) {
                try { el.click(); } catch (e) {}
            }
            return tapped;
        };

        const diag = [];
        let rounds = 0;
        let complete = false;
        let stoppedAtKnown = reachedKnown();
        while (!stoppedAtKnown && rounds < OPTIONS.maxRounds) {
            let control = findMoreControl(sectionRoot());
            if (!control) control = findMoreControl(document);
            if (!control) {
                complete = byId.size > 0;
                break;
            }
            const before = byId.size;
            rounds++;
            const entry = {
                round: rounds,
                control: describe(control),
                before,
                tapBridge: typeof window.__comixNativeTap === "function",
                total: reportedTotal(),
            };
            const usedTap = await activate(control, entry);
            entry.method = usedTap ? "tap" : "click";
            let grew = await waitFor(() => (absorb(), byId.size > before) || null,
                { timeoutMs: OPTIONS.settleMs, intervalMs: OPTIONS.pollMs });
            if (!grew && !reachedKnown()) {
                await activate(control, null);
                grew = await waitFor(() => (absorb(), byId.size > before) || null,
                    { timeoutMs: OPTIONS.settleMs, intervalMs: OPTIONS.pollMs });
            }
            if (!grew && !reachedKnown()) {
                try { control.click(); } catch (e) {}
                grew = await waitFor(() => (absorb(), byId.size > before) || null,
                    { timeoutMs: OPTIONS.settleMs, intervalMs: OPTIONS.pollMs });
            }
            entry.after = byId.size;
            entry.url = location.href;
            diag.push(entry);
            if (reachedKnown()) {
                stoppedAtKnown = true;
                break;
            }
            if (!grew) {
                complete = byId.size > 0;
                break;
            }
        }

        const total = reportedTotal();
        if (total != null && byId.size < total && !stoppedAtKnown) complete = false;
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
            reportedTotal: total,
            diag: diag.slice(0, 12),
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
