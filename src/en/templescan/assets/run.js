// Runs a page script on the site's own page, or reports Cloudflare's or the site's check or the page's error status instead.
(script, args, stateName) => {
    // Before the status: Cloudflare's challenge page comes with a 403
    if (window._cf_chl_opt || location.pathname === '/challenge') return JSON.stringify({ check: true });
    const status = performance.getEntriesByType('navigation')[0]?.responseStatus || 200;
    if (status >= 400) return JSON.stringify({ status });
    window[stateName] = window[stateName] || {};
    return JSON.stringify(script(args, window[stateName]));
}
