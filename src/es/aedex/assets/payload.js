// Reads the page's server-rendered data: the flight stream its inline scripts push, joined back together.
() => () => {
    if (!document.querySelector('a[href^="/"]')) return null;
    let stream = '';
    for (const script of document.querySelectorAll('script:not([src])')) {
        const text = script.textContent;
        if (!text.startsWith('self.__next_f.push(')) continue;
        const [kind, chunk] = JSON.parse(text.slice(text.indexOf('(') + 1, text.lastIndexOf(')')));
        if (kind === 1) stream += chunk;
    }
    return stream ? { stream } : null;
}
