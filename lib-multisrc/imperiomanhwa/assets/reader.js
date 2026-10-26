// Open the chapter's sealed pages with the site's own reader module
async (input) => {
    const entry = await import(input.entry);
    const reader = Object.values(entry).find(
        (it) => it && typeof it.openReader === "function" && typeof it.fetchManifest === "function",
    );
    if (!reader) throw new Error("Reader module not found");

    const opened = reader.openReader(input.reader, input.chapterId);
    if (!opened) throw new Error("Reader sealed");
    const pages = await reader.fetchManifest(opened, input.chapterId, input.seriesSlug, opened.q);
    return pages.map((page) => ({ n: page.n, src: page.src, srcset: page.srcset }));
}
