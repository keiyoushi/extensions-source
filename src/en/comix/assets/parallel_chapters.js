(() => {
    "use strict";

    const LIMIT = 100;
    const sleep = ms => new Promise(r => setTimeout(r, ms));
    const stopAt = new Set(((window.__comixHarvestOptions || {}).stopAtIds || []).map(Number));

    const waitForModule = async () => {
        const re = /\/apicore-[^/?#]+\.js(?:[?#]|$)/;
        const deadline = Date.now() + 15000;
        while (Date.now() < deadline) {
            const url = performance.getEntriesByType("resource")
                .map(e => e.name).find(n => re.test(n));
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

    const mapChapter = (ch) => {
        const url = ch.yr || Object.values(ch).find(v => typeof v === "string" && /\/title\/.*-chapter-/.test(v));
        if (!url) return null;
        const m = /\/(\d+)-chapter-([\d.]+)/.exec(url);
        return {
            id: ch.fq ?? (m ? parseInt(m[1]) : 0),
            url,
            number: ch.Vn ?? (m ? parseFloat(m[2]) : 0),
            name: ch.OC || "",
            votes: ch.Fl || 0,
            createdAtFormatted: ch.Zi || Object.values(ch).find(v => typeof v === "string" && /\d+\s*\w+\s+ago/i.test(v)) || "",
            group: ch.Ck && typeof ch.Ck === "object"
                ? { id: Object.values(ch.Ck).find(v => typeof v === "number" && Number.isInteger(v)) ?? null,
                    name: Object.values(ch.Ck).find(v => typeof v === "string") || "" }
                : null,
            isOfficial: ch.Za || false,
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

        if (typeof window.__comixNativeTap === "function") {
            window.__comixNativeTap(50, 50);
            await sleep(300);
        }

        const order = { number: "desc" };
        const r1 = await api[key](hid, { page: 1, limit: LIMIT, order });
        const first = items(r1).map(mapChapter).filter(Boolean);
        const pages = Math.min(Math.max(1, Math.ceil(total(r1) / LIMIT)), 50);

        if (stopAt.size > 0 && first.some(ch => stopAt.has(ch.id)))
            return { chapters: first, complete: false };

        if (pages <= 1)
            return { chapters: first, complete: true };

        let allOk = true;
        const rest = await Promise.all(
            Array.from({ length: pages - 1 }, (_, i) =>
                api[key](hid, { page: i + 2, limit: LIMIT, order })
                    .then(items).catch(() => { allOk = false; return []; })
            )
        );

        return {
            chapters: [...first, ...rest.flat().map(mapChapter).filter(Boolean)],
            complete: allOk,
        };
    };

    window.__comixA1ParallelPromise = run().catch(e => ({
        chapters: [], complete: false, error: String(e && e.message || e),
    }));
})();
