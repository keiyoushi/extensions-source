// Chapter list through the site's own chapters API method, one page at a time
(latestChapterId, mangaId, chapterPath, maxPages, pass, reject) => {
    const chapters = [];
    const isChapter = (item) => item &&
        typeof item.id === "number" &&
        typeof item.number === "number" &&
        typeof item.url === "string" &&
        item.url.includes(chapterPath);
    const isRoute = (route) => typeof route === "function" &&
        /^\(?[\w$]+\)?=>`\/manga\/\$\{[\w$]+\}\/chapters`$/.test(String(route));
    let page = 1;
    let done = false;
    let chaptersApi = null;

    const fail = (message) => {
        done = true;
        reject(message);
    };

    const matchChaptersApi = (exports) => {
        for (const routes of exports.flatMap((value) => value && typeof value === "object" ? Object.values(value) : [])) {
            if (!routes || typeof routes !== "object") continue;
            const routeKeys = Object.keys(routes);
            const key = routeKeys.find((name) => isRoute(routes[name]));
            if (!key) continue;
            const api = exports.find((value) => value && typeof value === "object" && value !== routes &&
                typeof value[key] === "function" &&
                routeKeys.every((name) => name in value) &&
                String(value[key]).includes("params"));
            if (api) return (params) => api[key](mangaId, params);
        }
        return null;
    };

    // Site's manga API object, matched to the route table by its keys, from the chunks next to the running entry
    const findChaptersApi = async () => {
        const scripts = performance.getEntriesByType("resource")
            .map((entry) => entry.name)
            .filter((url) => /\/assets\/.*\.js$/.test(url));
        const entry = scripts.find((url) => /\/main-[^/]+\.js$/.test(url));
        if (!entry) return null;
        const directory = entry.slice(0, entry.lastIndexOf("/") + 1);
        const exports = [];
        for (const url of new Set(scripts.filter((url) => url.startsWith(directory)))) {
            exports.push(...Object.values(await import(url)));
            const api = matchChaptersApi(exports);
            if (api) return api;
        }
        return null;
    };

    const requestPage = async (target) => {
        try {
            chaptersApi = chaptersApi || await findChaptersApi();
        } catch (e) {
            return fail(`Could not find the site's chapters API: ${e && e.message || e}`);
        }
        if (!chaptersApi) return fail("Could not find the site's chapters API");
        try {
            await chaptersApi({ page: target, limit: 20, order: { number: "desc" } });
        } catch (e) {
            if (done || page !== target) return;
            const status = e && e.response && e.response.status;
            fail(status === 502
                ? `Comix blocked chapter page ${target} (HTTP 502). Try again later, or change the user agent in Advanced settings`
                : `Chapter page ${target} failed: ${status ? `HTTP ${status}` : e && e.message || e}`);
        }
    };

    return (holder) => {
        const items = holder.items;
        const meta = holder.meta;
        if (done || !meta || !Array.isArray(items) || !items.every(isChapter)) return;
        if (items.length === 0 && meta.total !== 0) return;
        if (typeof meta.page !== "number" || typeof meta.lastPage !== "number") {
            fail("Chapter list has no page numbers");
            return;
        }
        if (meta.page !== page) return;
        chapters.push(...items);
        if (items.some((item) => item.id === latestChapterId)) {
            done = true;
            pass(JSON.stringify(chapters));
            return;
        }
        if (!meta.hasNext || page >= Math.min(meta.lastPage, maxPages)) {
            done = true;
            const count = new Set(chapters.map((item) => item.id)).size;
            if (count === meta.total) {
                pass(JSON.stringify(chapters));
            } else {
                reject(`Got ${count} of ${meta.total} chapters`);
            }
            return;
        }
        page++;
        requestPage(page);
    };
}
