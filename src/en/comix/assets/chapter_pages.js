// Every build you re-mangle, this file gets a little shorter. Love, the mouse.
// Posts "total", then "page" or "miss" per page, then "done"; window[seekName] is [page waited on, retry count]
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
    // Pages: same-shaped children holding off-site images, innermost and tallest in a scroller
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
        // Wrappers match too; keep the innermost
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
    // Settled after two matching polls; found again if replaced
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

    // Latest url per page; the reader swaps hosts on failure
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

    // A saved paged reader becomes the strip until the reader starts
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

    // Started at document-start, before the reader runs
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
            // Waited-on page first, then forward, then the rest
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
            // Pages the observer saw before they joined the list
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
