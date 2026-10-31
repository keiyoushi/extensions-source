// Prepended to the reader worker: its raw HKDF import is the page key. Patched on the prototype so
// aliased calls and code in imported chunks are covered too.
(bridge) => {
    const subtle = SubtleCrypto.prototype;
    subtle.importKey = new Proxy(subtle.importKey, {
        apply(target, thisArg, args) {
            try {
                const [format, keyData, algorithm] = args;
                const name = (algorithm && algorithm.name) || algorithm;
                if (format === "raw" && name === "HKDF" && keyData && keyData.byteLength === 32) {
                    const bytes = ArrayBuffer.isView(keyData)
                        ? new Uint8Array(keyData.buffer, keyData.byteOffset, keyData.byteLength)
                        : new Uint8Array(keyData);
                    new BroadcastChannel(bridge).postMessage(btoa(String.fromCharCode(...bytes)));
                }
            } catch (ignored) {}
            return Reflect.apply(target, thisArg, args);
        },
    });
}
