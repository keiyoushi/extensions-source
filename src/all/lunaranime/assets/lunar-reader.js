(function (slug, chapter, lang) {
    const SKIP = new Set(['return', 'child', 'sibling', 'alternate', '_owner', 'stateNode', 'ref']);
    const MAX_DEPTH = 5;

    function isThisChapter(value, withSlug) {
        const fields = Object.values(value).map(String);
        return fields.includes(chapter) && fields.includes(lang) && (!withSlug || fields.includes(slug));
    }

    function isPageList(value) {
        if (!Array.isArray(value.images) || value.images.length === 0) return false;
        if (!value.images.every((image) => image && typeof image.url === 'string')) return false;
        if (value.images.every((image) => image.url.includes('/api/cdn/p/'))) return false;
        return isThisChapter(value, true);
    }

    function isPointsLock(value) {
        return typeof value.price === 'number' && 'balance' in value && isThisChapter(value, false);
    }

    function isDenial(value) {
        return typeof value.reason === 'string' && typeof value.message === 'string';
    }

    function find(root) {
        const found = {};
        const seen = new Map();

        function read(value, key) {
            try {
                return value[key];
            } catch {
                return undefined;
            }
        }

        function scan(value, depth) {
            if (!value || typeof value !== 'object' || depth > MAX_DEPTH || seen.get(value) <= depth) return;
            seen.set(value, depth);
            // Other frames' windows throw on access
            let keys;
            try {
                if (value instanceof Node || value.$$typeof) return;
                if (!Array.isArray(value)) {
                    if (!found.pages && isPageList(value)) found.pages = value.images;
                    if (!found.lock && isPointsLock(value)) found.lock = value;
                    if (!found.denial && isDenial(value)) found.denial = value;
                }
                keys = Object.keys(value);
            } catch {
                return;
            }
            for (const key of keys) {
                if (!SKIP.has(key)) scan(read(value, key), depth + 1);
            }
        }

        const fibers = [root];
        while (fibers.length && !found.pages) {
            const fiber = fibers.pop();
            scan(fiber.memoizedProps, 0);
            for (let hook = fiber.memoizedState; hook && typeof hook === 'object' && 'next' in hook; hook = hook.next) {
                scan(hook.memoizedState, 0);
            }
            if (fiber.sibling) fibers.push(fiber.sibling);
            if (fiber.child) fibers.push(fiber.child);
        }
        return found;
    }

    const key = Object.keys(document.body).find((name) => name.startsWith('__reactFiber$'));
    if (!key) return { check: false };
    let root = document.body[key];
    while (root.return) root = root.return;

    const found = find(root);
    if (found.pages) {
        return { pages: found.pages.map((image) => ({ url: image.url, veil: typeof image.veil === 'string' ? image.veil : null })) };
    }
    if (found.lock) return { price: found.lock.price, balance: found.lock.balance };
    if (found.denial) return { reason: found.denial.reason, message: found.denial.message };

    const visible = (selector) =>
        [...document.querySelectorAll(selector)].some((widget) => (widget.parentElement || widget).getBoundingClientRect().height > 0);
    const puzzle = 'iframe[src*="hcaptcha"][src*="frame=challenge"]';
    const puzzleOpen = [...document.querySelectorAll(puzzle)].some((frame) => frame.getBoundingClientRect().height > 0);
    const turnstile = visible('input[name="cf-turnstile-response"]');
    return {
        check: turnstile || puzzleOpen || visible('textarea[name="h-captcha-response"], iframe[src*="hcaptcha"]'),
        turnstile,
        area: puzzleOpen ? puzzle : null,
    };
})
