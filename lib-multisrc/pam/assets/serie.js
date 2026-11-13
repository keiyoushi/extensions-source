// What the series page shows right now: its details, the open tab, the chapter rows on screen and where its controls are.
() => {
    const main = document.querySelector('main');
    const h1 = main && main.querySelector('h1');
    if (!h1) return null;

    const text = (el) => (el ? el.textContent.trim() : '');
    // A control's centre, and whether a tap there reaches it rather than an ad or a toolbar over it
    const at = (el) => {
        const rect = el.getBoundingClientRect();
        const x = rect.left + rect.width / 2;
        const y = rect.top + rect.height / 2;
        return { x, y, clear: y > 0 && y < innerHeight && el.contains(document.elementFromPoint(x, y)) };
    };
    // Labels in the site's two languages
    const labels = {
        altTitles: ['Alternative titles', 'Titres alternatifs'],
        author: ['Author', 'Auteur'],
        artist: ['Artist', 'Artiste', 'Dessinateur'],
        year: ['Year', 'Année'],
        rating: ['Rating', 'Note'],
        chapters: ['Chapters', 'Chapitres'],
        accountRequired: ['Account required', 'Compte requis'],
    };
    const fields = [...main.querySelectorAll('div > span:first-child + span')];
    const field = (names) => fields.find((value) => names.includes(text(value.previousElementSibling)));
    const links = (el, selector) => (el ? [...new Set([...el.querySelectorAll(selector)].map(text).filter(Boolean))] : []);

    // Synopsis and Information tabs, above the open panel
    const tabs = [...main.querySelectorAll('div > button:first-child + button:last-child')].find((button) =>
        button.parentElement.nextElementSibling?.matches('p, div'),
    );
    const panel = tabs?.parentElement.nextElementSibling;
    const status = panel?.querySelector('a[href*="status="]');
    const altTitles = field(labels.altTitles);
    const info = status
        ? {
              altTitles: altTitles ? [...altTitles.children].filter((span) => !span.ariaHidden).map(text) : [],
              author: links(field(labels.author), 'a'),
              artist: links(field(labels.artist), 'a'),
              year: text(field(labels.year)) || null,
              status: new URL(status.href).searchParams.get('status'),
              genres: links(panel, 'a[href*="include_genres="]'),
          }
        : null;

    // Chapter rows: links holding a name block and a views/age block
    const rows = [...main.querySelectorAll('a[href*="/chapter/"]')].filter(
        (row) => row.children.length >= 2 && row.lastElementChild.children.length >= 2,
    );
    let current = null;
    for (let el = rows[0]?.parentElement; el && el !== main && !current; el = el.parentElement) {
        current = el.querySelector('button[aria-current="page"]');
    }
    const page = current ? Number(text(current)) : 1;
    let pager = current;
    while (pager && pager !== main && pager.querySelectorAll('button').length < 2) pager = pager.parentElement;
    const next = pager && [...pager.querySelectorAll('button')].find((button) => text(button) === String(page + 1));

    return {
        title: text(h1),
        cover:
            [...main.querySelectorAll('img')].find((img) => img.alt === text(h1))?.src ??
            main.querySelector('video[poster]')?.poster ??
            null,
        synopsis: panel?.matches('p') ? panel.innerText.trim() : null,
        info,
        infoTab: tabs && !info ? at(tabs) : null,
        types: links(main, 'a[href*="include_types="]'),
        teams: links(main, 'a[href^="/team/"]'),
        rating: text(field(labels.rating)) || null,
        chapterCount: field(labels.chapters) ? Number(text(field(labels.chapters)).replace(/\D/g, '')) : null,
        page,
        chapters: rows.map((row) => {
            const parts = row.children;
            const nameBox = parts[parts.length - 2];
            return {
                path: row.getAttribute('href'),
                name: text(nameBox.querySelector('div > span')),
                title: text(nameBox.querySelector('span[title]')) || null,
                premium: Boolean(row.querySelector('[style*="--premium"]')),
                account: [...row.querySelectorAll('span')].some((span) => labels.accountRequired.includes(text(span))),
                age: text(parts[parts.length - 1].lastElementChild) || null,
            };
        }),
        next: next ? at(next) : null,
        viewport: { width: innerWidth, height: innerHeight },
        scale: devicePixelRatio,
    };
}
