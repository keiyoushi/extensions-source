(function() {
    if (window._keyCheckRunning) return;
    window._keyCheckRunning = true;

    const interval = setInterval(async function() {
        try {
            const el = document.querySelector(".swiper");
            if (!el) return;

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

            if (!mgr || !mgr.parser || !mgr.parser.drmContext) return;
            if (!mgr.parser.drmParser || !mgr.parser.drmParser.drmHeader) return;

            const fileList = mgr.parser.drmParser.drmHeader.encryptedFileList;
            const contextKeys = mgr.parser.drmContext.keys || {};
            const loadedKeyIds = Object.keys(contextKeys);

            const missingKeyIds = new Set();
            const keyIdToFilePath = {};

            for (const [path, info] of Object.entries(fileList)) {
                if (info.keyId && !loadedKeyIds.includes(info.keyId)) {
                    missingKeyIds.add(info.keyId);
                    if (!keyIdToFilePath[info.keyId]) {
                        keyIdToFilePath[info.keyId] = path;
                    }
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
                clearInterval(interval);
                window.android.passKeys(JSON.stringify(out));
            }

        } catch (e) {
        }
    }, 1000);
})();
