// Reads the series list the page fetched for its url, from the site's own response cache. With [genres] (names), ticks
// them in the site's filter panel first, since only the panel knows their ids, and reports the id each one got.
(query, genres) => {
    const ids = {};
    // Ticks whose ids haven't reached the url yet, in tick order, and when the last one was made
    let waiting = [];
    let tickedAt = 0;
    // Genres already in the page url when it opened
    const preset = new URLSearchParams(location.search).get('genre')?.split('|') ?? [];

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
        // The site's button labelled with this text (a count may follow), preferring the copy a tap would reach
        const button = (text) => {
            const spots = [...document.querySelectorAll('button')]
                .filter((el) => visible(el) && el.textContent.trim().startsWith(text))
                .map((el) => point(el));
            return spots.find((spot) => spot.clear) ?? spots[0];
        };
        // A tap on a panel option; one further down the panel's own list is swiped to inside that list
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
            // Ticks whose ids never reached the url (a full-page ad swallowed the change) are undone and made again
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
                // Checked without its id in the url: undone first
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

        const wanted = applied ? { ...query, genre: applied } : query;
        for (let i = 0; i < sessionStorage.length; i++) {
            const key = sessionStorage.key(i);
            if (!key.startsWith('catha_swr_mangas_')) continue;
            const params = new URLSearchParams(key.slice('catha_swr_mangas_'.length));
            const names = [...params.keys()];
            if (names.length !== Object.keys(wanted).length || names.some((name) => params.get(name) !== wanted[name])) continue;
            return { ...base, list: JSON.parse(sessionStorage.getItem(key)).data, ids };
        }
        return null;
    };
}
