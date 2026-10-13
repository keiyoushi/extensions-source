// Chapter list through the site's own `?page=` state, one page at a time
(latestChapterId, chapterPath, maxPages, pageDelayMs, pass, reject) => {
    const chapters = [];
    const isChapter = (item) => item &&
        typeof item.id === "number" &&
        typeof item.number === "number" &&
        typeof item.url === "string" &&
        item.url.includes(chapterPath);
    const PAGER_FALLBACK_MS = 3000;
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
        const target = page;
        // Site's pager, for when it ignores the popstate
        const clickPage = (tries) => {
            if (done || page !== target) return;
            const button = [...document.querySelectorAll(".mpage__chapters nav.npager button.npager__num")]
                .find((b) => b.textContent.trim() === String(target));
            if (button) {
                button.click();
            } else if (tries > 0) {
                setTimeout(() => clickPage(tries - 1), 200);
            } else {
                done = true;
                reject(`Chapter list pager has no page ${target}`);
            }
        };
        setTimeout(() => {
            const url = new URL(location.href);
            url.searchParams.set("page", String(target));
            history.pushState(history.state, "", url);
            dispatchEvent(new PopStateEvent("popstate", { state: history.state }));
            setTimeout(() => clickPage(25), PAGER_FALLBACK_MS);
        }, pageDelayMs);
    };
}
