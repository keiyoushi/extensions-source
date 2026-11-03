// Runs a page script on the site's own page, or reports the site's challenge or the page's error status instead.
(script, args, stateName) => {
    // Cloudflare's challenge page comes with a 403
    if (location.pathname === '/challenge' || window._cf_chl_opt) return JSON.stringify({ challenge: true });
    const status = performance.getEntriesByType('navigation')[0]?.responseStatus || 200;
    if (status >= 400) return JSON.stringify({ status });
    window[stateName] = window[stateName] || {};
    return JSON.stringify(script(args, window[stateName]));
}
