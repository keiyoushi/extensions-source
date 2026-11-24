(bridge, read) => {
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
