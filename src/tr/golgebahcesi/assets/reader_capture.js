(bridge, channelName, workerHook) => {
    if (window !== window.top) return;

    // Turn off the reader's prefetch so each decoded image follows its fetch
    localStorage.setItem("rdr_diag_ovr", JSON.stringify({ prefetch: false }));

    window.Blob = new Proxy(window.Blob, {
        construct(target, args) {
            const [parts, options] = args;
            if (Array.isArray(parts) && typeof parts[0] === "string" && /javascript/.test((options && options.type) || "")) {
                return Reflect.construct(target, [[workerHook, ...parts], options]);
            }
            return Reflect.construct(target, args);
        },
    });

    // Report pages visible so the reader decodes them
    const observed = [];
    window.IntersectionObserver = new Proxy(window.IntersectionObserver, {
        construct(target, args) {
            const observer = Reflect.construct(target, args);
            const callback = args[0];
            observer.observe = new Proxy(observer.observe, {
                apply(observe, thisArg, observeArgs) {
                    observed.push({ element: observeArgs[0], callback, observer });
                    return Reflect.apply(observe, thisArg, observeArgs);
                },
            });
            return observer;
        },
    });
    const setVisible = (page, isIntersecting) => {
        for (const { element, callback, observer } of observed) {
            if (!page.contains(element)) continue;
            callback([{ target: element, isIntersecting }], observer);
        }
    };

    const captured = new Set();
    let count = 0;

    new BroadcastChannel(channelName).onmessage = ({ data }) => {
        if (data.count) {
            count = data.count;
            bridge.postMessage("count:" + count);
            return;
        }
        if (captured.has(data.index)) return;
        captured.add(data.index);
        localStorage.removeItem("rdr_diag_ovr");
        const reader = new FileReader();
        reader.onload = () => bridge.postMessage("page:" + data.index + "," + reader.result);
        reader.readAsDataURL(data.image);
    };

    // Page index to when it was last marked visible
    const visible = new Map();
    let first = -1;
    let since = 0;
    setInterval(() => {
        if (!count) return;
        const pages = document.querySelectorAll("[data-reader-page]");
        const wanted = [];
        for (let i = 0; i < count && wanted.length < 4; i++) {
            if (!captured.has(i)) wanted.push(i);
        }
        if (wanted.length === 0) return;
        for (const i of [...visible.keys()]) {
            if (!wanted.includes(i)) {
                setVisible(pages[i], false);
                visible.delete(i);
            }
        }
        const now = Date.now();
        for (const i of wanted) {
            if (now - (visible.get(i) || 0) < 10000) continue;
            if (!pages[i]) {
                bridge.postMessage("error:Page " + (i + 1) + " is missing from the reader");
                return;
            }
            setVisible(pages[i], true);
            visible.set(i, now);
        }
        if (wanted[0] !== first) {
            first = wanted[0];
            since = now;
        } else if (now - since > 30000) {
            bridge.postMessage("error:Page " + (first + 1) + " did not load");
        }
    }, 250);
}
