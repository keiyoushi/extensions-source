// Answers the site's rotate captcha, then posts the solved message the site's own captcha page sends
// Rotation search ported from https://github.com/nekomangaorg/Neko (ComixWafSolver)
(reject) => {
    const GRID = 128;

    const loadImage = (src) => new Promise((resolve, fail) => {
        const image = new Image();
        image.onload = () => resolve(image);
        image.onerror = () => fail(new Error("Could not decode the captcha image"));
        image.src = src;
    });

    const pixels = (image, offset, size) => {
        const canvas = document.createElement("canvas");
        canvas.width = image.width;
        canvas.height = image.height;
        const context = canvas.getContext("2d");
        context.drawImage(image, 0, 0);
        return context.getImageData(offset, offset, size, size).data;
    };

    const gray = (data, i) => (data[i * 4] + data[i * 4 + 1] + data[i * 4 + 2]) / 3;

    const angle = (image, piece) => {
        const size = piece.width;
        const reference = pixels(image, Math.floor((image.width - size) / 2), size);
        const rotated = pixels(piece, 0, size);
        const center = (size - 1) / 2;
        const scale = size / GRID;
        let bestAngle = 0;
        let bestScore = Infinity;
        for (let degrees = 0; degrees < 360; degrees++) {
            const rad = (degrees * Math.PI) / 180;
            const cos = Math.cos(rad);
            const sin = Math.sin(rad);
            let sum = 0;
            let valid = 0;
            for (let i = 0; i < GRID * GRID; i++) {
                const gx = (i % GRID) * scale;
                const gy = Math.floor(i / GRID) * scale;
                const vx = gx - center;
                const vy = gy - center;
                const x = center + vx * cos + vy * sin;
                const y = center - vx * sin + vy * cos;
                if (x < 0 || x >= size || y < 0 || y >= size) continue;
                const index = Math.floor(y) * size + Math.floor(x);
                if (rotated[index * 4 + 3] <= 128) continue;
                const diff = gray(reference, Math.floor(gy) * size + Math.floor(gx)) - gray(rotated, index);
                sum += diff * diff;
                valid++;
            }
            if (valid >= 100 && sum / valid < bestScore) {
                bestScore = sum / valid;
                bestAngle = degrees;
            }
        }
        return bestAngle;
    };

    const json = async (response) => {
        if (!response.ok) throw new Error(`HTTP error ${response.status}`);
        return response.json();
    };

    const attempt = async () => {
        const challenge = await json(await fetch("/@waf/generate", { headers: { Accept: "application/json" } }));
        const [image, piece] = await Promise.all([loadImage(challenge.image_base64), loadImage(challenge.thumb_base64)]);
        const response = await fetch("/@waf/verify", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ captcha_id: challenge.captcha_id, angle: angle(image, piece) }),
        });
        return (await json(response)).success === true;
    };

    (async () => {
        for (let i = 0; i < 3; i++) {
            if (await attempt()) {
                parent.postMessage({ type: "waf-captcha-solved" }, location.origin);
                return;
            }
        }
        reject("Failed to solve the captcha, open in WebView to solve it");
    })().catch((error) => reject(`Failed to solve the captcha: ${error.message}`));
}
