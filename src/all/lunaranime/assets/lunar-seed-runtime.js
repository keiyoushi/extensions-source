(function (groups) {
    const registry = {};
    const cache = {};
    const captured = [null, null];
    const wrapped = new WeakSet();
    const loaded = new Set();
    let activeUrls = [];
    let activeIndex = -1;

    function record(args) {
        if (args.length >= 2 && typeof args[1] === 'string') {
            if (args[0] === 0) {
                captured[0] = args[1];
            } else if (args[0] === 1) {
                captured[1] = args[1];
            }
        }
    }

    function wrap(namespace) {
        if (!namespace || wrapped.has(namespace)) {
            return namespace;
        }
        wrapped.add(namespace);
        const keys = Object.keys(namespace);
        for (let i = 0; i < keys.length; i++) {
            const original = namespace[keys[i]];
            if (typeof original === 'function') {
                namespace[keys[i]] = new Proxy(original, {
                    apply(target, self, args) {
                        record(args);
                        return Reflect.apply(target, self, args);
                    },
                });
            }
        }
        return namespace;
    }

    function loadNext() {
        while (activeIndex >= 0) {
            const url = activeUrls[activeIndex];
            activeIndex -= 1;
            if (loaded.has(url)) {
                continue;
            }
            const xhr = new XMLHttpRequest();
            xhr.open('GET', url, false);
            xhr.send();
            if (xhr.status >= 200 && xhr.status < 300) {
                loaded.add(url);
                (0, eval)(xhr.responseText);
                return true;
            }
            return false;
        }
        return false;
    }

    function ensure(id) {
        while (!registry[id]) {
            if (!loadNext()) {
                return false;
            }
        }
        return true;
    }

    function standin(id) {
        return new Proxy(
            {},
            {
                get(target, name) {
                    if (typeof name !== 'string') {
                        return undefined;
                    }
                    if (/^use(Layout|Insertion)?Effect$/.test(name)) {
                        return (effect) => {
                            effect();
                        };
                    }
                    if (name === 'useMemo') {
                        return (fn) => fn();
                    }
                    if (name === 'useState') {
                        return (init) => [typeof init === 'function' ? init() : init, () => {}];
                    }
                    if (name === 'useRef') {
                        return (init) => ({ current: init });
                    }
                    if (/^use/.test(name) || name === 'memo' || name === 'forwardRef') {
                        return (value) => value;
                    }
                    if (ensure(id)) {
                        return importNs(id)[name];
                    }
                    return undefined;
                },
            },
        );
    }

    function run(id) {
        if (cache[id]) {
            return cache[id];
        }
        const factory = registry[id];
        if (!factory) {
            const empty = { esm: {}, cjs: undefined, usedS: false };
            cache[id] = empty;
            return empty;
        }
        const esm = {};
        let usedS = false;
        const moduleObject = { exports: {} };
        const current = {};
        cache[id] = current;
        const context = {
            i(dependency) {
                return importNs(dependency);
            },
            r(dependency) {
                ensure(dependency);
                const dep = run(dependency);
                return dep.usedS ? dep.esm : dep.cjs;
            },
            s(list) {
                usedS = true;
                for (let k = 0; k < list.length; k += 3) {
                    esm[list[k]] = list[k + 2];
                }
            },
            v(value) {
                moduleObject.exports = value;
            },
            e: globalThis,
            g: globalThis,
            A(dependency) {
                return {
                    then() {
                        return importNs(dependency);
                    },
                };
            },
            n: undefined,
            L: '',
        };
        factory(context, moduleObject, moduleObject);
        current.esm = esm;
        current.cjs = moduleObject.exports;
        current.usedS = usedS;
        return current;
    }

    function importNs(id) {
        if (!registry[id]) {
            return standin(id);
        }
        const module = run(id);
        if (module.usedS) {
            return wrap(module.esm);
        }
        const namespace = wrap(module.cjs);
        if (namespace && namespace.default === undefined) {
            namespace.default = namespace;
        }
        return namespace;
    }

    globalThis.TURBOPACK = {
        push(entry) {
            for (let k = 1; k < entry.length; k += 2) {
                registry[entry[k]] = entry[k + 1];
            }
        },
    };

    for (let g = 0; g < groups.length; g++) {
        const group = groups[g];
        activeUrls = group.chunkUrls;
        activeIndex = group.chunkUrls.length - 1;
        if (!ensure(group.moduleId)) {
            continue;
        }
        const component = run(group.moduleId).esm.default;
        if (typeof component !== 'function') {
            continue;
        }
        const candidates = group.candidates;
        for (let i = 0; i < candidates.length; i++) {
            captured[0] = null;
            captured[1] = null;
            try {
                component(candidates[i]);
            } catch (error) {
                continue;
            }
            if (captured[0] && captured[1]) {
                return [captured[0], captured[1]];
            }
        }
    }
    return null;
})
