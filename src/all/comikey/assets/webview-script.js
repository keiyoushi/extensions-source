document.addEventListener("DOMContentLoaded", (e) => {
    if (document.querySelector("#unlock-full")) {
        window.__interface__.passError("error_locked_chapter_unlock_in_webview");
    }
});

document.addEventListener(
    "you-right-now:reeeeeee",
    async (e) => {
        try {
            const db = await new Promise((resolve, reject) => {
                const request = indexedDB.open("firebase-app-check-database");

                request.onsuccess = (event) => resolve(event.target.result);
                request.onerror = (event) => reject(event.target);
            });

            const act = await new Promise((resolve, reject) => {
                db.onerror = (event) => reject(event.target);

                const request = db.transaction("firebase-app-check-store").objectStore("firebase-app-check-store").getAll();

                request.onsuccess = (event) => {
                    const entries = event.target.result;
                    db.close();

                    if (entries.length < 1) {
                        window.__interface__.passError("error_open_in_webview_then_try_again");
                    }

                    const value = entries[0].value;

                    if (value.expireTimeMillis < Date.now()) {
                        window.__interface__.passError("error_open_in_webview_then_try_again");
                    }

                    resolve(value.token)
                }
            });

            const manifest = JSON.parse(document.querySelector("#lmao-init").textContent).manifest;
            window.__interface__.passPayload(manifest, act, await e.detail);
        } catch (e) {
            window.__interface__.passError(`WebView error: ${e}`);
        }
    },
    { once: true },
);
