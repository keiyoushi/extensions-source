() => () => {
    const visible = (el) => el && el.getClientRects().length > 0;
    // The element's centre in CSS pixels, and whether it is the element found there
    const point = (el) => {
        const box = el.getBoundingClientRect();
        const x = box.left + box.width / 2;
        const y = box.top + box.height / 2;
        return { x, y, clear: y > 0 && y < innerHeight && el.contains(document.elementFromPoint(x, y)) };
    };
    const base = { scale: devicePixelRatio, height: innerHeight, width: innerWidth };

    const ageCheck = [...document.querySelectorAll('input[type=checkbox]')].find((box) => visible(box) && box.closest('.fixed'));
    if (ageCheck) {
        const dialog = ageCheck.closest('.fixed');
        const yes = [...dialog.querySelectorAll('button')].find(
            (button) => visible(button) && button.innerText.trim() !== 'No' && button.innerText.trim().length > 1,
        );
        if (!ageCheck.checked) return { ...base, tap: point(ageCheck.closest('div') ?? ageCheck) };
        if (yes) return { ...base, tap: point(yes) };
    }

    const title = [...document.querySelectorAll('h2')].find(visible);
    if (!title) return null;
    // The details box: the title's nearest ancestor that also holds the description's "show more" button
    let box = title;
    while (
        box &&
        ![...box.querySelectorAll('button')].some((button) => visible(button) && button.querySelector('span') && box.querySelector('p'))
    )
        box = box.parentElement;
    if (!box) return null;
    const alt = title.parentElement.nextElementSibling;
    const badges = alt?.nextElementSibling;
    // The synopsis: the longest paragraph of the details box
    const description = [...box.querySelectorAll('p')].sort((a, b) => b.textContent.length - a.textContent.length)[0];
    const cover = document.querySelector('img[fetchpriority=high]');
    const author = [...document.querySelectorAll('h3')].find(
        (h) => h.querySelector('span') && /Autor/.test(h.firstChild?.textContent ?? ''),
    );
    // The series id, from the page's own request for its rating (the page may be open by its slug)
    const id = performance
        .getEntriesByType('resource')
        .map((entry) => /\/api\/mangas\/valoracion\/(\d+)/.exec(entry.name)?.[1])
        .find(Boolean);

    // A chapter row: the smallest box around one heading that also holds the chapter's link
    const rows = [...document.querySelectorAll('h4')]
        .map((heading) => {
            let row = heading;
            while (row && !row.querySelector('a[href*="/chapter/"]')) row = row.parentElement;
            if (!row || row.querySelectorAll('h4').length !== 1) return null;
            const texts = heading.parentElement.querySelectorAll('p');
            return {
                href: row.querySelector('a[href*="/chapter/"]').getAttribute('href'),
                heading: heading.textContent.trim(),
                title: texts[0]?.textContent.trim() || null,
                age: texts[1]?.textContent.trim() || null,
                row,
            };
        })
        .filter((row) => row !== null);
    // The button under the list that shows the next chapters
    const list = rows[0]?.row.parentElement;
    const more = [...document.querySelectorAll('button')].find(
        (button) => visible(button) && /\(\d+/.test(button.innerText) && list && button.parentElement.parentElement.contains(list),
    );
    const rating = box.querySelector('[aria-label$=" de 5"]');

    return {
        ...base,
        id,
        title: title.textContent.trim(),
        altTitle: alt?.tagName === 'H3' ? alt.textContent.trim() : null,
        badges: badges ? [...badges.querySelectorAll('span')].map((span) => span.textContent.trim()) : [],
        description: description?.textContent.trim() || null,
        cover: cover?.getAttribute('src') ?? null,
        scans: [...document.querySelectorAll('a[href^="/scans/"]')].map((link) => link.innerText.trim()).filter(Boolean),
        author: author?.querySelector('span')?.textContent.trim() || null,
        rows: rows.map((entry) => ({ href: entry.href, heading: entry.heading, title: entry.title, age: entry.age })),
        more: more ? point(more) : null,
        // The site's star rating ("4.5 de 5") and the vote count beside it, when the series has votes
        rating: Number(/^([\d.]+) de 5$/.exec(rating?.getAttribute('aria-label') ?? '')?.[1] ?? NaN) || null,
        votes: Number(/\((\d+) valoraci/.exec(rating?.parentElement?.textContent ?? '')?.[1] ?? 0),
        // The series' chapter count, shown next to its rating
        count: Number(
            [...box.querySelectorAll('span')].map((span) => /^(\d+) cap[ií]tulos?$/i.exec(span.textContent.trim())?.[1]).find(Boolean) ??
                -1,
        ),
    };
}
