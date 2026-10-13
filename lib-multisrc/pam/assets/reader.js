// The site reader's manifest and page key, captured as its own code decodes them.
(bridge) => {
    const installed = Symbol.for(`${bridge}-reader`);
    if (window[installed]) return;
    window[installed] = true;

    const post = (reader) => window[bridge].post(JSON.stringify({ reader }));

    const defineProperty = Object.defineProperty;
    const subtle = crypto.subtle;
    const importKey = subtle.importKey;
    defineProperty(Object.prototype, "hint", {
        configurable: true,
        set(value) {
            defineProperty(this, "hint", { value, writable: true, enumerable: true, configurable: true });
            const manifest = this;
            // Checked once the whole manifest is assigned; the page key is imported after that.
            queueMicrotask(() => {
                if (typeof manifest.base !== "string" || !manifest.base.startsWith("/p/")) return;
                subtle.importKey = function (format, keyData, algorithm) {
                    if (format === "raw" && (algorithm?.name ?? algorithm) === "HKDF") {
                        subtle.importKey = importKey;
                        const { base, count, variants } = manifest;
                        const key = new Uint8Array(keyData.buffer ?? keyData, keyData.byteOffset ?? 0, keyData.byteLength);
                        post({ manifest: { base, count, variants }, contentKey: btoa(String.fromCharCode(...key)) });
                    }
                    return importKey.apply(this, arguments);
                };
            });
        },
    });
}
