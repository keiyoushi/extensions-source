() => () => {
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
    const field = [...document.querySelectorAll('label')].find(
        (label) => /^g[ée]neros$/i.test(label.textContent.trim()) && onScreen(label),
    );
    if (!field) {
        const open = button('Filtros');
        return open ? { ...base, tap: open } : null;
    }
    const options = [...field.parentElement.querySelectorAll('label')].filter((label) => label.querySelector('input[type=checkbox]'));
    if (!options.length) return { ...base, tap: point(field.parentElement.querySelector('button')) };
    // Ids the site's cached series lists show for their genres
    const ids = {};
    for (let i = 0; i < sessionStorage.length; i++) {
        const key = sessionStorage.key(i);
        if (!key.startsWith('catha_swr_mangas_')) continue;
        for (const series of JSON.parse(sessionStorage.getItem(key)).data?.data ?? []) {
            for (const genre of series.fk_generos ?? []) if (genre.generos_id) ids[genre.generos_id.nombre] = genre.generos_id.id;
        }
    }
    return { ...base, names: options.map((label) => label.textContent.trim()), ids };
}
