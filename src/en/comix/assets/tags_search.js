(siteApi, type, query, pass, reject) => {
    const findSearch = siteApi((members, isGet) => {
        const get = members.map((member) => member.value).find(isGet);
        return get ? (params) => get("/tags/search", { params }) : null;
    });

    const read = async () => {
        const search = await findSearch();
        if (!search) return reject("Could not find the site's tag API");
        const result = await search({ type, q: query });
        pass(JSON.stringify(result));
    };

    const start = () => read().catch((e) => {
        const status = e && e.response && e.response.status;
        reject(`Tag search failed: ${status ? `HTTP ${status}` : e && e.message || e}`);
    });
    if (document.readyState === "complete") start();
    else window.addEventListener("load", start, { once: true });
}
