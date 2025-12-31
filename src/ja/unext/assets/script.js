(function() {
    if (window._keyCheckRunning) return;
    window._keyCheckRunning = true;

    let attempts = 0;
    const interval = setInterval(async function() {
        attempts++;
        try {
            const el = document.querySelector(".swiper");
            if (!el) {
                return;
            }

            const getFiber = n => {
                const k = Object.keys(n).find(x =>
                    x.startsWith("__reactFiber$") ||
                    x.startsWith("__reactInternalInstance$")
                );
                return n ? n[k] : null;
            };

            let curr = getFiber(el);
            let mgr = null;
            while (curr) {
                if (curr.memoizedProps?.manager) {
                    mgr = curr.memoizedProps.manager;
                    break;
                }
                curr = curr.return;
            }

            if (!mgr) {
                return;
            }

            if (!mgr.parser?.drmContext?.keys) {
                return;
            }

            const keys = mgr.parser.drmContext.keys;
            const ids = Object.keys(keys);

            if (ids.length === 0) {
                try {
                    const files = mgr.parser.drmParser.drmHeader.encryptedFileList;
                    const first = Object.keys(files)[0];
                    mgr.parser.getBinaryObject(first);
                } catch (e) {}
                return;
            }

            const out = {};
            for (const id of ids) {
                const raw = await window.crypto.subtle.exportKey("raw", keys[id]);
                out[id] = btoa(String.fromCharCode(...new Uint8Array(raw)));
            }

            clearInterval(interval);
            window.android.passKeys(JSON.stringify(out));

        } catch (e) {}
    }, 1000);
})();
