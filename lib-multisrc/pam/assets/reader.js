// The download WebView is off-screen, so the reader never starts, and its page key is imported
// in the reader worker rather than on this window.
(bridge, workerPatch) => {
    const installed = Symbol.for(`${bridge}-reader`);
    if (window[installed]) return;
    window[installed] = true;

    // Proxied native getters, so they still read as native code
    for (const [name, value] of [["visibilityState", "visible"], ["hidden", false]]) {
        const descriptor = Object.getOwnPropertyDescriptor(Document.prototype, name);
        Object.defineProperty(Document.prototype, name, { ...descriptor, get: new Proxy(descriptor.get, { apply: () => value }) });
    }

    let posted = false;
    new BroadcastChannel(bridge).onmessage = (event) => {
        if (posted || typeof event.data !== "string" || !event.data) return;
        posted = true;
        window[bridge].post(JSON.stringify({ reader: { contentKey: event.data } }));
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
            const [url, options] = args;
            if (!options || options.type !== "module") return Reflect.construct(target, args, newTarget);
            const script = (source) => URL.createObjectURL(new Blob([source], { type: "text/javascript" }));
            const patch = script(`(${workerPatch})(${JSON.stringify(bridge)});`);
            const original = new URL(String(url), location.href).href;
            const wrapper = script(`import ${JSON.stringify(patch)};\nimport ${JSON.stringify(original)};`);
            return Reflect.construct(target, [wrapper, options], newTarget);
        },
    });
}
