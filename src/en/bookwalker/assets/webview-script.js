//__INJECT_WEBVIEW_INTERFACE = { reportImage: console.log }
const webviewInterface = __INJECT_WEBVIEW_INTERFACE;

const checkLoadedTimer = setInterval(() => {
    if (document.getElementById('pageSliderCounter')?.innerText) {
        webviewInterface.reportViewerLoaded();
        clearInterval(checkLoadedTimer);
    }
}, 10);

const checkMessageTimer = setInterval(() => {
    const messageElt = document.querySelector('#messageDialog .message');
    if (messageElt) {
        webviewInterface.reportFailedToLoad(messageElt.textContent);
        clearInterval(checkMessageTimer);
    }
}, 2000);

// In order for image extraction to work, the viewer needs to be in horizontal mode.
// That setting is stored in localStorage, so we intercept calls to localStorage.getItem()
// in order to fake the value we want.
// There is a potential timing concern here if this JavaScript runs too late, but it
// seems that the value is read later in the loading process so it should be okay.
localStorage.getItem = function(key) {
    let result = Storage.prototype.getItem.apply(this, [key]);
    if (key === '/NFBR_Settings/NFBR.SettingData') {
        try {
            const data = JSON.parse(result);
            data.viewerPageTransitionAxis = 'horizontal';
            result = JSON.stringify(data);
        }
        catch (e) {}
    }
    return result;
};

function getCurrentPageIndex() {
    return +document.getElementById('pageSliderCounter').innerText.split('/')[0] - 1;
}

function getLastPageIndex() {
    return +document.getElementById('pageSliderCounter').innerText.split('/')[1] - 1;
}

const alreadyFetched = [];

// The goal here is to capture the full-size processed image data just before it
// gets resized and drawn to the viewport.
const baseDrawImage = CanvasRenderingContext2D.prototype.drawImage;
CanvasRenderingContext2D.prototype.drawImage = function(image, sx, sy, sWidth, sHeight /* , ... */) {
    baseDrawImage.apply(this, arguments);

    // It's important that the screen size is small enough that only one page
    // appears at a time so that this page number is accurate.
    // Otherwise, pages could end up out of order, skipped, or duplicated.
    const pageIdx = getCurrentPageIndex();
    if (alreadyFetched[pageIdx]) {
        // We already found this page, no need to do the processing again
        return;
    }

    const current = document.querySelector('.currentScreen');
    // It can render pages on the opposite side of the spread even in one-page mode,
    // so to make sure we're not grabbing the wrong image, we want to check that
    // the current page is the side that the image is being drawn to.
    if (current.contains(this.canvas)) {
        // imageData should be a Uint8ClampedArray containing RGBA row-major bitmap image data.
        // We don't create the final JPEGs right here with Canvas.toBlob/OffscreenCanvas.convertToBlob
        // because in testing, that was _extremely_ slow to run in the Webview, taking upwards of
        // 15 seconds per image. Doing that conversion on the JVM side is near-instantaneous.
        let imageData;

        if (image instanceof ImageBitmap) {
            const ctx = new OffscreenCanvas(sWidth, sHeight)
                .getContext('2d', { willReadFrequently: true });
            ctx.drawImage(image, sx, sy, sWidth, sHeight);
            imageData = ctx.getImageData(sx, sy, sWidth, sHeight).data;
        }
        else if (image instanceof HTMLCanvasElement && image.matches('canvas.dummy[width][height]')) {
            const ctx = image.getContext('2d', { willReadFrequently: true });
            imageData = ctx.getImageData(sx, sy, sWidth, sHeight).data;
        }
        else {
            // Other misc images can sometimes be drawn. We don't care about those.
            return;
        }
        console.log("intercepted image");

        alreadyFetched[pageIdx] = true;

        // The WebView interface only allows communicating with strings,
        // so we need to convert our ArrayBuffer to a string for transport.
        const textData = new TextDecoder("windows-1252").decode(imageData);
        console.log("sending encoded data");
        webviewInterface.reportImage(pageIdx, textData, sWidth, sHeight);
    }
}

// JS UTILITIES
const leftArrowKeyCode = 37;
const rightArrowKeyCode = 39;

function fireKeyboardEvent(elt, keyCode) {
    elt.dispatchEvent(new window.KeyboardEvent('keydown', { keyCode }));
}

window.__INJECT_JS_UTILITIES = {
    fetchPageData(targetPageIndex) {
        alreadyFetched[targetPageIndex] = false;

        const lastPageIndex = getLastPageIndex();

        if (targetPageIndex > lastPageIndex) {
            // This generally occurs when reading a preview chapter.
            webviewInterface.reportImageDoesNotExist(
                targetPageIndex,
                "You have reached the end of the preview.",
            );
            return;
        }

        const renderer = document.getElementById('renderer');
        const slider = document.getElementById('pageSliderBar');
        const isLTR = Boolean(slider.querySelector('.ui-slider-range-min'));

        const [forwardsKeyCode, backwardsKeyCode] = isLTR
            ? [rightArrowKeyCode, leftArrowKeyCode]
            : [leftArrowKeyCode, rightArrowKeyCode];

        if (getCurrentPageIndex() === targetPageIndex) {
            // The image may have already loaded, but we need to shuffle around for it to get reported.
            // Otherwise, we can get stuck waiting for the image to be reported forever and
            // eventually time out.
            console.log('already at correct page');
            if (targetPageIndex === lastPageIndex) {
                fireKeyboardEvent(renderer, backwardsKeyCode);
                fireKeyboardEvent(renderer, forwardsKeyCode);
            }
            else {
                fireKeyboardEvent(renderer, forwardsKeyCode);
                fireKeyboardEvent(renderer, backwardsKeyCode);
            }
            return;
        }

        function invertIfRTL(value) {
            if (isLTR) {
                return value;
            }
            return 1 - value;
        }

        const { x, width, y, height } = slider.getBoundingClientRect();
        const options = {
            clientX: x + width * invertIfRTL(targetPageIndex / lastPageIndex),
            clientY: y + height / 2,
            bubbles: true,
        };
        slider.dispatchEvent(new MouseEvent('mousedown', options));
        slider.dispatchEvent(new MouseEvent('mouseup', options));

        // That should have gotten us most of the way there but since the clicks aren't always
        // perfectly accurate, we may need to make some adjustments to get the rest of the way.
        // This mostly comes up for longer chapters and volumes that have a large number of pages,
        // since the small webview makes the slider pretty short.

        function adjustPage() {
            const distance = targetPageIndex - getCurrentPageIndex();
            console.log("current", getCurrentPageIndex(), "target", targetPageIndex, "distance", distance)
            if (distance !== 0) {
                const keyCode = distance > 0 ? forwardsKeyCode : backwardsKeyCode;
                for (let i = 0; i < Math.abs(distance); i++) {
                    renderer.dispatchEvent(new KeyboardEvent('keydown', { keyCode }));
                }
            }

            console.log("final location", getCurrentPageIndex());

            // Sometimes, particularly when the page has just loaded, the adjustment doesn't work.
            // If that happens, retry the adjustment after a brief delay.
            if (getCurrentPageIndex() !== targetPageIndex) {
                console.log("retrying page adjustment in 100ms...")
                setTimeout(adjustPage, 100);
            }
        }

        adjustPage();
    }
}
