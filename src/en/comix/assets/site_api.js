// The site's own API client: a route from its route table and its GET helper, which resolves to the response body,
// from the chunks next to the running entry
(isRoute) => async () => {
    const FIND_TIMEOUT_MS = 10000;
    const isGet = (value) => typeof value === "function" &&
        String(value).includes(".get(") &&
        String(value).endsWith(".data");
    const find = async () => {
        const scripts = performance.getEntriesByType("resource")
            .map((entry) => entry.name)
            .filter((url) => /\/assets\/.*\.js$/.test(url));
        const entry = scripts.find((url) => /\/main-[^/]+\.js$/.test(url));
        if (!entry) return null;
        const directory = entry.slice(0, entry.lastIndexOf("/") + 1);
        const members = [];
        for (const url of new Set(scripts.filter((url) => url.startsWith(directory)))) {
            for (const value of Object.values(await import(url))) {
                if (value && typeof value === "object") members.push(...Object.values(value));
            }
            const route = members
                .filter((group) => group && typeof group === "object")
                .flatMap((group) => Object.values(group))
                .find(isRoute);
            const get = members.find(isGet);
            if (route && get) return (id, params) => get(route(id), { params });
        }
        return null;
    };
    // The site's chunks keep loading after the page's load event
    const deadline = Date.now() + FIND_TIMEOUT_MS;
    for (;;) {
        const api = await find();
        if (api || Date.now() >= deadline) return api;
        await new Promise((resolve) => setTimeout(resolve, 250));
    }
}
