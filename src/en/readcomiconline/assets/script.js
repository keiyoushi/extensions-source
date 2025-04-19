(() => {
    const isValidUrl = (str) => {
        try {
            new URL(str);
            return true;
        } catch (e) {
            return false;
        }
    }

    const arrays = [];
    const functions = [];
    const results = [];

    const iframe = document.createElement('iframe');
    document.body.appendChild(iframe);
    const builtInKeys = new Set(Object.getOwnPropertyNames(iframe.contentWindow));
    document.body.removeChild(iframe);

    for (const [key, value] of Object.entries(window)) {
        if (builtInKeys.has(key)) continue;

        if (Array.isArray(value)) {
            arrays.push(value);
        } else if (typeof value === 'function' && value.length >= 1) {
            functions.push(value);
        }
    }

    arrays.forEach(arrayValue => {
        functions.forEach(funcValue => {
            const argCount = funcValue.length;

            for (let i = 0; i < argCount; i++) {
                try {
                    const mapped = arrayValue.map(elem => {
                        const args = new Array(argCount).fill(undefined);
                        args[i] = elem;
                        return funcValue(...args);
                    });

                    if (
                        Array.isArray(mapped) &&
                        mapped.length !== 0 &&
                        mapped.every(item => typeof item === 'string' && isValidUrl(item))
                    ) {
                        results.push(mapped);
                        break;
                    }
                } catch (err) {}
            }
        });
    });

    return results;
})();
