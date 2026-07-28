(() => {
    const done = (value) => window["__AUTH_BRIDGE_NAME__"].post(value || "");

    try {
        const request = indexedDB.open("manga-app");

        request.onerror = () => done(null);
        request.onsuccess = () => {
            const db = request.result;

            if (!db.objectStoreNames.contains("auth")) {
                db.close();
                done(null);
                return;
            }

            const transaction = db.transaction("auth", "readonly");
            const tokenRequest = transaction.objectStore("auth").get("apiAccessToken");

            tokenRequest.onerror = () => {
                db.close();
                done(null);
            };
            tokenRequest.onsuccess = () => {
                const token = tokenRequest.result;
                db.close();
                done(typeof token === "string" ? token : null);
            };
        };
    } catch (_error) {
        done(null);
    }
})();
