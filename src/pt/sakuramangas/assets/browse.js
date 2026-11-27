// The catalog grid of /obras/. With a query, it is typed into the site's search field first.
((done, tap, options, type) => {
    "use strict";

    const OPT = Object.assign({ timeoutMs: 40000, pollMs: 300, query: null }, options || {});
    const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
    const text = (el) => (el && el.textContent ? el.textContent : "").replace(/\s+/g, " ").trim();
    const abs = (src) => (src ? new URL(src, location.href).href : null);

    // A card links to /obras/<slug> with no chapter number
    const mangaRe = /^\/obras\/[^/?#]+\/?$/;

    // The cover sits in the card around the link
    const coverOf = (anchor) => {
        for (let el = anchor, depth = 0; el && depth < 5; el = el.parentElement, depth++) {
            const img = el.querySelector("img");
            if (img) return abs(img.getAttribute("src") || img.getAttribute("data-src"));
        }
        return null;
    };

    const readCards = () => {
        const byUrl = new Map();
        for (const anchor of document.querySelectorAll('a[href*="/obras/"]')) {
            const path = (anchor.getAttribute("href") || "").split(/[?#]/)[0].replace(/^https?:\/\/[^/]+/, "");
            if (!mangaRe.test(path) || byUrl.has(path)) continue;
            const img = anchor.querySelector("img");
            const title = ((img && img.getAttribute("alt")) || text(anchor) || anchor.getAttribute("title") || "").trim();
            if (title) byUrl.set(path, { url: path, title: title, cover: coverOf(anchor) });
        }
        return [...byUrl.values()];
    };

    const signature = () => readCards().map((card) => card.url).join("|");

    // As on a title page, the content section stays hidden in an app's WebView; it is shown so the field can be tapped
    const revealContent = () => {
        for (const section of document.querySelectorAll("section.webpage")) {
            if (getComputedStyle(section).display === "none") section.style.display = "block";
        }
    };

    const waitFor = async (check) => {
        const until = Date.now() + OPT.timeoutMs;
        while (Date.now() < until && !check()) await sleep(OPT.pollMs);
    };

    const harvest = async () => {
        await waitFor(() => readCards().length > 0);
        if (OPT.query) {
            const before = signature();
            revealContent();
            const field = document.getElementById("main-search-input");
            field.scrollIntoView({ block: "center" });
            await sleep(400);
            const rect = field.getBoundingClientRect();
            // The site turns off focus() in JavaScript: the field is focused with a touch, then typed into with keys
            tap(rect.left + rect.width / 2, rect.top + rect.height / 2);
            await sleep(800);
            type(OPT.query);
            await waitFor(() => signature() !== before);
        }
        return { cards: readCards() };
    };

    harvest().then((result) => done(JSON.stringify(result)));
})
