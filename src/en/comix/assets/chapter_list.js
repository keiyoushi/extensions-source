// Chapter list through the site's own chapters API method, newest uploads first, up to a page with a known chapter;
// pages past a failure are left out
(knownIds, mangaId, chapterPath, maxPages, pass, reject) => {
    const known = new Set(knownIds);
    const chapters = [];
    const isChapter = (item) => item &&
        typeof item.id === "number" &&
        typeof item.number === "number" &&
        typeof item.url === "string" &&
        item.url.includes(chapterPath);
    const isRoute = (route) => typeof route === "function" &&
        /^\(?[\w$]+\)?=>`\/manga\/\$\{[\w$]+\}\/chapters`$/.test(String(route));
    const PAGE_TIMEOUT_MS = 25000;
    let page = 1;
    // The site's own first load is by chapter number, so it is skipped and the list read again by upload date
    let siteLoaded = false;
    let expecting = false;
    // Numbers in the page our own call returned (the site's mapped copy), so a page the site loads itself isn't taken for it
    let returnedNumbers = null;
    let done = false;
    let chaptersApi = null;

    const finish = (complete, error) => {
        done = true;
        pass(JSON.stringify({ chapters, complete, error }));
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
        let error = `Chapter page ${target} never arrived`;
        try {
            chaptersApi = chaptersApi || await findChaptersApi();
            if (!chaptersApi) {
                finish(false, "Could not find the site's chapters API");
                return;
            }
            expecting = true;
            const result = await Promise.race([
                chaptersApi({ page: target, limit: 20, order: { created_at: "desc" } }),
                new Promise((resolve, timeout) => setTimeout(() => timeout(new Error("timed out")), PAGE_TIMEOUT_MS)),
            ]);
            const arrays = Array.isArray(result) ? [result] : Object.values(result || {}).filter(Array.isArray);
            returnedNumbers = arrays.map((returned) => ({
                count: returned.length,
                values: new Set(returned.flatMap((item) => Object.values(item || {}).filter((value) => typeof value === "number"))),
            }));
            error = `Chapter page ${target} came back in a shape the extension doesn't recognise`;
        } catch (e) {
            const status = e && e.response && e.response.status;
            error = status === 502
                ? `Comix blocked chapter page ${target} (HTTP 502). Try again later, or change the user agent in Advanced settings`
                : `Chapter page ${target} failed: ${status ? `HTTP ${status}` : e && e.message || e}`;
        }
        // Captured pages are handed over on a 0 ms timer, before this one
        setTimeout(() => {
            if (!done && page === target) finish(false, error);
        }, 1000);
    };

    return (holder) => {
        const items = holder.items;
        const meta = holder.meta;
        if (done || !meta || !Array.isArray(items) || !items.every(isChapter)) return;
        if (items.length === 0 && meta.total !== 0) return;
        if (typeof meta.page !== "number" || typeof meta.lastPage !== "number") {
            if (chapters.length > 0) {
                finish(false, "Chapter list has no page numbers");
            } else {
                done = true;
                reject("Chapter list has no page numbers");
            }
            return;
        }
        if (!siteLoaded) {
            siteLoaded = true;
            requestPage(page);
            return;
        }
        if (!expecting || meta.page !== page) return;
        if (!(returnedNumbers && returnedNumbers.some((returned) => returned.count === items.length &&
            items.every((item) => returned.values.has(item.id))))) return;
        expecting = false;
        returnedNumbers = null;
        chapters.push(...items);
        if (items.some((item) => known.has(item.id))) {
            finish(true);
            return;
        }
        if (!meta.hasNext || page >= Math.min(meta.lastPage, maxPages)) {
            const count = new Set(chapters.map((item) => item.id)).size;
            finish(count === meta.total, `Got ${count} of ${meta.total} chapters`);
            return;
        }
        page++;
        requestPage(page);
    };
}
