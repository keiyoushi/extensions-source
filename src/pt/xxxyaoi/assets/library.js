(replaced) => {
    const isCard = (node) =>
        node.nodeType === Node.ELEMENT_NODE && (node.matches('a[href*="/bl/"]') || node.querySelector('a[href*="/bl/"]'));
    let parsed = false;
    let changedAt = 0;
    let replacedAt = 0;
    new MutationObserver((records) => {
        if (!records.some((record) => [...record.addedNodes, ...record.removedNodes].some(isCard))) return;
        changedAt = Date.now();
        if (parsed) replacedAt = changedAt;
    }).observe(document, { childList: true, subtree: true });
    document.addEventListener('DOMContentLoaded', () => {
        parsed = true;
    });

    return () => {
        if (document.readyState !== 'complete' || Date.now() - changedAt < 400) return null;
        if (replaced && !replacedAt) return null;
        return { url: location.href, html: document.documentElement.outerHTML };
    };
}
