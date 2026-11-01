(channelName) => {
    const channel = new BroadcastChannel(channelName);
    let urls = [];
    // Match decoded images to fetches by size: ciphertext minus the 28-byte AES-GCM nonce and tag, or equal
    const lengths = new Map();
    const images = [];

    const match = () => {
        for (let i = images.length - 1; i >= 0; i--) {
            for (const [index, length] of lengths) {
                const overhead = length - images[i].size;
                if (overhead !== 28 && overhead !== 0) continue;
                lengths.delete(index);
                channel.postMessage({ index, image: images[i] });
                images.splice(i, 1);
                break;
            }
        }
        if (images.length > 16) images.shift();
    };

    self.addEventListener("message", (event) => {
        const data = event.data;
        if (data && data.type === "store" && Array.isArray(data.urls)) {
            urls = data.urls;
            channel.postMessage({ count: urls.length });
        }
    }, true);

    Response.prototype.arrayBuffer = new Proxy(Response.prototype.arrayBuffer, {
        apply(target, response, args) {
            const result = Reflect.apply(target, response, args);
            const index = urls.indexOf(response.url);
            if (index >= 0) {
                result.then((buffer) => {
                    lengths.set(index, buffer.byteLength);
                    match();
                }, () => {});
            }
            return result;
        },
    });

    self.createImageBitmap = new Proxy(self.createImageBitmap, {
        apply(target, thisArg, args) {
            if (args[0] instanceof Blob) {
                images.push(args[0]);
                match();
            }
            return Reflect.apply(target, thisArg, args);
        },
    });
}
