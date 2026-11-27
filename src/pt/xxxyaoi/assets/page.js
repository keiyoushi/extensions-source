(read) => {
    const finished = [];
    new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
            if (entry.name.startsWith(location.origin)) finished.push({ url: entry.name, end: entry.responseEnd });
        }
    }).observe({ type: 'resource', buffered: true });

    return () => {
        if ([...document.scripts].some((script) => script.textContent.includes('_cf_chl_opt'))) return { check: true };
        if (document.readyState === 'loading') return null;
        const status = performance.getEntriesByType('navigation')[0]?.responseStatus ?? 0;
        if (status >= 400) return { status, title: document.title };
        const result = read();
        if (result !== null) {
            const now = performance.now();
            return { result, finished: finished.map(({ url, end }) => ({ url, ago: Math.round(now - end) })) };
        }
        const widget = [...document.querySelectorAll('iframe[src*="challenges.cloudflare.com"]')].find(
            (frame) => frame.getBoundingClientRect().height > 0,
        );
        const pending = [...document.querySelectorAll('[name="cf-turnstile-response"]')].some((input) => !input.value);
        return widget || pending ? { check: true } : null;
    };
}
