(function() {
    if (window._keyCheckRunning) return;
    window._keyCheckRunning = true;

    const attempt = async (observer) => {
        try {
            const el = document.querySelector(".swiper");
            if (!el) return;

            const getFiber = n => {
                const k = Object.keys(n).find(x =>
                    x.startsWith("__reactFiber$") ||
                    x.startsWith("__reactInternalInstance$")
                );
                return n?.[k];
            };

            let curr = getFiber(el);
            let mgr = null;
            while (curr) {
                if (mgr = curr.memoizedProps?.manager) break;
                curr = curr.return;
            }

            if (!mgr?.parser?.drmContext) return;
            if (!mgr?.parser?.drmParser?.drmHeader) return;

            const fileList = mgr.parser.drmParser.drmHeader.encryptedFileList;
            const contextKeys = mgr.parser.drmContext.keys || {};
            const loadedKeyIds = new Set(Object.keys(contextKeys));

            const missingKeyIds = new Set();
            const keyIdToFilePath = {};

            for (const [path, info] of Object.entries(fileList)) {
                if (info.keyId && !loadedKeyIds.has(info.keyId)) {
                    missingKeyIds.add(info.keyId);
                    keyIdToFilePath[info.keyId] ??= path;
                }
            }

            if (missingKeyIds.size > 0) {
                for (const kid of missingKeyIds) {
                    const filePath = keyIdToFilePath[kid];
                    if (filePath) {
                        mgr.parser.getBinaryObject(filePath).catch(() => {});
                    }
                }
                return;
            }

            const out = {};
            for (const id of loadedKeyIds) {
                try {
                    const raw = await window.crypto.subtle.exportKey("raw", contextKeys[id]);
                    out[id] = btoa(String.fromCharCode(...new Uint8Array(raw)));
                } catch (e) {
                }
            }

            if (Object.keys(out).length > 0) {
                if (observer) observer.disconnect();
                window.android.passKeys(JSON.stringify(out));
            }

        } catch (e) {
        }
    };

    const observer = new MutationObserver(() => {
        attempt(observer);
    });

    observer.observe(document.body, { childList: true, subtree: true });
    attempt(observer);
})();
