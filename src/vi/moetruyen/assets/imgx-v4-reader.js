(() => {
    if (window.__MOE_IMGX_INSTALLED__) return;
    window.__MOE_IMGX_INSTALLED__ = true;

    const bridge = window.__IMGX_BRIDGE__;
    const post = (value) => {
        try {
            bridge.post(JSON.stringify(value));
        } catch (_) {
        }
    };
    const captured = new Map();

    const toBase64 = (bytes) => {
        let binary = "";
        const chunk = 0x8000;
        for (let offset = 0; offset < bytes.length; offset += chunk) {
            binary += String.fromCharCode.apply(null, bytes.subarray(offset, offset + chunk));
        }
        return btoa(binary);
    };

    const captureBuffer = (index, buffer, mime) => {
        if (!Number.isSafeInteger(index) || index < 0) return;
        if (captured.has(index)) return;
        try {
            const bytes = new Uint8Array(buffer);
            captured.set(index, true);
            post({
                type: "page",
                index,
                data: toBase64(bytes),
                mime: mime || "image/webp",
            });
        } catch (_) {
        }
    };

    try {
        const channel = new BroadcastChannel("moe-imgx-pages");
        channel.onmessage = (event) => {
            const data = event.data;
            if (!data || typeof data !== "object") return;
            if (data.type === "page") {
                captureBuffer(Number(data.pageIndex), data.buffer, data.mime);
            }
        };
    } catch (error) {
        post({
            type: "error",
            message: `BroadcastChannel failed: ${error?.message || error}`,
        });
    }

    (async () => {
        const waitFor = async (predicate, timeout) => {
            const deadline = performance.now() + timeout;
            while (performance.now() < deadline) {
                const value = predicate();
                if (value) return value;
                await new Promise((resolve) => setTimeout(resolve, 40));
            }
            return null;
        };

        try {
            const runtime = await waitFor(() => globalThis.__IMGX_RUNTIME__, 15000);
            if (!runtime || typeof runtime.renderPage !== "function") {
                throw new Error("IMGX reader runtime unavailable");
            }

            const pagesRoot = await waitFor(
                () => document.querySelector("[data-reader-lazy-pages]"),
                10000,
            );
            if (!pagesRoot) {
                throw new Error("IMGX reader metadata missing");
            }

            const declaredTotal = Number(pagesRoot.getAttribute("data-reader-total-pages") || 0);
            const shellCount = document.querySelectorAll(".page-protected-shell[data-page-index]").length;
            const pageCount = [declaredTotal, shellCount].find((value) => Number.isSafeInteger(value) && value > 0);
            if (!pageCount) {
                throw new Error("IMGX page count missing");
            }

            await waitFor(
                () => captured.size > 0 || document.querySelector(".page-protected-shell.is-loaded"),
                15000,
            );

            for (let index = 0; index < pageCount; index++) {
                if (captured.has(index)) continue;
                try {
                    if (typeof runtime.releasePage === "function") {
                        runtime.releasePage(index);
                    }
                    await runtime.renderPage(index);
                } catch (_) {
                }
                if (!captured.has(index)) {
                    await waitFor(() => captured.has(index), 2500);
                }
                if (captured.size === 0 && index >= 2) {
                    throw new Error("IMGX captured 0 pages");
                }
            }

            await waitFor(() => captured.size >= pageCount, 4000);

            const missing = [];
            for (let index = 0; index < pageCount; index++) {
                if (!captured.has(index)) missing.push(index);
            }
            if (captured.size === 0) {
                throw new Error("IMGX captured 0 pages");
            }
            if (missing.length > 0) {
                throw new Error(`IMGX missing pages: ${missing.join(",")}`);
            }

            post({ type: "done", count: captured.size });
        } catch (error) {
            post({
                type: "error",
                message: error?.message || String(error),
            });
        }
    })();
})();
