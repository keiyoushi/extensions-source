document.addEventListener("DOMContentLoaded", (e) => {
    // This is intentional. Simply binding `_` to `window.__interface__.gettext` will
    // throw an error: "Java bridge method can't be invoked on a non-injected object".
    const _ = (key) => window.__interface__.gettext(key);

    if (document.querySelector("#unlock-full")) {
        window.__interface__.passError(_("error_locked_chapter_unlock_in_webview"));
    }
});

document.addEventListener(
    "you-right-now:reeeeeee",
    async (e) => {
        const _ = (key) => window.__interface__.gettext(key);

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
                        window.__interface__.passError(`${_("error_open_in_webview_then_try_again")} (${_("error_token_not_found")}).`);
                    }

                    const value = entries[0].value;

                    if (value.expireTimeMillis < Date.now()) {
                        window.__interface__.passError(`${_("error_open_in_webview_then_try_again")} (${_("error_token_expired")}).`);
                    }

                    resolve(value.token)
                }
            });

            const manifest = JSON.parse(document.querySelector("#lmao-init").textContent).manifest;
            window.__interface__.passPayload(manifest, act, await e.detail);
        } catch (e) {
            window.__interface__.passError(`${_("error_unknown_error")}: ${e}`);
        }
    },
    { once: true },
);
