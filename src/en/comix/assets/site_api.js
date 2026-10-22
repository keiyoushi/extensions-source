// The site's own API client, from the chunks next to the running entry. Every build re-mangles
// names, so members are resolved by source shape; the selector receives the exported members
// found so far, each with the object holding it, and returns what it needs.
//
// The site also wraps its API objects in proxies that report no keys, so enumeration alone
// finds nothing behind them. For those, a second pass recovers the member keys and sources
// from the chunk text and probes each exported object for them; a probed function reports
// the recovered source as its own, so the shape checks still see the function's text.
(select) => async () => {
    const FIND_TIMEOUT_MS = 10000;
    const MAX_SOURCE = 6000;
    const KEYWORDS = new Set([
        "if", "for", "while", "switch", "catch", "return", "function", "else", "do", "new", "typeof",
    ]);
    const isGet = (value) => typeof value === "function" &&
        String(value).includes(".get(") &&
        String(value).endsWith(".data");
    const namespaces = new Map();
    const memberCache = new Map();
    const texts = new Map();

    const chunkText = async (url) => {
        if (!texts.has(url)) {
            try {
                texts.set(url, await (await fetch(url)).text());
            } catch (e) {
                texts.set(url, "");
            }
        }
        return texts.get(url);
    };

    // Index just past the body that starts at or after `start`: a method's braced body, or an
    // arrow's body, which ends at the enclosing comma or closer when it is an expression.
    // Brackets and strings are tracked so a body is cut where it really ends.
    const scanBody = (text, start) => {
        let i = start;
        while (i < text.length && /\s/.test(text[i])) i++;
        const expr = text[i] !== "{";
        const stack = [];
        let mode = null;
        if (!expr) {
            stack.push("{");
            i++;
        }
        for (; i < text.length && i - start <= MAX_SOURCE; i++) {
            const c = text[i];
            const next = text[i + 1] || "";
            if (mode === "line") {
                if (c === "\n") mode = null;
                continue;
            }
            if (mode === "block") {
                if (c === "*" && next === "/") {
                    i++;
                    mode = null;
                }
                continue;
            }
            if (mode) {
                if (c === "\\") {
                    i++;
                    continue;
                }
                if (mode === "`" && c === "$" && next === "{") {
                    stack.push("${");
                    mode = null;
                    i++;
                    continue;
                }
                if (c === mode) mode = null;
                continue;
            }
            if (c === "/" && next === "/") {
                mode = "line";
                i++;
                continue;
            }
            if (c === "/" && next === "*") {
                mode = "block";
                i++;
                continue;
            }
            if (c === "'" || c === '"' || c === "`") {
                mode = c;
                continue;
            }
            if (c === "(" || c === "[" || c === "{") {
                stack.push(c);
                continue;
            }
            if (c === ")" || c === "]" || c === "}") {
                if (stack.length === 0) return expr ? i : i + 1;
                if (stack.pop() === "${") {
                    mode = "`";
                    continue;
                }
                if (!expr && stack.length === 0) return i + 1;
                continue;
            }
            if (expr && c === "," && stack.length === 0) return i;
        }
        return Math.min(i, text.length);
    };

    // The function definitions in a chunk's text, as { key, source }: object properties
    // holding arrows, and method shorthand, in the forms the site's bundler emits.
    const extractDefinitions = (text) => {
        const defs = [];
        const seen = new Set();
        const add = (key, sourceStart, bodyStart) => {
            if (!key || KEYWORDS.has(key)) return;
            const source = text.slice(sourceStart, scanBody(text, bodyStart)).trim();
            if (source.length < 8 || seen.has(key + "@" + sourceStart)) return;
            seen.add(key + "@" + sourceStart);
            defs.push({ key, source });
        };
        for (const match of text.matchAll(/([A-Za-z_$][\w$]*)\s*:\s*(async\s*(?:\([^)]*\)|[\w$]+)\s*=>|\([^)]*\)\s*=>|[\w$]+\s*=>)/g)) {
            add(match[1], match.index + match[0].length - match[2].length, match.index + match[0].length);
        }
        for (const match of text.matchAll(/\basync\s+([A-Za-z_$][\w$]*)\s*\([^)]*\)\s*\{/g)) {
            add(match[1], match.index, match.index + match[0].length - 1);
        }
        for (const match of text.matchAll(/(?:^|[,{;])\s*([A-Za-z_$][\w$]*)\s*\([^)]*\)\s*\{/g)) {
            add(match[1], match.index + match[0].indexOf(match[1]), match.index + match[0].length - 1);
        }
        return defs;
    };

    // Members hidden behind the key-hiding proxies: probe each exported object for the keys
    // recovered from the text. A hit wraps the live function so it reports the recovered
    // source; the wrapper still calls through to the site's own function.
    const probeHidden = async (url, exported, members) => {
        const text = await chunkText(url);
        if (!text) return;
        const defs = extractDefinitions(text);
        if (!defs.length) return;
        const objects = Object.values(exported).filter(
            (value) => value && (typeof value === "object" || typeof value === "function"),
        );
        for (const owner of objects) {
            for (const { key, source } of defs) {
                // No already-probed shortcut: unrelated objects reuse the same
                // (mangled) keys, so one key can carry several definitions; each
                // is a separate candidate and the selectors pick by source shape
                let live;
                try {
                    live = owner[key];
                } catch (e) {
                    continue;
                }
                if (typeof live !== "function") continue;
                const wrapped = function (...args) {
                    return live.apply(this && this !== wrapped ? this : owner, args);
                };
                wrapped.toString = () => source;
                members.push({ owner, key, value: wrapped });
            }
        }
    };

    const enumerate = (exported) => {
        const members = [];
        for (const [, exportedValue] of Object.entries(exported)) {
            if (exportedValue && typeof exportedValue === "object") {
                for (const [key, member] of Object.entries(exportedValue)) {
                    members.push({ owner: exportedValue, key, value: member });
                    if (member && typeof member === "object") {
                        for (const [nestedKey, nested] of Object.entries(member)) {
                            members.push({ owner: member, key: nestedKey, value: nested });
                        }
                    }
                }
            }
        }
        return members;
    };

    const find = async () => {
        const scripts = performance.getEntriesByType("resource")
            .map((entry) => entry.name)
            .filter((url) => /\/assets\/.*\.js$/.test(url));
        const entry = scripts.find((url) => /\/main-[^/]+\.js$/.test(url));
        if (!entry) return null;
        // The site serves its chunks from more than one mirror (its build directory
        // and a /t3/ mirror), and which mirror serves a chunk varies per load, so
        // every loaded script is a candidate; shape checks do the filtering
        const urls = [...new Set(scripts)];
        for (const url of urls) {
            if (!namespaces.has(url)) {
                let exported = {};
                try {
                    exported = await import(url);
                } catch (e) {
                    // Not an importable module; it contributes no members
                }
                namespaces.set(url, exported);
                memberCache.set(url, enumerate(exported));
            }
            const found = select(memberCache.get(url), isGet);
            if (found) return found;
        }
        // Nothing the selector needs is enumerable; retry with the members hidden
        // behind the site's proxies included
        for (const url of urls) {
            const members = [...memberCache.get(url)];
            await probeHidden(url, namespaces.get(url), members);
            const found = select(members, isGet);
            if (found) return found;
        }
        return null;
    };
    // The site's chunks keep loading after the page's load event
    const deadline = Date.now() + FIND_TIMEOUT_MS;
    for (;;) {
        const api = await find();
        if (api || Date.now() >= deadline) return api;
        await new Promise((resolve) => setTimeout(resolve, 250));
    }
}
