// Runs in a world of its own: reads the page while it is ready and posts what it sees, one read at a time.
(bridge, read) => {
    // The site's service worker fetches pages with WebView's own user agent, which the site refuses: a page it served
    // loads again from the network, and the worker the page registers is dropped before the next page
    const workers = navigator.serviceWorker;
    const dropWorkers = () => workers?.getRegistrations().then((all) => Promise.all(all.map((worker) => worker.unregister())));
    if (workers?.controller) {
        dropWorkers().finally(() => location.reload());
        return;
    }
    setInterval(dropWorkers, 1000);
    const channel = window[bridge];
    let postedAt = 0;
    channel.onmessage = () => {
        postedAt = 0;
    };
    setInterval(() => {
        // A read whose answer never came (the app was busy, or dropped it) is posted again
        if (postedAt && Date.now() - postedAt < 5000) return;
        let snapshot;
        try {
            snapshot = read();
        } catch {
            return;
        }
        if (snapshot === null) return;
        postedAt = Date.now();
        channel.postMessage(JSON.stringify(snapshot));
    }, 150);
}
