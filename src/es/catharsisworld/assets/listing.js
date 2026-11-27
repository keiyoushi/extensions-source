// [query]: the parameters of the list request the page sends for its url; [genres]: genre names, whose ids only the
// filter panel gives
(query, genres) => {
    const ids = {};
    // Ticks whose ids haven't reached the url yet, in tick order, and when the last one was made
    let waiting = [];
    let tickedAt = 0;
    // Genres already in the page url when it opened
    const preset = new URLSearchParams(location.search).get('genre')?.split('|') ?? [];
    // The page's answered list requests
    const requests = [];
    new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
            const url = new URL(entry.name);
            if (url.origin === location.origin && url.pathname === '/api/mangas') requests.push({ params: url.searchParams, entry });
        }
    }).observe({ type: 'resource', buffered: true });
    // The cards last read, and when they changed
    let shown = null;
    let shownAt = 0;

    return () => {
        const visible = (el) => el && el.getClientRects().length > 0;
        const onScreen = (el) => {
            const box = el.getBoundingClientRect();
            return box.height > 0 && box.bottom > 0 && box.top < innerHeight && box.right > 0 && box.left < innerWidth;
        };
        const point = (el) => {
            const box = el.getBoundingClientRect();
            const x = box.left + box.width / 2;
            const y = box.top + box.height / 2;
            return { x, y, clear: y > 0 && y < innerHeight && el.contains(document.elementFromPoint(x, y)) };
        };
        const base = { scale: devicePixelRatio, width: innerWidth, height: innerHeight };
        // The button labelled with this text (a count may follow); the page has one per layout
        const button = (text) => {
            const spots = [...document.querySelectorAll('button')]
                .filter((el) => visible(el) && el.textContent.trim().startsWith(text))
                .map((el) => point(el));
            return spots.find((spot) => spot.clear) ?? spots[0];
        };
        const reach = (option) => {
            const spot = point(option);
            let list = option.parentElement;
            while (
                list &&
                list !== document.body &&
                !(list.scrollHeight > list.clientHeight && /auto|scroll/.test(getComputedStyle(list).overflowY))
            ) {
                list = list.parentElement;
            }
            if (spot.clear || !list || list === document.body) return spot;
            const box = list.getBoundingClientRect();
            const top = Math.max(box.top, 0);
            const bottom = Math.min(box.bottom, innerHeight);
            return { ...spot, fromX: box.left + box.width / 2, fromY: (top + bottom) / 2 };
        };
        const applied = new URLSearchParams(location.search).get('genre');

        if (genres) {
            // Each tick adds its genre's id to the end of the page url
            const fresh = (applied?.split('|') ?? []).filter((id) => !preset.includes(id) && !Object.values(ids).includes(id));
            if (waiting.length && fresh.length === waiting.length) {
                waiting.forEach((name, i) => (ids[name] = fresh[i]));
                waiting = [];
            }
            if (waiting.length && Date.now() - tickedAt < 4000) return null;
            waiting = [];
            const field = [...document.querySelectorAll('label')].find(
                (label) => /^g[ée]neros$/i.test(label.textContent.trim()) && onScreen(label),
            );
            const pending = genres.filter((name) => !(name in ids));
            if (pending.length) {
                if (!field) {
                    const open = button('Filtros');
                    return open ? { ...base, tap: open } : null;
                }
                const options = [...field.parentElement.querySelectorAll('label')].filter((label) =>
                    label.querySelector('input[type=checkbox]'),
                );
                if (!options.length) return { ...base, tap: point(field.parentElement.querySelector('button')) };
                // A genre the panel doesn't offer is left out
                pending.filter((name) => !options.some((label) => label.textContent.trim() === name)).forEach((name) => (ids[name] = null));
                const offered = options.filter((label) => pending.includes(label.textContent.trim()));
                if (!offered.length) return null;
                const stale = offered.find((label) => label.querySelector('input').checked);
                const option = stale ?? offered[0];
                const spot = reach(option);
                if (spot.clear && !stale) {
                    waiting = [option.textContent.trim()];
                    tickedAt = Date.now();
                }
                return { ...base, tap: spot };
            }
        }

        // The cards show the request for the page's url once it is answered and they stop changing
        const wanted = applied ? { ...query, genre: applied } : query;
        const request = requests.findLast(({ params }) => Object.entries(wanted).every(([name, value]) => params.get(name) === value));
        if (!request) return null;
        if (request.entry.responseStatus >= 400) return { ...base, error: request.entry.responseStatus };
        const cards = [...document.querySelectorAll('a[href]')].filter(
            (card) => /^\/manga\/[^/]+\/?$/.test(new URL(card.href).pathname) && card.querySelector('h3'),
        );
        const seen = cards.map((card) => card.href).join();
        if (seen !== shown) {
            shown = seen;
            shownAt = performance.now();
        }
        if (performance.now() - Math.max(shownAt, request.entry.responseEnd) < 500) return null;
        const pages = [...document.querySelectorAll('button')].map((el) => el.textContent.trim()).filter((text) => /^\d+$/.test(text));
        return {
            ...base,
            cards: cards.map((card) => {
                const heading = card.querySelector('h3');
                const spans = [...card.querySelectorAll('span')];
                return {
                    id: new URL(card.href).pathname.split('/')[2],
                    title: heading.textContent.trim(),
                    cover: card.querySelector('img[src*="/assets/"]')?.getAttribute('src') ?? null,
                    // The badges on the cover, and the genres under the title
                    badges: spans.filter((span) => !heading.parentElement.contains(span)).map((span) => span.textContent.trim()),
                    genres: spans.filter((span) => heading.parentElement.contains(span)).map((span) => span.textContent.trim()),
                };
            }),
            next: Math.max(0, ...pages.map(Number)) > Number(wanted.page),
            ids,
        };
    };
}
