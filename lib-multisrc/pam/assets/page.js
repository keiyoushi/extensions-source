// Runs a page reader once the page has loaded and isn't a challenge or an error page.
(read) => {
    // Cloudflare's challenge page sets up its options in an inline script
    if ([...document.scripts].some((script) => script.textContent.includes('_cf_chl_opt'))) return { challenge: true };
    if (document.readyState !== 'complete') return null;
    const status = performance.getEntriesByType('navigation')[0]?.responseStatus ?? 0;
    if (status >= 400) return { status };
    const result = read();
    return result === null ? null : { result };
}
