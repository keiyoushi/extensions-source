// Chapter list through the site's own `?page=` state, one page at a time
(latestChapterId, chapterPath, maxPages, pageDelayMs, pass, reject) => {
    const chapters = [];
    const isChapter = (item) => item &&
        typeof item.id === "number" &&
        typeof item.number === "number" &&
        typeof item.url === "string" &&
        item.url.includes(chapterPath);
    let page = 1;
    let done = false;

    return (holder) => {
        const items = holder.items;
        const meta = holder.meta;
        if (done || !meta || !Array.isArray(items) || !items.every(isChapter)) return;
        if (items.length === 0 && meta.total !== 0) return;
        if (typeof meta.page !== "number" || typeof meta.lastPage !== "number") {
            done = true;
            reject("Chapter list has no page numbers");
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
        setTimeout(() => {
            const url = new URL(location.href);
            url.searchParams.set("page", String(page));
            history.pushState(history.state, "", url);
            dispatchEvent(new PopStateEvent("popstate", { state: history.state }));
        }, pageDelayMs);
    };
}
