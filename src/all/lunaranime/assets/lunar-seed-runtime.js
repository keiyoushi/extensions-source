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
                if (!xhr.responseText.startsWith('(globalThis.TURBOPACK')) {
                    continue;
                }
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
            let ids = [];
            for (let k = 1; k < entry.length; k++) {
                if (typeof entry[k] === 'function') {
                    ids.forEach((id) => {
                        registry[id] = entry[k];
                    });
                    ids = [];
                } else {
                    ids.push(entry[k]);
                }
            }
        },
    };

    function findSeeds() {
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
    }

    function findModule(test, accept) {
        activeUrls = [].concat(...groups.map((group) => group.chunkUrls));
        activeIndex = activeUrls.length - 1;
        const tried = new Set();
        for (;;) {
            const ids = Object.keys(registry);
            for (let k = 0; k < ids.length; k++) {
                if (!tried.has(ids[k]) && test(String(registry[ids[k]]))) {
                    tried.add(ids[k]);
                    const value = accept(ids[k]);
                    if (value) {
                        return value;
                    }
                }
            }
            if (!loadNext()) {
                return null;
            }
        }
    }

    function renames(fn) {
        const pattern = /\(\s*[\w$]+\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)/g;
        return [...String(fn).matchAll(pattern)].map((match) => [match[1], match[2]]);
    }

    function readNames(id) {
        const exports = run(id).esm;
        const headers = [];
        const steps = [];
        let outer = null;
        let inner = null;
        Object.keys(exports).forEach((key) => {
            const value = exports[key];
            if (typeof value === 'string' && /^[A-Za-z0-9-]+$/.test(value)) {
                headers.push(value);
            } else if (typeof value === 'number') {
                steps.push(value);
            } else if (typeof value === 'function' && String(value).includes('"session_data"')) {
                outer = renames(value);
            } else if (typeof value === 'function' && String(value).includes('"cache_status"')) {
                inner = renames(value);
            }
        });
        return headers.length === 3 && steps.length === 1 && outer && inner ? { headers, mintStep: steps[0], outer, inner } : null;
    }

    function readHeaderRoles(id, headers) {
        const probe = 'lunar-header-probe';
        let session;
        try {
            session = importNs(id);
        } catch (error) {
            return null;
        }
        const exported = Object.keys(session).map((key) => session[key]);
        let token = null;
        Object.keys(session).forEach((key) => {
            const setter = session[key];
            if (token || typeof setter !== 'function' || setter.length !== 2) {
                return;
            }
            let result;
            try {
                result = setter({}, probe);
            } catch (error) {
                return;
            }
            if (result && typeof result === 'object') {
                token = Object.keys(result).find((name) => result[name] === probe && headers.includes(name)) || null;
            }
        });
        const type = headers.find((name) => name !== token && exported.includes(name));
        if (!token || !type) {
            return null;
        }
        return { token, type, proof: headers.find((name) => name !== token && name !== type) };
    }

    function findNames() {
        const names = findModule(
            (source) => source.includes('"session_data"') && source.includes('"cache_status"') && source.includes('hasOwnProperty'),
            readNames,
        );
        if (!names) {
            return null;
        }
        const roles = findModule(
            (source) => source.includes('/api/captcha/config'),
            (id) => readHeaderRoles(id, names.headers),
        );
        if (!roles) {
            return null;
        }
        return { ...roles, mintStep: names.mintStep, outer: names.outer, inner: names.inner };
    }

    const seeds = findSeeds();
    if (!seeds) {
        return null;
    }
    const names = findNames();
    if (!names) {
        return null;
    }
    return { axis: seeds[0], pitch: seeds[1], names };
})
