// The download WebView is off-screen, so the reader never starts, and its page key is imported
// in the reader worker rather than on this window.
(bridge) => {
    const installed = Symbol.for(`${bridge}-reader`);
    if (window[installed]) return;
    window[installed] = true;

    const post = (reader) => window[bridge].post(JSON.stringify({ reader }));
    const defineProperty = Object.defineProperty;

    // Proxied native getters, so they still read as native code
    for (const [name, value] of [["visibilityState", "visible"], ["hidden", false]]) {
        const descriptor = Object.getOwnPropertyDescriptor(Document.prototype, name);
        defineProperty(Document.prototype, name, { ...descriptor, get: new Proxy(descriptor.get, { apply: () => value }) });
    }

    // Only the first manifest and key are sent, so a preloaded chapter's can't be paired with them.
    let manifest = null;
    let contentKey = null;
    let published = false;
    const publish = () => {
        if (published || !manifest || !contentKey) return;
        published = true;
        post({ manifest, contentKey });
    };

    const specs = new WeakMap();
    Object.defineProperty = new Proxy(defineProperty, {
        apply(target, thisArg, args) {
            const defined = Reflect.apply(target, thisArg, args);
            const [obj, key, desc] = args;
            if (!obj || (key !== "base" && key !== "count" && key !== "variants")) return defined;
            let spec = specs.get(obj);
            if (!spec) specs.set(obj, (spec = {}));
            const value = desc && desc.value;
            if (key === "base" && typeof value === "string" && value.startsWith("/p/")) spec.base = value;
            else if (key === "count" && Number.isInteger(value) && value > 0) spec.count = value;
            else if (key === "variants" && Array.isArray(value)) spec.variants = value;
            if (!spec.scheduled) {
                spec.scheduled = true;
                queueMicrotask(() => {
                    spec.scheduled = false;
                    if (manifest || typeof spec.base !== "string" || !Number.isInteger(spec.count)) return;
                    manifest = { base: spec.base, count: spec.count, variants: spec.variants || [] };
                    publish();
                });
            }
            return defined;
        },
    });

    new BroadcastChannel(bridge).onmessage = (event) => {
        if (contentKey || typeof event.data !== "string" || !event.data) return;
        contentKey = event.data;
        publish();
    };

    // Each observer's first target is reported visible; the off-screen view's own "not intersecting"
    // entries for it are dropped so the reader doesn't unload it again.
    const forcedEntry = (target) => {
        const rect = target.getBoundingClientRect();
        return {
            isIntersecting: true,
            isVisible: true,
            intersectionRatio: 1,
            target,
            time: performance.now(),
            boundingClientRect: rect,
            intersectionRect: rect,
            rootBounds: null,
        };
    };
    const NativeObserver = window.IntersectionObserver;
    window.IntersectionObserver = new Proxy(NativeObserver, {
        construct(target, args, newTarget) {
            const [callback, ...rest] = args;
            if (typeof callback !== "function") return Reflect.construct(target, args, newTarget);
            const forced = new WeakSet();
            const filtered = function (entries, observer) {
                const kept = entries.filter((entry) => entry.isIntersecting || !forced.has(entry.target));
                if (kept.length) return callback.call(this, kept, observer);
            };
            const observer = Reflect.construct(target, [filtered, ...rest], newTarget);
            const observe = observer.observe.bind(observer);
            let first = true;
            observer.observe = (element) => {
                const result = observe(element);
                if (first && element instanceof Element) {
                    first = false;
                    forced.add(element);
                    // After the caller has finished setting up the observer
                    queueMicrotask(() => callback.call(observer, [forcedEntry(element)], observer));
                }
                return result;
            };
            return observer;
        },
    });

    const NativeWorker = window.Worker;
    window.Worker = new Proxy(NativeWorker, {
        construct(target, args, newTarget) {
            const absolute = new URL(String(args[0]), location.href);
            if (!absolute.pathname.endsWith(".js")) return Reflect.construct(target, args, newTarget);
            absolute.searchParams.set(bridge, "");
            return Reflect.construct(target, [absolute, args[1]], newTarget);
        },
    });
}
