let pageCount = 0;
const ImgToBlobBase64 = async (i, e) => {
    const canvas = OffscreenCanvas && OffscreenCanvas.prototype.convertToBlob
        ? new OffscreenCanvas(e.naturalWidth, e.naturalHeight)
        : document.createElement("canvas");
    canvas.width = e.naturalWidth;
    canvas.height = e.naturalHeight;
    const canvasCtx = canvas.getContext("2d");
    canvasCtx.drawImage(e, 0, 0);
    if (canvas instanceof OffscreenCanvas) {
        const blob = await canvas.convertToBlob();
        const reader = new FileReader();
        reader.onloadend = () => window.__interface__.setPage(i, reader.result);
        reader.readAsDataURL(blob);
    } else {
        const dataUrl = canvas.toDataURL();
        window.__interface__.setPage(i, dataUrl);
        canvas.remove();
    }
};
function waitForElm(selector) {
    return new Promise(resolve => {
        if (document.querySelector(selector)) {
            return resolve(document.querySelector(selector));
        }
        const observer = new MutationObserver(() => {
            if (document.querySelector(selector)) {
                observer.disconnect();
                resolve(document.querySelector(selector));
            }
        });
        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
    });
}
let currentPageIndex = 0;
let scrollQueue = Promise.resolve();
function scrollIntoPage(targetPageIndex) {
    scrollQueue = scrollQueue.then(() => {
        return new Promise(resolve => {
            const images = document.querySelectorAll("div.mh_comicpic img");
            if (targetPageIndex !== currentPageIndex) {
                const step = targetPageIndex > currentPageIndex ? 1 : -1;
                let delay = 0;
                for (let i = currentPageIndex; i !== targetPageIndex; i += step) {
                    delay += 5000;
                    setTimeout(() => {
                        images[i + step].scrollIntoView();
                        if (i + step === targetPageIndex) {
                            currentPageIndex = targetPageIndex;
                            resolve();
                        }
                    }, delay);
                }
            } else {
                images[targetPageIndex].scrollIntoView();
                currentPageIndex = targetPageIndex;
                resolve();
            }
        });
    });
}
function reloadPic(pageIndex) {
    __cr.reloadPic(document.querySelectorAll("#mangalist"), pageIndex + 1)
}
waitForElm('div.mh_comicpic img').then(() => {
    pageCount = parseInt($.cookie(__cad.getCookieValue()[1] + mh_info.pageid) || "0");
    window.__interface__.setPageCount(pageCount);
});
const observer = new MutationObserver(() => {
    if (document.querySelector("div.mh_comicpic img")) {
        const images = document.querySelectorAll("div.mh_comicpic img");
        for (let i = 0; i < images.length; i++) {
            const img = images[i];
            if (!img._onloadHijacked) {
                const originalOnload = img.onload;
                img.onload = async function (event) {
                    if (originalOnload) originalOnload.call(this, event);
                    ImgToBlobBase64(i, this);
                };
                img._onloadHijacked = true;
            }
        }
        if (images.length >= pageCount) {
            observer.disconnect();
        }
    }
});
observer.observe(document.body, {
    childList: true,
    subtree: true
});
