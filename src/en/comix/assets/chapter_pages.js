(siteApi, chapterId, pass, reject) => {
    const findChapter = siteApi((members, isGet) => {
        const route = members.map((member) => member.value).find((value) => typeof value === "function" &&
            /^\(?[\w$]+\)?=>`\/chapters\/\$\{[\w$]+\}`$/.test(String(value)));
        const get = members.map((member) => member.value).find(isGet);
        return route && get ? (id, params) => get(route(id), { params }) : null;
    });
    const isPages = (value) => value &&
        Array.isArray(value.items) &&
        value.items.length > 0 &&
        value.items.every((item) => item && typeof item.url === "string");

    const read = async () => {
        const chapter = await findChapter();
        if (!chapter) return reject("Could not find the site's chapter API");
        const result = await chapter(chapterId);
        // The site's reader takes the pages either as an object or as a bare array
        const pages = [result, ...Object.values(result || {})]
            .map((value) => Array.isArray(value) ? { items: value } : value)
            .find(isPages);
        if (!pages) return reject("Chapter pages came back in a shape the extension doesn't recognise");
        pass(JSON.stringify(pages));
    };

    const start = () => read().catch((e) => {
        const status = e && e.response && e.response.status;
        reject(`Chapter pages failed: ${status ? `HTTP ${status}` : e && e.message || e}`);
    });
    if (document.readyState === "complete") start();
    else window.addEventListener("load", start, { once: true });
}
