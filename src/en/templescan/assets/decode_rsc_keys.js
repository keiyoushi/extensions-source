// Finds the site's RSC key decoder among the page's webpack modules and returns its rename table.
(async (chunks, keys, fields) => {
    const sources = await Promise.all(chunks.map(async (url) => (await fetch(url)).text()));
    for (const source of sources) {
        // Plain chunk pushes only: the webpack runtime would boot the app.
        if (/^(?:"use strict";)?\(self\.webpackChunk\w*=/.test(source)) (0, eval)(source);
    }

    const probe = Object.fromEntries(keys.map((key) => [key, key]));
    const factories = Object.keys(self)
        .filter((name) => name.startsWith("webpackChunk"))
        .flatMap((name) => self[name])
        .flatMap((entry) => Object.values(entry[1]));

    for (const factory of factories) {
        const module = { exports: {} };
        const require = () => { throw new Error(); };
        require.r = () => {};
        require.d = (target, getters) => {
            for (const name in getters) Object.defineProperty(target, name, { get: getters[name], enumerable: true });
        };
        try { factory(module, module.exports, require); } catch { continue; }

        for (const exported of Object.values(module.exports)) {
            let decoded;
            try { decoded = exported(probe); } catch { continue; }
            if (!decoded || typeof decoded !== "object") continue;
            const table = Object.fromEntries(
                Object.entries(decoded).filter(([name, key]) => name !== key && keys.includes(key)),
            );
            if (fields.some((field) => field in table)) return table;
        }
    }
    return null;
})
