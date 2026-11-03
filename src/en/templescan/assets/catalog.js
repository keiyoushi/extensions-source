// The comics page's cards for each requested status in the requested order, then the site search's results for a query.
(args, state) => {
    const selects = Array.from(document.querySelectorAll('select'));
    if (location.pathname !== '/comics' || selects.length === 0) return null;
    // Changes made before React has hydrated a select are lost
    if (!selects.every(s => Object.keys(s).some(k => k.startsWith('__reactProps$')))) return null;

    const button = label => Array.from(document.querySelectorAll('button')).find(b => label.test(b.textContent.trim()));
    state.cards = state.cards || [];

    // The site lists one status at a time
    if (state.cards.length < args.statuses.length) {
        let changed = false;
        for (const value of [args.statuses[state.cards.length], args.order, args.direction]) {
            const select = selects.find(s => Array.from(s.options).some(o => o.value === value));
            if (!select) return { error: 'The site no longer has the filter option ' + value };
            if (select.value === value) continue;
            Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set.call(select, value);
            select.dispatchEvent(new Event('change', { bubbles: true }));
            changed = true;
        }
        if (changed || state.loading) return null;

        // Each click shows 10 more cards; click again as soon as the page has drawn them
        if (button(/^load more$/i)) {
            state.loading = true;
            const loadMore = () => {
                const next = button(/^load more$/i);
                if (!next) return (state.loading = false);
                next.click();
                requestAnimationFrame(loadMore);
            };
            loadMore();
            return null;
        }
        state.cards.push(document.querySelector('main').outerHTML);
        return null;
    }
    if (!args.query) return { cards: state.cards };

    const input = Array.from(document.querySelectorAll('input[type=search]')).find(i => i.offsetParent);
    if (!input) return null;
    if (!state.typed) {
        input.focus();
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, args.query);
        input.dispatchEvent(new Event('input', { bubbles: true }));
        state.typed = true;
        return null;
    }

    const text = document.body.innerText;
    if (/No results for/.test(text)) return { cards: state.cards, results: '' };
    if (/Search unavailable/.test(text)) return { error: 'The site search is unavailable, try again later' };
    const results = document.querySelector('[role=listbox]');
    if (!results) return null;
    // "Showing 10 of 94" until every result is listed
    const shown = /Showing (\d+) of (\d+)/.exec(text);
    if (shown && Number(shown[1]) < Number(shown[2])) {
        button(/^show more results$/i)?.click();
        return null;
    }
    return { cards: state.cards, results: results.outerHTML };
}
