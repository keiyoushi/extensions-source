// Decoded API responses from the site's decoder
(handle) => {
    const defineProperty = Object.defineProperty;
    const capture = (holder) => setTimeout(() => handle(holder));
    defineProperty(Object.prototype, "items", {
        configurable: true,
        set(value) {
            defineProperty(this, "items", {
                value,
                writable: true,
                enumerable: true,
                configurable: true,
            });
            capture(this);
        },
    });
    Object.defineProperty = function (target, key, descriptor) {
        const result = defineProperty.apply(this, arguments);
        if (key === "items" && descriptor && "value" in descriptor) capture(target);
        return result;
    };
}
