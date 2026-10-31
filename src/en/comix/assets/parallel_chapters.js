(() => {
    "use strict";

    const LIMIT = 100;
    const WAVE = 40;
    const MAX_PAGES = 1000;
    const RETRY_KEY = "__comixA1Retried";
    const CHAPTERS_RE = /\/api\/v1\/manga\/[^/]+\/chapters/;
    const sleep = ms => new Promise(r => setTimeout(r, ms));
    const stopAt = new Set(((window.__comixHarvestOptions || {}).stopAtIds || []).map(Number));

    let lastTrusted = 0;
    for (const type of ["click", "keydown"]) {
        document.addEventListener(type, e => {
            if (e.isTrusted) lastTrusted = performance.now();
        }, { capture: true, passive: true });
    }

    let flagged = 0;
    try {
        performance.setResourceTimingBufferSize(10000);
        new PerformanceObserver(list => {
            for (const e of list.getEntries()) {
                if (!CHAPTERS_RE.test(e.name)) continue;
                try {
                    const q = new URL(e.name).searchParams;
                    if ((Number(q.get("v")) & 1) || /[^0]/.test(q.get("w") || "0")) flagged++;
                } catch (err) {}
            }
        }).observe({ type: "resource" });
    } catch (e) {}

    const retryOnce = () => {
        try {
            if (sessionStorage.getItem(RETRY_KEY)) return false;
            sessionStorage.setItem(RETRY_KEY, "1");
        } catch (e) {
            return false;
        }
        location.reload();
        return true;
    };

    const inert = el => !el || !el.closest(
        "a,button,input,select,textarea,label,summary,iframe,[role=button],[role=link],[onclick],[contenteditable]");
    const pickPoint = () => {
        const spots = [[0.5, 0.5], [0.06, 0.5], [0.94, 0.5], [0.5, 0.94], [0.06, 0.94], [0.94, 0.94], [0.5, 0.06]];
        for (const [fx, fy] of spots) {
            const x = Math.round(innerWidth * fx);
            const y = Math.round(innerHeight * fy);
            if (inert(document.elementFromPoint(x, y))) return [x, y];
        }
        return null;
    };

    const tap = async () => {
        if (typeof window.__comixNativeTap !== "function") return false;
        for (let attempt = 0; attempt < 3; attempt++) {
            const point = pickPoint();
            if (!point) return false;
            const asked = performance.now();
            window.__comixNativeTap(point[0], point[1]);
            while (performance.now() - asked < 1500) {
                if (lastTrusted >= asked) return true;
                await sleep(25);
            }
        }
        return false;
    };

    const waitForModule = async () => {
        const re = /\/apicore-[^/?#]+\.js(?:[?#]|$)/;
        const deadline = Date.now() + 15000;
        while (Date.now() < deadline) {
            const url = performance.getEntriesByType("resource").map(e => e.name).find(n => re.test(n));
            if (url) return url;
            await sleep(100);
        }
        throw new Error("apicore module not loaded");
    };

    const findApi = async (url) => {
        const [mod, src] = await Promise.all([import(url), fetch(url).then(r => r.text())]);
        const m = /async"([\w$]+)"\(\w+,\w+=\{\}\)\{const [\w$]+=[\w$]+,[\w$]+=[\w$]+\.([\w$]+)\.\1\(\w+\);/.exec(src);
        if (!m) throw new Error("chapter method not found");
        for (const v of Object.values(mod)) {
            try { if (v && typeof v[m[1]] === "function") return { api: v, key: m[1] }; } catch (e) {}
        }
        throw new Error("api export not found");
    };

    const fromUrl = url => {
        const m = /\/(\d+)-chapter-([\d.]+)/.exec(url || "");
        return m ? { id: parseInt(m[1], 10), number: parseFloat(m[2]) } : null;
    };

    const urlOf = ch => (typeof ch.yr === "string" && ch.yr) ||
        Object.values(ch).find(v => typeof v === "string" && /\/title\/.+-chapter-/.test(v));

    const checkFields = ch => {
        if (!ch) return;
        const parsed = fromUrl(urlOf(ch));
        const ok = parsed && typeof ch.yr === "string" && ch.fq === parsed.id && typeof ch.Vn === "number" && "OC" in ch;
        if (!ok) throw new Error("chapter fields changed: " + Object.keys(ch).join(","));
    };

    const mapChapter = (ch) => {
        if (!ch || typeof ch !== "object") return null;
        const url = urlOf(ch);
        const parsed = fromUrl(url);
        if (!parsed) return null;
        return {
            id: Number.isInteger(ch.fq) ? ch.fq : parsed.id,
            url,
            number: typeof ch.Vn === "number" ? ch.Vn : parsed.number,
            name: typeof ch.OC === "string" ? ch.OC : "",
            votes: typeof ch.Fl === "number" ? ch.Fl : 0,
            createdAtFormatted: (typeof ch.Zi === "string" && ch.Zi) ||
                Object.values(ch).find(v => typeof v === "string" && /\d+\s*\w+\s+ago/i.test(v)) || "",
            group: ch.Ck && typeof ch.Ck === "object"
                ? { id: Object.values(ch.Ck).find(v => Number.isInteger(v)) ?? null,
                    name: Object.values(ch.Ck).find(v => typeof v === "string") || "" }
                : null,
            isOfficial: ch.Za === true,
        };
    };

    const items = r => Object.values(r || {}).find(Array.isArray) || [];

    const total = r => {
        for (const v of Object.values(r || {})) {
            if (v && typeof v === "object" && !Array.isArray(v)) {
                const nums = Object.values(v).filter(n => typeof n === "number");
                if (nums.length >= 3) return Math.max(...nums);
            }
        }
        return 0;
    };

    const run = async () => {
        const { api, key } = await findApi(await waitForModule());
        const hid = (/^\/title\/([a-z0-9]+)-/i.exec(location.pathname) || [])[1];
        if (!hid) throw new Error("no hid");
        const onTitle = () => location.pathname.startsWith("/title/" + hid);
        const order = { number: "desc" };
        const get = page => api[key](hid, { page, limit: LIMIT, order });

        const r1 = await get(1);
        const rows1 = items(r1);
        checkFields(rows1[0]);
        const first = rows1.map(mapChapter).filter(Boolean);
        if (stopAt.size > 0 && first.some(ch => stopAt.has(ch.id))) return { chapters: first, complete: false };

        const expected = rows1.length < LIMIT ? rows1.length : Math.max(total(r1), rows1.length);
        const needed = Math.max(1, Math.ceil(expected / LIMIT));
        const pages = Math.min(needed, MAX_PAGES);
        const byPage = new Map([[1, rows1]]);
        let failed = 0;
        let stopped = false;

        for (let page = 2; page <= pages;) {
            if (!onTitle()) { stopped = true; break; }
            if (!(await tap())) {
                if (byPage.size === 1) throw new Error("no trusted tap");
                stopped = true;
                break;
            }
            const end = Math.min(page + WAVE, pages + 1);
            const wave = [];
            for (; page < end; page++) {
                const p = page;
                wave.push(get(p).then(r => { byPage.set(p, items(r)); }, () => { failed++; }));
            }
            await Promise.all(wave);
            await sleep(250);
            if (flagged > 0) {
                if (retryOnce()) return new Promise(() => {});
                stopped = true;
                break;
            }
        }

        const chapters = [];
        const ids = new Set();
        let short = false;
        let unmapped = 0;
        for (let p = 1; p <= pages; p++) {
            const rows = byPage.get(p);
            if (!rows) { short = true; continue; }
            if (p < pages && rows.length < LIMIT) short = true;
            for (const raw of rows) {
                const ch = mapChapter(raw);
                if (!ch) { unmapped++; continue; }
                if (ids.has(ch.id)) continue;
                ids.add(ch.id);
                chapters.push(ch);
            }
        }

        return {
            chapters,
            complete: !stopped && !failed && !short && !unmapped && pages === needed && ids.size >= expected,
        };
    };

    window.__comixA1ParallelPromise = run().catch(e => ({
        chapters: [], complete: false, error: String((e && e.message) || e),
    }));
})();
