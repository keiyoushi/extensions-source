(() => {
    try {
        const origPost = self.postMessage.bind(self);
        const channel = new BroadcastChannel("moe-imgx-pages");
        self.postMessage = (data, transfer) => {
            try {
                if (data && data.type === "PAGE_READY" && data.bitmap) {
                    const bitmap = data.bitmap;
                    const width = Number(bitmap.width || 0);
                    const height = Number(bitmap.height || 0);
                    if (width > 0 && height > 0) {
                        const canvas = new OffscreenCanvas(width, height);
                        const context = canvas.getContext("2d");
                        if (context) {
                            context.drawImage(bitmap, 0, 0);
                            canvas.convertToBlob({ type: "image/webp", quality: 0.9 })
                                .then((blob) => blob.arrayBuffer())
                                .then((buffer) => {
                                    channel.postMessage(
                                        {
                                            type: "page",
                                            pageIndex: Number(data.pageIndex),
                                            mime: "image/webp",
                                            buffer,
                                        },
                                        [buffer],
                                    );
                                })
                                .catch(() => {});
                        }
                    }
                }
            } catch (_) {
            }
            return origPost(data, transfer);
        };
    } catch (_) {
    }
})();
