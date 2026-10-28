// Open the chapter's sealed pages with the site's own reader module
async (input) => {
    let sitekey = null;
    window.turnstile = {
        render: (el, options) => {
            if (input.token) {
                setTimeout(() => options.callback(input.token));
            } else {
                sitekey = options.sitekey;
                setTimeout(() => options["error-callback"]());
            }
            return "shim";
        },
        remove: () => {},
    };

    const entry = await import(input.entry);
    const reader = Object.values(entry).find(
        (it) => it && typeof it.openReader === "function" && typeof it.fetchManifest === "function",
    );
    if (!reader) throw new Error("Reader module not found");

    const opened = reader.openReader(input.reader, input.chapterId);
    if (!opened) throw new Error("Reader sealed");
    let pages;
    try {
        pages = await reader.fetchManifest(opened, input.chapterId, input.seriesSlug, opened.q);
    } catch (e) {
        if (sitekey) e.sitekey = sitekey;
        throw e;
    }
    return pages.map((page) => ({ n: page.n, src: page.src, srcset: page.srcset }));
}
