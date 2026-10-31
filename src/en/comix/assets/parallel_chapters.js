(() => {
    "use strict";

    const LIMIT = 100;
    const TAP_EVERY = 3;
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

    try { performance.setResourceTimingBufferSize(10000); } catch (e) {}
    const flaggedRequests = () => performance.getEntriesByType("resource").filter(e => {
        if (!CHAPTERS_RE.test(e.name)) return false;
        try {
            const q = new URL(e.name).searchParams;
            return (Number(q.get("v")) & 1) || /[^0]/.test(q.get("w") || "0");
        } catch (err) {
            return false;
        }
    }).length;

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
        const mod = await import(url);
        const keys = {};
        for (const exported of Object.values(mod)) {
            if (!exported || typeof exported !== "object") continue;
            for (const group of Object.values(exported)) {
                if (!group || typeof group !== "object" || Array.isArray(group)) continue;
                for (const [name, fn] of Object.entries(group)) {
                    if (typeof fn !== "function") continue;
                    try {
                        const queryKey = fn("H", {});
                        if (!Array.isArray(queryKey) || queryKey[0] !== "manga") continue;
                        if (queryKey[1] === "chapters" && queryKey[2] === "H") keys.chapters = name;
                        if (queryKey[1] === "recommended" && queryKey[2] === "H") keys.recommended = name;
                    } catch (e) {}
                }
            }
        }
        if (!keys.chapters || !keys.recommended) throw new Error("chapter method not found");
        const apis = Object.values(mod).filter(v => {
            try { return v && typeof v[keys.chapters] === "function" && typeof v[keys.recommended] === "function"; } catch (e) { return false; }
        });
        if (apis.length !== 1) throw new Error("api export not found");
        return { api: apis[0], key: keys.chapters };
    };

    const fromUrl = url => {
        const m = /\/(\d+)-chapter-([\d.]+)/.exec(url || "");
        return m ? { id: parseInt(m[1], 10), number: parseFloat(m[2]) } : null;
    };

    const FIELDS = 13;
    const [ID, NUMBER, NAME, OFFICIAL, VOTES, DATE, GROUP, LINK] = [0, 2, 4, 6, 7, 8, 10, 12];

    const checkFields = ch => {
        if (!ch) return;
        const row = Object.values(ch);
        const parsed = fromUrl(row[LINK]);
        const ok = row.length === FIELDS && parsed && row[ID] === parsed.id && row[NUMBER] === parsed.number &&
            (row[NAME] === null || typeof row[NAME] === "string") && typeof row[OFFICIAL] === "boolean";
        if (!ok) throw new Error("chapter fields changed");
    };

    const mapChapter = (ch) => {
        if (!ch || typeof ch !== "object") return null;
        const row = Object.values(ch);
        const parsed = fromUrl(row[LINK]);
        if (!parsed) return null;
        const group = row[GROUP] && Object.values(row[GROUP]);
        return {
            id: Number.isInteger(row[ID]) ? row[ID] : parsed.id,
            url: row[LINK],
            number: typeof row[NUMBER] === "number" ? row[NUMBER] : parsed.number,
            name: typeof row[NAME] === "string" ? row[NAME] : "",
            votes: typeof row[VOTES] === "number" ? row[VOTES] : 0,
            createdAtFormatted: typeof row[DATE] === "string" ? row[DATE] : "",
            group: group ? { id: Number.isInteger(group[0]) ? group[0] : null, name: group[1] || "" } : null,
            isOfficial: row[OFFICIAL] === true,
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

        for (let page = 2; page <= pages; page++) {
            if (!onTitle()) { stopped = true; break; }
            if ((page - 2) % TAP_EVERY === 0 && !(await tap())) {
                if (byPage.size === 1) throw new Error("no trusted tap");
                stopped = true;
                break;
            }
            await get(page).then(r => { byPage.set(page, items(r)); }, () => { failed++; });
            if (flaggedRequests() > 0) {
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
