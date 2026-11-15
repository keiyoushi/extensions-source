// Runs in a world of its own: reads the page while it is ready and posts what it sees, one read at a time.
(bridge, read) => {
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
