(() => {
    const customProps = (() => {
        const iframe = document.createElement('iframe');
        document.body.appendChild(iframe);

        const standardProps = new Set(Object.getOwnPropertyNames(iframe.contentWindow));
        const currentProps = new Set(Object.getOwnPropertyNames(window));
        const customProps = new Set([...currentProps].filter(el => !standardProps.has(el)));

        document.body.removeChild(iframe);
        return customProps;
    })();

    const multiMaybeUrls = [];
    const multiMaybeFn = [];

    customProps.forEach(k => {
        const obj = window[k];
        if (
            Array.isArray(obj) &&
            obj.length !== 0 &&
            obj.every(el => typeof el === 'string') &&
            obj.every(el => el.length > 8)
        ) {
            multiMaybeUrls.push(obj);
        } else if (
            typeof obj === 'function' &&
            obj.length >= 1
        ) {
            multiMaybeFn.push(obj);
        }
    });

    const isValidUrl = (maybeUrl) => {
        try {
            new URL(maybeUrl);
            return true;
        } catch (_) {
            return false;
        }
    }

    const results = [];

    multiMaybeUrls.forEach(maybeUrls => {
        multiMaybeFn.forEach(maybeFn => {
            const maybeFnArgCount = maybeFn.length;

            for (let i = 0; i < maybeFnArgCount; i++) {
                try {
                    const maybeUrlsDecoded = maybeUrls.map(el => {
                        const args = new Array(maybeFnArgCount).fill(undefined);
                        args[i] = el;
                        return maybeFn(...args);
                    });

                    if (
                        Array.isArray(maybeUrlsDecoded) &&
                        maybeUrlsDecoded.length !== 0 &&
                        maybeUrlsDecoded.every(el => typeof el === 'string') &&
                        maybeUrlsDecoded.every(isValidUrl)
                    ) {
                        results.push(maybeUrlsDecoded);
                    }
                } catch (_) {}
            }
        });
    });

    const getPriority = (url) => {
        if (url.includes('/pw/')) return 4;
        if (url.includes('?rhlupa=')) return 3;
        if (url.includes('?')) return 2;
        if (url.endsWith('.jpg')) return 1;
        if (url.endsWith('s0')) return -1;
        return 0;
    };

    const resultsUnique = [...new Set(results.map(JSON.stringify))].map(JSON.parse);
    const resultsSorted = [...resultsUnique].sort((a, b) => getPriority(b[0]) - getPriority(a[0]));
    return resultsSorted;
})();
