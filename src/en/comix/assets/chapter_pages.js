// Reader page urls, read as the reader loads each page and posted one message at a time: "total" once the
// page count is known, "page" per page, "miss" for a page that didn't load, then "done".
// window[seekName] holds [page waited on, retry count]; a new retry count gives a missed page another try.
(pass, reject, seekName, knownIndices) => {
    const startedAt = Date.now();
    const FIRST_PAGE_TIMEOUT_MS = 30000;
    const WALK_TIMEOUT_MS = 220000;
    const SLOT_TIMEOUT_MS = 15000;
    const SETTLE_MS = 100;
    const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

    let container = null;
    let slots = [];
    let total = 0;
    const shapeOf = (element) => element.tagName + "." + (element.getAttribute("class") || "");
    // The pages are the run of same-shaped children holding an image from another site, in the innermost,
    // tallest element inside a scrolling box; when the reader shows a "<current> / <total>" counter, the run
    // must be that long. Comments and posters come from the site itself.
    const site = location.hostname.replace(/^www\./, "");
    const isForeignImage = (img) => {
        try {
            const host = new URL(img.getAttribute("src") || "", location.href).hostname;
            return host !== site && !host.endsWith("." + site);
        } catch (e) {
            return false;
        }
    };
    const scrolls = (element) => element.scrollHeight > element.clientHeight &&
        (element === document.scrollingElement || /(auto|scroll)/.test(getComputedStyle(element).overflowY));
    const insideScroller = (element) => {
        for (let parent = element.parentElement; parent; parent = parent.parentElement) if (scrolls(parent)) return true;
        return false;
    };
    const findReader = () => {
        const totals = new Set();
        for (const element of document.body.querySelectorAll("*")) {
            if (element.firstElementChild) continue;
            const match = /^\s*\d+\s*\/\s*(\d+)\s*$/.exec(element.textContent || "");
            if (match) totals.add(parseInt(match[1], 10));
        }
        const matches = [];
        for (const element of document.body.querySelectorAll("*")) {
            const withImage = Array.prototype.find.call(element.children,
                (child) => Array.prototype.some.call(child.querySelectorAll("img"), isForeignImage));
            if (!withImage) continue;
            const shape = shapeOf(withImage);
            const count = Array.prototype.filter.call(element.children, (child) => shapeOf(child) === shape).length;
            if (totals.size && !totals.has(count)) continue;
            if (insideScroller(element)) matches.push({ element, shape });
        }
        // Wrappers around the pages match too, so only the innermost matches count
        let best = null;
        for (const match of matches) {
            if (matches.some((other) => other !== match && match.element.contains(other.element))) continue;
            if (!best || match.element.offsetHeight > best.element.offsetHeight) best = match;
        }
        return best && {
            element: best.element,
            slots: Array.prototype.filter.call(best.element.children, (child) => shapeOf(child) === best.shape),
        };
    };
    // Taken once the same reader is found twice in a row, and looked up again if the page replaces it
    let candidate = null;
    const attachReader = () => {
        if (container && container.isConnected) return true;
        const reader = document.body && findReader();
        const settled = reader && candidate && reader.element === candidate.element &&
            reader.slots.length === candidate.slots.length;
        candidate = reader;
        if (!settled) return false;
        ({ element: container, slots } = reader);
        total = slots.length;
        container.querySelectorAll("img[src]").forEach(record);
        return true;
    };
    const slotIndex = (img) => {
        let slot = img;
        while (slot.parentElement && slot.parentElement !== container) slot = slot.parentElement;
        return slots.indexOf(slot);
    };

    // Each page's latest url: the reader may move a page to another host after a failed load
    const captured = new Map(knownIndices.map((index) => [index, null]));
    const missed = new Set();
    const record = (img) => {
        if (!container || !attachReader()) return;
        let url;
        try { url = new URL(img.getAttribute("src") || "", location.href); } catch (e) { return; }
        if (!/^https?:$/.test(url.protocol)) return;
        const index = slotIndex(img);
        if (index < 0 || captured.get(index) === url.href) return;
        captured.set(index, url.href);
        missed.delete(index);
        pass(JSON.stringify({ type: "page", index, url: url.href }));
    };

    // Only the long-strip reader lays out every page, so a saved paged reader is switched to it for this run,
    // and put back once the reader has started (it reads the setting only then)
    const savedReader = {};
    for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        try {
            const raw = localStorage.getItem(key);
            const stored = JSON.parse(raw);
            if (stored && stored.state && "readingDirection" in stored.state && stored.state.readingDirection !== "ttb") {
                savedReader[key] = raw;
                stored.state.readingDirection = "ttb";
                localStorage.setItem(key, JSON.stringify(stored));
            }
        } catch (e) {}
    }
    const restoreReader = () => {
        Object.entries(savedReader).forEach(([key, raw]) => localStorage.setItem(key, raw));
        Object.keys(savedReader).forEach((key) => delete savedReader[key]);
    };

    // Watches image urls as the reader sets them; started at document-start, before the reader runs
    const recordImage = (node) => {
        if (node.tagName === "IMG") record(node);
        else if (node.querySelectorAll) node.querySelectorAll("img[src]").forEach(record);
    };
    new MutationObserver((mutations) => {
        for (const mutation of mutations) {
            if (mutation.type === "attributes") recordImage(mutation.target);
            else mutation.addedNodes.forEach(recordImage);
        }
    }).observe(document, { subtree: true, childList: true, attributes: true, attributeFilter: ["src"] });

    const waitForReader = async () => {
        while (Date.now() < startedAt + FIRST_PAGE_TIMEOUT_MS) {
            if (attachReader()) return;
            await sleep(200);
        }
        throw new Error("The reader didn't load any pages");
    };

    const waited = new Map();
    let retries = 0;
    const readSeek = () => {
        const value = window[seekName];
        if (!Array.isArray(value)) return -1;
        const [seek, retry] = value;
        if (retry !== retries) {
            retries = retry;
            if (missed.delete(seek)) waited.delete(seek);
        }
        return seek;
    };

    const walk = async () => {
        await waitForReader();
        restoreReader();
        pass(JSON.stringify({ type: "total", total }));
        const done = (i) => captured.has(i) || missed.has(i);
        while (Date.now() < startedAt + WALK_TIMEOUT_MS) {
            // The page being waited on first, then forward from it, then anything left behind it
            const seek = readSeek();
            let target = seek >= 0 && seek < total && !done(seek) ? seek : -1;
            for (let i = Math.max(seek, 0); target < 0 && i < total; i++) if (!done(i)) target = i;
            for (let i = 0; target < 0 && i < total; i++) if (!done(i)) target = i;
            if (target < 0) break;

            const time = (waited.get(target) || 0) + SETTLE_MS;
            waited.set(target, time);
            if (time > SLOT_TIMEOUT_MS) {
                missed.add(target);
                pass(JSON.stringify({ type: "miss", index: target }));
                continue;
            }
            // Images the observer saw before their page took its place in the list
            if (attachReader()) {
                container.querySelectorAll("img[src]").forEach(record);
                slots[target].scrollIntoView({ block: "center" });
            }
            await sleep(SETTLE_MS);
        }
        pass(JSON.stringify({ type: "done" }));
    };

    walk().catch((e) => {
        restoreReader();
        reject(e && e.message || String(e));
    });
}
