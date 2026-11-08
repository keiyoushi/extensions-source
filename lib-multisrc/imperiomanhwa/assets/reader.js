// Unwrap the chapter's reader key with the site's openReader, the export that derives the rk|/ks| keys
async (input) => {
    const entry = await import(input.entry);
    const openReader = Object.values(entry)
        .flatMap((value) => (value && typeof value === 'object' ? Object.values(value) : [value]))
        .find((fn) => {
            if (typeof fn !== 'function') return false;
            const source = Function.prototype.toString.call(fn);
            return source.includes('rk|') && source.includes('ks|');
        });
    if (!openReader) throw new Error('Reader module not found');
    const opened = openReader(input.reader, input.chapterId);
    if (!opened) throw new Error('Reader sealed');
    return { ep: opened.ep, p: opened.p, v: opened.v, k: opened.k, q: opened.q };
}
