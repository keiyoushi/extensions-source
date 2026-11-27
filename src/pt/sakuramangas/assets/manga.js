// A title page: its details, and the whole chapter list, loaded by tapping "ver mais" until the button is gone.
((done, tap, options) => {
    "use strict";

    const OPT = Object.assign({ timeoutMs: 90000, settleMs: 9000, pollMs: 300, withChapters: true }, options || {});
    const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
    const text = (el) => (el && el.textContent ? el.textContent : "").replace(/\s+/g, " ").trim();
    const abs = (src) => (src ? new URL(src, location.href).href : null);

    const slug = (location.pathname.match(/\/obras\/([^/]+)/) || [])[1] || "";
    const chapterRe = new RegExp("^/obras/" + slug.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "/(\\d+(?:\\.\\d+)?)/?$");

    const pick = (...selectors) => {
        for (const selector of selectors) {
            const value = text(document.querySelector(selector));
            if (value) return value;
        }
        return "";
    };

    const details = () => {
        const cover = document.querySelector(".capa");
        const genres = [...document.querySelectorAll(".generos a, .generos .badge, [class*='genero'] a, [class*='genero'] .badge")]
            .map(text).filter((genre) => genre && genre.length <= 40);
        return {
            title: pick(".h1-titulo", ".h1-titulo-mobile"),
            author: pick(".autor", ".autor-mobile"),
            description: pick(".sinopse"),
            status: pick("#status"),
            demographic: pick("#demografia"),
            cover: cover ? abs(cover.getAttribute("src")) : null,
            genres: [...new Set(genres)],
        };
    };

    const readChapters = (into) => {
        for (const anchor of document.querySelectorAll(".chapter-list a[href]")) {
            const path = (anchor.getAttribute("href") || "").split(/[?#]/)[0].replace(/^https?:\/\/[^/]+/, "");
            const match = path.match(chapterRe);
            if (!match || into.has(path)) continue;
            const row = anchor.closest(".d-flex") || anchor;
            into.set(path, { number: match[1], url: path, date: text(row.querySelector(".meta-info")) });
        }
        return into;
    };

    // The site keeps its content section hidden until a load step that fails in an app's WebView; the chapter list is
    // already in it, so it is shown for "ver mais" to have a place to be tapped
    const revealContent = () => {
        for (const section of document.querySelectorAll("section.webpage")) {
            if (getComputedStyle(section).display === "none") section.style.display = "block";
        }
    };

    // The button left once the content is shown, or null when the list is complete
    const moreButton = () => {
        revealContent();
        const button = document.getElementById("ver-mais");
        if (!button || button.disabled || !button.offsetParent) return null;
        const style = getComputedStyle(button);
        return style.display === "none" || style.visibility === "hidden" ? null : button;
    };

    const harvest = async () => {
        const chapters = new Map();
        const started = Date.now();
        while (Date.now() - started < OPT.timeoutMs) {
            readChapters(chapters);
            if (details().title && (!OPT.withChapters || chapters.size > 0)) break;
            await sleep(OPT.pollMs);
        }

        if (OPT.withChapters) {
            for (let button = moreButton(); button; button = moreButton()) {
                // A tap outside the viewport is dropped, and the button sits below the list
                button.scrollIntoView({ block: "center" });
                await sleep(400);
                const rect = button.getBoundingClientRect();
                if (rect.width <= 0 || rect.height <= 0) break;
                const before = chapters.size;
                tap(rect.left + rect.width / 2, rect.top + rect.height / 2);
                const until = Date.now() + OPT.settleMs;
                while (Date.now() < until && readChapters(chapters).size === before) await sleep(OPT.pollMs);
                if (chapters.size === before) break;
            }
        }

        const list = [...chapters.values()].sort((a, b) => parseFloat(b.number) - parseFloat(a.number));
        return { details: details(), chapters: list, complete: moreButton() === null };
    };

    harvest().then((result) => done(JSON.stringify(result)));
})
