(bridge, read) => {
    const channel = window[bridge];
    let waiting = false;
    channel.onmessage = () => {
        waiting = false;
    };
    setInterval(() => {
        if (waiting) return;
        let snapshot;
        try {
            snapshot = read();
        } catch {
            return;
        }
        if (snapshot === null) return;
        waiting = true;
        channel.postMessage(JSON.stringify(snapshot));
    }, 200);
}
