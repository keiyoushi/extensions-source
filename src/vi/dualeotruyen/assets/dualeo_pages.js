(pass, reject) => {
    const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
    const WALK_TIMEOUT_MS = 50000;
    const SLOT_TIMEOUT_MS = 12000;
    const startedAt = Date.now();

    const imageSrc = (el) => {
        const src = el.getAttribute("src") || "";
        if (!/^https?:/.test(src)) return null;
        if (!el.complete || el.naturalWidth < 100 || el.naturalHeight < 100) return null;
        return src;
    };

    const deepSlots = (root) => {
        const out = [];
        root.querySelectorAll("img, canvas").forEach((el) => {
            if (el.querySelector("img, canvas")) return;
            out.push(el);
        });
        return out;
    };

    const findContainer = () => {
        let best = null;
        let bestCount = 3;
        document.querySelectorAll("img, canvas").forEach((el) => {
            const parent = el.parentElement;
            if (!parent || parent === document.body || parent === document.documentElement) return;
            const count = parent.querySelectorAll("img, canvas").length;
            if (count > bestCount) {
                best = parent;
                bestCount = count;
            }
        });
        return best;
    };

    const slotUrl = (el) => {
        if (!el.isConnected) return null;
        const bindings = window.__dualeoCanvasUrls;
        if (bindings) {
            const bound = bindings.get(el);
            if (bound) return bound;
        }
        if (typeof el.naturalWidth === "number") {
            const direct = imageSrc(el);
            if (direct) return direct;
        }
        if (!el.querySelector) return null;
        const inner = el.querySelector("img, canvas");
        if (!inner) return null;
        if (bindings) {
            const boundInner = bindings.get(inner);
            if (boundInner) return boundInner;
        }
        return typeof inner.naturalWidth === "number" ? imageSrc(inner) : null;
    };

    const walk = async () => {
        let container = findContainer();
        const started = Date.now();
        while ((!container || deepSlots(container).length < 2) && Date.now() - started < 30000) {
            await sleep(250);
            container = findContainer();
        }
        if (!container) throw new Error("Reader not found");

        const slots = deepSlots(container);
        if (!slots.length) throw new Error("Reader is empty");
        const after = slots.map((el) => el.nextElementSibling);

        const urls = new Array(slots.length).fill(null);
        let misses = 0;
        for (let i = 0; i < slots.length; i++) {
            if (Date.now() > startedAt + WALK_TIMEOUT_MS) break;
            const el = slots[i];
            const waitedAt = Date.now();
            while (el.isConnected && Date.now() - waitedAt < SLOT_TIMEOUT_MS) {
                const url = slotUrl(el);
                if (url) { urls[i] = url; break; }
                el.scrollIntoView({ block: "center" });
                await sleep(150);
            }
            if (!urls[i] && !el.isConnected && after[i] && after[i].isConnected) {
                const candidate = after[i].previousElementSibling;
                if (candidate && candidate !== el && slots.indexOf(candidate) === -1) {
                    urls[i] = slotUrl(candidate);
                }
            }
            if (!urls[i] && el.isConnected) misses++;
        }

        const pages = urls.filter((url) => url != null);
        if (!pages.length) throw new Error("No pages loaded");
        pass(JSON.stringify({ pages, misses, slots: slots.length }));
    };

    walk().catch((e) => reject(e && e.message || String(e)));
}
