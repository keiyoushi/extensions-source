//__INJECT_WEBVIEW_INTERFACE = { reportImage: console.log }
const webviewInterface = __INJECT_WEBVIEW_INTERFACE;

const checkLoadedTimer = setInterval(() => {
    if (document.getElementById('current-progression')) {
        if (getSpine()?.childElementCount === getPageCount()) {
            webviewInterface.reportViewerLoaded(getPageCount());
            clearInterval(checkLoadedTimer);
            setupInterceptor();
        }
    }
}, 10);

async function waitMilliseconds(ms) {
    await new Promise(r => setTimeout(r, 100));
}

function getSpine() {
    return document.querySelector('#thorium-web-container [aria-label=Spine]');
}

function getPageCount() {
    return +document.getElementById('current-progression').innerText.split(' of ')[1];
}

function getCurrentPageIndex() {
    return +document.getElementById('current-progression').innerText.split(' of ')[0] - 1;
}

function getLastPageIndex() {
    return getPageCount() - 1;
}

const alreadyExtracted = [];

async function extractImage(pageIndex, retriesRemaining = 3) {
    if (alreadyExtracted[pageIndex]) {
        console.log('already extracted', pageIndex);
        return;
    }
    alreadyExtracted[pageIndex] = true;

    const iframe = getSpine().children[pageIndex].querySelector('iframe.readium-navigator-iframe.loaded');
    if (!iframe) {
        console.error(pageIndex, 'was not loaded!');
        return;
    }

    // A page is comprised of a watermarked image that forms the bulk of the content,
    // plus a separate smaller image rendered that covers over the watermark.
    // The second image is always in the top-left, which makes it simple to compose them.
    const images = iframe.contentDocument.querySelectorAll('img');
    const fullImage = images[0];

    if (!fullImage) {
        // maybe the iframe hasn't actually loaded yet. Let's try again in a bit.
        if (retriesRemaining > 0) {
            console.error(pageIndex, 'retrying...');
            await waitMilliseconds(100);
            return await extractImage(pageIndex, retriesRemaining - 1);
        }
        else {
            console.error(pageIndex, 'never loaded!');
            return;
        }
    }

    const topImage = images[1];

    if (fullImage.src.startsWith('https://')) {
        // This is a public URL which should be directly accessible rather than a blob.
        // Because of browser cross-origin security, we actually can't follow the normal path in
        // this case and need to send the URL up to the extension directly.
        console.log("sending image URL for", pageIndex);
        webviewInterface.reportImageURL(pageIndex, fullImage.src);
        return;
    }

    const width = fullImage.width;
    const height = fullImage.height;
    const canvas = new OffscreenCanvas(width, height);

    const ctx = canvas.getContext('2d');
    ctx.drawImage(fullImage, 0, 0);
    ctx.drawImage(topImage, 0, 0);

    // imageData should be a Uint8ClampedArray containing RGBA row-major bitmap image data.
    // We don't create the final JPEGs right here with Canvas.toBlob/OffscreenCanvas.convertToBlob
    // because in testing, that was _extremely_ slow to run in the Webview, taking upwards of
    // 15 seconds per image. Doing that conversion on the JVM side is near-instantaneous.
    const imageData = ctx.getImageData(0, 0, width, height).data;

    // The WebView interface only allows communicating with strings,
    // so we need to convert our ArrayBuffer to a string for transport.
    const textData = new TextDecoder("windows-1252").decode(imageData);
    console.log("sending encoded data for", pageIndex);
    webviewInterface.reportImage(pageIndex, textData, width, height);
}

function isIframeLoaded(iframe) {
    return iframe.classList.contains('loaded');
}

function getIframeFromContainer(container) {
    return container.querySelector('iframe.readium-navigator-iframe');
}

function createLoadObserver(idx) {
    return new MutationObserver((mutations, self) => {
        for (const mutation of mutations) {
            if (isIframeLoaded(mutation.target)) {
                console.log(idx, 'loaded');
                extractImage(idx);
                self.disconnect();
            }
        }
    });
}

function onIframeFound(idx, iframe) {
    if (isIframeLoaded(iframe)) {
        console.log(idx, 'was already loaded');
        extractImage(idx);
    }
    else {
        console.log(idx, 'attaching load observer');
        createLoadObserver(idx).observe(iframe, { attributes: true, attributeFilter: ['class'] });
    }
}

function createIframeObserver(idx) {
    return new MutationObserver((mutations, self) => {
        for (const mutation of mutations) {
            if (mutation.addedNodes.length >= 1) {
                const iframe = getIframeFromContainer(mutation.target);
                if (iframe) {
                    console.log(idx, 'iframe attached');
                    onIframeFound(idx, iframe);
                    self.disconnect();
                }
            }
        }
    });
}

function setupInterceptorForPage(idx, container = null) {
    container ??= getSpine().children[idx];
    const iframe = getIframeFromContainer(container);
    if (iframe) {
        console.log(idx, 'iframe was already attached');
        onIframeFound(idx, iframe);
    }
    else {
        createIframeObserver(idx).observe(container, { childList: true });
    }
}

function setupInterceptor() {
    [...getSpine().children].forEach((child, idx) => {
        setupInterceptorForPage(idx, child);
    });
}

// JS UTILITIES
//const leftArrowKeyCode = 'ArrowLeft';
//const rightArrowKeyCode = 'ArrowRight';
//
//function fireKeyboardEvent(elt, code) {
//    elt.dispatchEvent(new KeyboardEvent('keydown', { code }));
//}

window.__INJECT_JS_UTILITIES = {
    async fetchPageData(targetPageIndex) {
        if (alreadyExtracted[targetPageIndex]) {
            alreadyExtracted[targetPageIndex] = false;
            setupInterceptor(targetPageIndex);
        }

        const lastPageIndex = getLastPageIndex();

        const isLTR = Boolean(document.querySelector('.thorium_web_reader_paginatedArrow_rightContainer > button[aria-label=Next]'));

//        const [forwardsKeyCode, backwardsKeyCode] = isLTR
//            ? [rightArrowKeyCode, leftArrowKeyCode]
//            : [leftArrowKeyCode, rightArrowKeyCode];

        const leftButton = document.querySelector('.thorium_web_reader_paginatedArrow_leftContainer > button')
        const rightButton = document.querySelector('.thorium_web_reader_paginatedArrow_rightContainer > button')

        const [forwardsButton, backwardsButton] = isLTR
            ? [rightButton, leftButton]
            : [leftButton, rightButton];

        if (getCurrentPageIndex() === targetPageIndex) {
            // The image may have already loaded, but we need to shuffle around for it to get reported.
            // Otherwise, we can get stuck waiting for the image to be reported forever and
            // eventually time out.
            console.log('already at correct page');
//            if (targetPageIndex === lastPageIndex) {
//                fireKeyboardEvent(renderer, backwardsKeyCode);
//                fireKeyboardEvent(renderer, forwardsKeyCode);
//            }
//            else {
//                fireKeyboardEvent(renderer, forwardsKeyCode);
//                fireKeyboardEvent(renderer, backwardsKeyCode);
//            }
            return;
        }

        async function performUIAction(cb) {
            cb();
            await waitMilliseconds(0);
        }

        async function adjustPage() {
            if (alreadyExtracted[targetPageIndex]) {
                // There's no need to navigate to this page anymore
                return;
            }

            const distance = targetPageIndex - getCurrentPageIndex();
            console.log("current", getCurrentPageIndex(), "target", targetPageIndex, "distance", distance)

            // If we're close-by, let's wait a little bit to see if it will load on its own.
            if (Math.abs(distance) <= 4) {
                console.log("close to target, waiting");
                await waitMilliseconds(1500);
                if (alreadyExtracted[targetPageIndex]) {
                    console.log(targetPageIndex, "loaded while waiting");
                    return;
                }
                console.log(targetPageIndex, "didn't load while waiting");
            }

            await performUIAction(() => document.querySelector('button.thorium_web_overflow_hint').click());
            await performUIAction(() => document.querySelector('.thorium_web_overflow_menuItem[data-key="jumpToPosition"]').click());
            await performUIAction(() => {
                const input = document.querySelector('input.thorium_web_jumpToPosition_input');
                console.log("entering", targetPageIndex, "into input");
                input.value = targetPageIndex + 1;
                input.dispatchEvent(new InputEvent('input', { bubbles: true }))
            });
            await performUIAction(() => document.querySelector('.thorium_web_jumpToPosition_button[type="submit"]').click());

            console.log("final location", getCurrentPageIndex());

            if (getCurrentPageIndex() !== targetPageIndex) {
                console.log("retrying page adjustment in 500ms...")
                setTimeout(adjustPage, 500);
            }
        }

        adjustPage();
    }
}
