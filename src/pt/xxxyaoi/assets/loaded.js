() => {
    let completeAt = 0;
    return () => {
        if (document.readyState !== 'complete') return null;
        if ([...document.querySelectorAll('li > a[href^="#"]')].some((link) => link.querySelector('header'))) return null;
        completeAt = completeAt || Date.now();
        if (Date.now() - completeAt < 300) return null;
        return { url: location.href, html: document.documentElement.outerHTML };
    };
}
