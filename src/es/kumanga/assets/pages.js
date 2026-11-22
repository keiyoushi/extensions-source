(() => {
    if (!window.Alpine) return null;
    const forAttr = window.Alpine.prefixed('for');
    const bindAttr = window.Alpine.prefixed('bind:');
    const findImg = (root) =>
        root.querySelector('img') ?? [...root.querySelectorAll('template')].map((t) => findImg(t.content)).find(Boolean);
    const lists = [...document.querySelectorAll(`template[${CSS.escape(forAttr)}]`)]
        .map((loop) => {
            const binding = [...(findImg(loop.content)?.attributes ?? [])].find(
                (a) => (a.name.startsWith(':') || a.name.startsWith(bindAttr)) && a.name.endsWith('src'),
            );
            const [, item, index, list] =
                loop.getAttribute(forAttr).match(/^\s*\(?\s*([\w$]+)\s*(?:,\s*([\w$]+))?\s*\)?\s+(?:in|of)\s+([\s\S]+)$/) ?? [];
            if (!binding || !list) return null;
            const items = window.Alpine.evaluate(loop, list);
            if (!Array.isArray(items) || items.length === 0) return null;
            return [...items].map((value, i) =>
                window.Alpine.evaluate(loop, binding.value, { scope: { [item]: value, ...(index ? { [index]: i } : {}) } }),
            );
        })
        .filter(Boolean);
    const [pages] = lists;
    return lists.length === 1 && pages.every((src) => typeof src === 'string') && new Set(pages).size === pages.length ? pages : null;
})()
