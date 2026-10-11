package eu.kanade.tachiyomi.extension.fr.japscan

// Desktop Chrome UA matching what the descrambler's working code path expects (the source's
// own UA is Android-mobile flavored, which makes the descrambler take a one-tile-per-page path).
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"

// Satisfies the reader's anti-adblock check (`window.aclib`) before it runs; without it the
// reader wipes the descrambled canvases because the real ad script is blocked.
internal val ACLIB_STUB = """
                            (function(){
                                if (window.aclib) return;
                                window.aclib = {
                                    runPop: function runPop(){},
                                    runBanner: function runBanner(){},
                                    runNative: function runNative(){},
                                    runInPagePush: function runInPagePush(){},
                                    isShowingPop: false,
                                };
                            })();
""".trimIndent()

// Paginated mode: minimal createObjectURL capture. The descrambler surfaces each composited
// page as a `blob:` URL, capturing the latest one per page-click is enough.
internal val PAGINATED_HOOK = """
                                (function(){
                                    if (window.__japscanHooked) return;
                                    window.__japscanHooked = true;
                                    window.__japscanLastBlob = null;
                                    var _orig = URL.createObjectURL.bind(URL);
                                    URL.createObjectURL = function(obj){
                                        var u = _orig(obj);
                                        try {
                                            if (obj && obj.type && /^image\//.test(obj.type)) {
                                                window.__japscanLastBlob = u;
                                            }
                                        } catch(e) {}
                                        return u;
                                    };
                                    try {
                                        URL.createObjectURL.toString = function(){
                                            return 'function createObjectURL() { [native code] }';
                                        };
                                    } catch(e) {}
                                })();
""".trimIndent()

internal fun webtoonHooks(interfaceName: String) = $$"""
                            (function(){
                                // Pin a logging channel through the Java bridge BEFORE the
                                // site has a chance to silence `console.log` (some pages do
                                // `console.log = function(){}` once their reader scripts run,
                                // which would swallow every diagnostic the rest of the driver
                                // emits via `console.log`).
                                try {
                                    var nativeLog = console.log.bind(console);
                                    window.__jlog = function(){ try { nativeLog.apply(console, arguments); } catch(e) {} };
                                } catch(e) {
                                    window.__jlog = function(){};
                                }
                                window.__jlog('[japscan] onPageStarted JS entered, alreadyHooked=' + (window.__japscanHooked === true) + ' url=' + location.href);
                                if (window.__japscanHooked) return;
                                window.__japscanHooked = true;

                                // Patch table: maps each patched function to the canonical
                                // native-code string for its name. We override
                                // Function.prototype.toString so the site sees the original
                                // native source whenever it stringifies one of our hooks —
                                // the reader trips `__poisoned = true` if it spots a
                                // tampered built-in (it explicitly checks `replace`,
                                // `atob` and friends, and likely checks `attachShadow`
                                // and the `shadowRoot` getter as well).
                                var masked = new WeakMap();
                                var origFnToString = Function.prototype.toString;
                                function mask(fn, name){
                                    try { masked.set(fn, 'function ' + name + '() { [native code] }'); } catch(e) {}
                                    return fn;
                                }
                                var patchedToString = function toString(){
                                    var m = masked.get(this);
                                    if (m !== undefined) return m;
                                    return origFnToString.call(this);
                                };
                                Function.prototype.toString = patchedToString;
                                mask(patchedToString, 'toString');

                                // Force every shadow root open at attach time so we can
                                // read its canvases later — but track which ones the
                                // caller *requested* be closed, and hide those from the
                                // public `Element.prototype.shadowRoot` getter. The site
                                // sees `el.shadowRoot === null` for its `<w-f1db5>` hosts
                                // (matching the unpatched behavior); only our compositor,
                                // via `window.__japscanShadowRootFor(el)`, can reach them.
                                try {
                                    var closedRoots = new WeakMap();
                                    var _attach = Element.prototype.attachShadow;
                                    var newAttach = function attachShadow(init){
                                        var requested = (init && init.mode) || 'open';
                                        var opts = {};
                                        if (init) { for (var k in init) opts[k] = init[k]; }
                                        opts.mode = 'open';
                                        var sr = _attach.call(this, opts);
                                        if (requested === 'closed') {
                                            try { closedRoots.set(this, sr); } catch(e) {}
                                        }
                                        return sr;
                                    };
                                    Element.prototype.attachShadow = newAttach;
                                    mask(newAttach, 'attachShadow');

                                    var origDesc = Object.getOwnPropertyDescriptor(Element.prototype, 'shadowRoot');
                                    var origGetter = origDesc && origDesc.get;
                                    var newGetter = function get(){
                                        if (closedRoots.has(this)) return null;
                                        return origGetter ? origGetter.call(this) : null;
                                    };
                                    Object.defineProperty(Element.prototype, 'shadowRoot', {
                                        get: newGetter,
                                        configurable: true,
                                    });
                                    mask(newGetter, 'get shadowRoot');

                                    window.__japscanShadowRootFor = function(el){
                                        try {
                                            if (closedRoots.has(el)) return closedRoots.get(el);
                                            return origGetter ? origGetter.call(el) : el.shadowRoot;
                                        } catch(e) { return null; }
                                    };
                                } catch(e) {
                                    window.__jlog('[japscan] shadow hook failed: ' + e);
                                }

                                // Android throttles requestAnimationFrame for non-visible WebViews
                                // (our WebView is detached, so it's never visible). The reader
                                // drives its tile-creation loop on RAF; without ticks it renders
                                // only the first tile per page and stalls. Force RAF to fire via
                                // setTimeout so the loop keeps progressing.
                                try {
                                    var _rAF = window.requestAnimationFrame;
                                    var newRAF = function requestAnimationFrame(cb){
                                        return setTimeout(function(){
                                            try { cb(performance.now()); } catch(e) {}
                                        }, 16);
                                    };
                                    window.requestAnimationFrame = newRAF;
                                    mask(newRAF, 'requestAnimationFrame');
                                } catch(e) {
                                    window.__jlog('[japscan] rAF hook failed: ' + e);
                                }

                                // Track canvas drawing activity so the compositor can wait for
                                // descrambling to settle before reading pixels. Wrapping
                                // drawImage is detectable via .toString — masked above.
                                try {
                                    window.__japscanLastDraw = 0;
                                    var _drawImage = CanvasRenderingContext2D.prototype.drawImage;
                                    var newDrawImage = function drawImage(){
                                        try {
                                            window.__japscanLastDraw = Date.now();
                                        } catch(e) {}
                                        return _drawImage.apply(this, arguments);
                                    };
                                    CanvasRenderingContext2D.prototype.drawImage = newDrawImage;
                                    mask(newDrawImage, 'drawImage');
                                } catch(e) {
                                    window.__jlog('[japscan] draw hook failed: ' + e);
                                }

                                // Spoof JS-side environment to match a desktop Chrome on this
                                // chapter. The WebView's native viewport/screen/touch signals
                                // tell the descrambler "you're a phone" and it takes the
                                // single-tile-per-page mobile path. Override the values that
                                // the differential probe identified:
                                //   innerWidth/innerHeight, devicePixelRatio,
                                //   screen.width/height, navigator.platform/maxTouchPoints,
                                //   matchMedia for (pointer: fine), (hover: hover),
                                //   and (min-width: <N>px).
                                //
                                // The UA is spoofed HERE, not at the WebSettings level:
                                // WebSettings keeps the Android UA on purpose so Cloudflare
                                // doesn't issue a fresh challenge for this request, and only
                                // the JS-visible navigator.userAgent reads as desktop.
                                // Anything not in the list above was tried and dropped —
                                // don't re-add fake plugins/chrome/webdriver on spec.
                                try {
                                    var defineGetter = function(obj, name, val){
                                        try {
                                            Object.defineProperty(obj, name, {
                                                get: function(){ return val; },
                                                configurable: true,
                                            });
                                        } catch(e) { window.__jlog('[japscan] defineGetter ' + name + ' failed: ' + e); }
                                    };
                                    defineGetter(window, 'innerWidth', 2560);
                                    defineGetter(window, 'innerHeight', 1214);
                                    defineGetter(window, 'devicePixelRatio', 0.75);
                                    defineGetter(window.screen, 'width', 1920);
                                    defineGetter(window.screen, 'height', 1080);
                                    defineGetter(window.screen, 'availWidth', 1920);
                                    defineGetter(window.screen, 'availHeight', 1040);
                                    defineGetter(navigator, 'userAgent', '$$DESKTOP_USER_AGENT');
                                    defineGetter(navigator, 'appVersion', '$$DESKTOP_USER_AGENT'.substring(8));
                                    defineGetter(navigator, 'platform', 'Win32');
                                    defineGetter(navigator, 'maxTouchPoints', 0);
                                    defineGetter(navigator, 'hardwareConcurrency', 12);
                                    // Remove ontouchstart so feature-detect-based mobile
                                    // branches see a non-touch device.
                                    try { delete window.ontouchstart; } catch(e) {}

                                    var _mm = window.matchMedia.bind(window);
                                    var newMM = function matchMedia(q){
                                        var orig = _mm(q);
                                        var shouldForceTrue = (
                                            q === '(pointer: fine)' ||
                                            q === '(hover: hover)' ||
                                            /\(min-width:\s*\d+px\)/.test(q) ||
                                            /\(min-device-width:\s*\d+px\)/.test(q)
                                        );
                                        if (!shouldForceTrue) return orig;
                                        return new Proxy(orig, {
                                            get: function(t, k){
                                                if (k === 'matches') return true;
                                                var v = t[k];
                                                return typeof v === 'function' ? v.bind(t) : v;
                                            },
                                        });
                                    };
                                    window.matchMedia = newMM;
                                    mask(newMM, 'matchMedia');
                                } catch(e) {
                                    window.__jlog('[japscan] env spoof failed: ' + e);
                                }

                                window.__jlog('[japscan] hooks installed');
                            })();
""".trimIndent()

internal fun paginatedDriver(interfaceName: String) = $$"""
                                (async function(){
                                    // onPageFinished fires more than once per load (redirects,
                                    // iframes, in-page navigations). Without this guard a second
                                    // driver races the first and saves every page twice.
                                    if (window.__japscanDriverStarted) return;
                                    window.__japscanDriverStarted = true;
                                    var sleep = function(ms){ return new Promise(function(r){ setTimeout(r, ms); }); };

                                    async function saveBlobUrl(u){
                                        if (!u) return false;
                                        try {
                                            var r = await fetch(u);
                                            var b = await r.blob();
                                            var d = await new Promise(function(res, rej){
                                                var fr = new FileReader();
                                                fr.onload = function(){ res(fr.result); };
                                                fr.onerror = rej;
                                                fr.readAsDataURL(b);
                                            });
                                            if (typeof d === 'string' && d.indexOf('data:image/') === 0) {
                                                window.$${interfaceName}_savePage.post(d);
                                                return true;
                                            }
                                        } catch(e) {
                                            console.log('[japscan] saveBlobUrl failed: ' + e);
                                        } finally {
                                            try { URL.revokeObjectURL(u); } catch(e) {}
                                        }
                                        return false;
                                    }

                                    async function waitForBlob(timeoutMs){
                                        var w = 0;
                                        while (!window.__japscanLastBlob && w < timeoutMs) {
                                            await sleep(100); w += 100;
                                        }
                                        if (!window.__japscanLastBlob) return -1;
                                        var lastUrl = window.__japscanLastBlob;
                                        var stable = 0;
                                        while (stable < 400) {
                                            await sleep(100);
                                            if (window.__japscanLastBlob !== lastUrl) {
                                                lastUrl = window.__japscanLastBlob;
                                                stable = 0;
                                            } else {
                                                stable += 100;
                                            }
                                        }
                                        return w;
                                    }

                                    var sel = document.getElementById('pages');
                                    var total = sel ? sel.options.length : 1;
                                    var nextBtn = document.getElementById('block-right');
                                    var prevBtn = document.getElementById('block-left');
                                    console.log('[japscan] paginated mode, total = ' + total + ', next=' + !!nextBtn + ', prev=' + !!prevBtn);

                                    function nav(btn, fallbackKey){
                                        try {
                                            if (btn) { btn.click(); return; }
                                            document.dispatchEvent(new KeyboardEvent('keydown', {
                                                key: fallbackKey, code: fallbackKey,
                                                which: fallbackKey === 'ArrowRight' ? 39 : 37,
                                                keyCode: fallbackKey === 'ArrowRight' ? 39 : 37,
                                                bubbles: true,
                                            }));
                                        } catch(e) {
                                            console.log('[japscan] nav failed: ' + e);
                                        }
                                    }

                                    // The reader pre-renders pages on load (the final pre-render is
                                    // the *last* page, not page 1), so the very first blob the hook
                                    // sees is unreliable. Wait for the pre-render to settle, discard
                                    // whatever was captured, then bounce next->prev to force a clean
                                    // page-1 render before we start harvesting.
                                    //
                                    // waitForBlob already means "a blob arrived and stopped changing
                                    // for 400ms", which is exactly the settle condition — a fixed
                                    // sleep was both slower here and too short on a loaded device.
                                    await waitForBlob(10000);
                                    window.__japscanLastBlob = null;

                                    nav(nextBtn, 'ArrowRight');
                                    await waitForBlob(8000);
                                    window.__japscanLastBlob = null;

                                    nav(prevBtn, 'ArrowLeft');
                                    var t0 = await waitForBlob(8000);
                                    var u0 = window.__japscanLastBlob;
                                    window.__japscanLastBlob = null;
                                    if (total > 1) nav(nextBtn, 'ArrowRight');
                                    var saved = u0 ? ((await saveBlobUrl(u0)) ? 1 : 0) : 0;
                                    console.log('[japscan] page 1 captured after ' + t0 + 'ms (saved=' + saved + ')');

                                    for (var i = 1; i < total; i++) {
                                        var t = await waitForBlob(8000);
                                        var u = window.__japscanLastBlob;
                                        window.__japscanLastBlob = null;
                                        if (i < total - 1) nav(nextBtn, 'ArrowRight');
                                        var ok = u ? await saveBlobUrl(u) : false;
                                        if (ok) saved++;
                                        console.log('[japscan] page ' + (i + 1) + ' captured after ' + t + 'ms (saved=' + saved + ')');
                                    }

                                    console.log('[japscan] done, ' + saved + ' / ' + total + ' pages saved');
                                    try {
                                        window.$${interfaceName}_passDone.post("");
                                    } catch(e) {
                                        console.log('[japscan] passDone failed: ' + e);
                                    }
                                })();
""".trimIndent()

internal fun webtoonDriver(interfaceName: String, urlSegment: String) = $$"""
                            (async function(){
                                // onPageFinished fires more than once per load (redirects,
                                // iframes, in-page navigations). Without this guard a second
                                // driver races the first and saves every tile twice.
                                if (window.__japscanDriverStarted) return;
                                window.__japscanDriverStarted = true;
                                var sleep = function(ms){ return new Promise(function(r){ setTimeout(r, ms); }); };
                                window.__jlog('[japscan] driver start');

                                // Collect every <canvas> reachable from `root`, descending
                                // through shadow roots (including the formerly-closed ones
                                // the attachShadow hook stashed in __japscanShadowRootFor).
                                var shadowFor = window.__japscanShadowRootFor || function(){ return null; };
                                function gatherCanvases(root, out) {
                                    if (!root) return;
                                    if (root.nodeType === 1 && root.tagName === 'CANVAS') {
                                        out.push(root);
                                    }
                                    var sr = null;
                                    try { sr = shadowFor(root); } catch(e) {}
                                    if (sr) {
                                        var sk = sr.children;
                                        if (sk) {
                                            for (var i = 0; i < sk.length; i++) gatherCanvases(sk[i], out);
                                        }
                                    }
                                    var ch = root.children;
                                    if (ch) {
                                        for (var j = 0; j < ch.length; j++) gatherCanvases(ch[j], out);
                                    }
                                }

                                // Long-page layout: `#d-img-N` holds a `cc…`-class wrapper whose
                                // direct <canvas> children are the descrambled vertical slices.
                                // Single source of truth for finding it — discoverTiles and
                                // expectedTileCount must agree, or a class rename desyncs them.
                                function findTileWrapper(container){
                                    if (!container) return null;
                                    for (var ci = 0; ci < container.children.length; ci++) {
                                        var ch = container.children[ci];
                                        if (ch.tagName === 'DIV' && ('' + ch.className).indexOf('cc') === 0) {
                                            return ch;
                                        }
                                    }
                                    return null;
                                }

                                // Discover tile groups in `#d-img-N`'s subtree. Canvases-first:
                                // walk canvases (works regardless of whether the host custom
                                // element is exposed in light DOM), group them by their shadow-root
                                // host, and treat each unique host as one tile.
                                //
                                // Returns `{ host, canvases }` records in stable order of first
                                // appearance. The host is the custom element (e.g. <b-123e6>,
                                // <w-f1db5> — the tag is randomized per series) whose `.canvas`
                                // own-property, when set, gives the descrambled bitmap; `canvases`
                                // is the layered set inside its shadow root.
                                //
                                // Discovery is STRUCTURAL, not readiness-based: we deliberately do
                                // NOT require `.canvas` to exist yet, because the descrambler is
                                // lazy and only renders a tile once it scrolls into view —
                                // chicken-and-egg if we filtered on `.canvas` here.
                                function discoverTiles(container){
                                    if (!container) return [];
                                    // Long pages: emit each slice as a standalone tile so
                                    // toDataURL runs per slice and the webtoon reader stitches them.
                                    var wrap = findTileWrapper(container);
                                    if (wrap) {
                                        var direct = [];
                                        for (var di = 0; di < wrap.children.length; di++) {
                                            var w = wrap.children[di];
                                            if (w.tagName === 'CANVAS' && w.width > 0 && w.height > 0) {
                                                direct.push(w);
                                            }
                                        }
                                        if (direct.length > 0) {
                                            var outArr = [];
                                            for (var di2 = 0; di2 < direct.length; di2++) {
                                                outArr.push({ host: direct[di2], canvases: [direct[di2]] });
                                            }
                                            return outArr;
                                        }
                                    }
                                    // Short-page / single-tile layout: canvases live inside a
                                    // custom-element host (e.g. <t-b8432>). Group them by
                                    // shadow-root host so each host yields one tile.
                                    var canvases = [];
                                    gatherCanvases(container, canvases);
                                    if (canvases.length === 0) return [];
                                    var map = new Map();
                                    var order = [];
                                    for (var i = 0; i < canvases.length; i++) {
                                        var c = canvases[i];
                                        if (c.width <= 0 || c.height <= 0) continue;
                                        var rn = c.getRootNode && c.getRootNode();
                                        var host = (rn && rn.host) ? rn.host : container;
                                        if (!map.has(host)) {
                                            map.set(host, []);
                                            order.push(host);
                                        }
                                        map.get(host).push(c);
                                    }
                                    var out = [];
                                    for (var k = 0; k < order.length; k++) {
                                        out.push({ host: order[k], canvases: map.get(order[k]) });
                                    }
                                    return out;
                                }

                                // Resolve a tile to a single canvas for export. Preference order:
                                //   1. `host.canvas` own-property — the descrambler's explicit
                                //      pointer to the final composited bitmap.
                                //   2. The lone canvas with `position: relative` in the layered
                                //      set — it establishes the visible tile and on inspection
                                //      consistently holds the real bitmap; siblings are decoys.
                                //   3. The first non-empty canvas — last-resort.
                                function pickTileCanvas(tile){
                                    // cc-wrapper slices are their own host — nothing to resolve.
                                    if (tile.host instanceof HTMLCanvasElement) return tile.host;
                                    try {
                                        if (tile.host && tile.host.canvas instanceof HTMLCanvasElement) {
                                            return tile.host.canvas;
                                        }
                                    } catch(e) {}
                                    var arr = tile.canvases || [];
                                    for (var i = 0; i < arr.length; i++) {
                                        var s = null;
                                        try { s = window.getComputedStyle(arr[i]); } catch(e) {}
                                        if (s && s.position === 'relative') return arr[i];
                                    }
                                    return arr[0] || null;
                                }

                                // Wait until at least one of the tile's canvases (or its
                                // host.canvas) has non-zero dimensions and the descrambler has
                                // gone quiet (no drawImage calls for ≥ quietMs).
                                async function waitForTileReady(tile, timeoutMs){
                                    var w = 0;
                                    var quietMs = 600;
                                    while (w < timeoutMs) {
                                        var anySized = false;
                                        try {
                                            if (tile.host && tile.host.canvas && tile.host.canvas.width > 0) anySized = true;
                                        } catch(e) {}
                                        if (!anySized) {
                                            var arr = tile.canvases || [];
                                            for (var i = 0; i < arr.length; i++) {
                                                if (arr[i].width > 0 && arr[i].height > 0) { anySized = true; break; }
                                            }
                                        }
                                        if (anySized) {
                                            // Honor the host's own ready flags if present.
                                            var hostReady = true;
                                            try {
                                                if (tile.host && ('cw' in tile.host)) {
                                                    hostReady = !!(tile.host.cw && tile.host.ch);
                                                }
                                            } catch(e) {}
                                            var sinceDraw = Date.now() - (window.__japscanLastDraw || 0);
                                            if (hostReady && sinceDraw >= quietMs) return w;
                                        }
                                        await sleep(150);
                                        w += 150;
                                    }
                                    return -1;
                                }

                                function tileToDataUri(tile){
                                    try {
                                        var c = pickTileCanvas(tile);
                                        if (!c || !c.width || !c.height) return null;
                                        return c.toDataURL('image/jpeg', 0.92);
                                    } catch(e) { return null; }
                                }

                                // Detect reader mode. Webtoon (long-strip) puts everything in
                                // `#full-reader` with one `<img id="img-N">` per page (each in
                                // its `<div id="d-img-N">` container) and hides `#single-reader`
                                // (class `d-none`); paginated mode is the opposite.
                                var fullReader = document.getElementById('full-reader');
                                var singleReader = document.querySelector('[id^="single-reader"]');
                                var dImgContainers = fullReader
                                    ? Array.from(fullReader.querySelectorAll('div[id^="d-img-"]'))
                                          .sort(function(a, b){
                                              return parseInt(a.id.replace('d-img-', ''), 10)
                                                   - parseInt(b.id.replace('d-img-', ''), 10);
                                          })
                                    : [];
                                var singleHidden = singleReader && singleReader.classList.contains('d-none');
                                var isWebtoon = dImgContainers.length > 0 && (singleHidden || !singleReader);
                                // The Kotlin side picked us based on the URL segment ($$urlSegment).
                                // Compare against what the DOM actually mounted so a misclassified
                                // series shows up in Logcat instead of producing silent garbage.
                                var urlHint = '$$urlSegment';
                                var hintMode = (urlHint === 'manhwa' || urlHint === 'manhua') ? 'webtoon' : 'paginated';
                                var domMode = isWebtoon ? 'webtoon' : 'paginated';
                                window.__jlog('[japscan] mode: webtoon=' + isWebtoon + ' pages=' + dImgContainers.length + ' urlHint=' + hintMode + ' dom=' + domMode + (hintMode !== domMode ? ' MISMATCH' : ''));



                                if (isWebtoon) {
                                    var total = dImgContainers.length;
                                    // Drop the hidden single-reader subtree to keep its stale
                                    // layer-canvases from adding memory pressure.
                                    if (singleReader && singleReader.parentNode) {
                                        try { singleReader.parentNode.removeChild(singleReader); } catch(e) {}
                                    }
                                    // For each `#d-img-N`: determine the expected tile count up
                                    // front (number of direct <canvas> children of the cc...
                                    // wrapper for long pages, else 1 for single-tile pages).
                                    // Try a one-pass capture first; if all expected tiles are
                                    // already drawn, we're done in <1s. Only fall back to the
                                    // slow scroll-and-discover loop if a tile is still missing.
                                    var tilesEmitted = 0;
                                    function expectedTileCount(container){
                                        var wrap = findTileWrapper(container);
                                        if (!wrap) return 1;
                                        var n = 0;
                                        for (var ki = 0; ki < wrap.children.length; ki++) {
                                            if (wrap.children[ki].tagName === 'CANVAS') n++;
                                        }
                                        return n > 0 ? n : 1;
                                    }
                                    async function captureOnce(container, pageLabel, savedHosts){
                                        var saved = 0;
                                        var tiles = discoverTiles(container);
                                        for (var ti = 0; ti < tiles.length; ti++) {
                                            var tile = tiles[ti];
                                            if (!tile.host || savedHosts.has(tile.host)) continue;
                                            var ready = await waitForTileReady(tile, 6000);
                                            if (ready < 0) continue;
                                            var picked = pickTileCanvas(tile);
                                            if (!picked || !picked.width || !picked.height) continue;
                                            var uri = tileToDataUri(tile);
                                            if (!uri) continue;
                                            try {
                                                window.$${interfaceName}_savePage.post(uri);
                                                savedHosts.add(tile.host);
                                                saved++;
                                                tilesEmitted++;
                                                window.__jlog('[japscan] ' + pageLabel + ' tile #' + savedHosts.size + ' saved (' + picked.width + 'x' + picked.height + ', ' + uri.length + ' chars)');
                                            } catch(e) {
                                                window.__jlog('[japscan] ' + pageLabel + ' savePage failed: ' + e);
                                            }
                                        }
                                        return saved;
                                    }
                                    for (var i = 0; i < total; i++) {
                                        var container = dImgContainers[i];
                                        var pageLabel = 'd-img-' + i;
                                        var expected = expectedTileCount(container);
                                        // Bring the container into view so any layout-dependent
                                        // measurements settle, then attempt a one-pass capture.
                                        try { container.scrollIntoView({ block: 'start' }); } catch(e) {}
                                        await sleep(100);
                                        var savedHosts = new Set();
                                        await captureOnce(container, pageLabel, savedHosts);
                                        // If something is still missing (rare — only the lazy
                                        // single-tile path), fall back to the scroll loop with
                                        // a short total budget.
                                        if (savedHosts.size < expected) {
                                            var contRect = container.getBoundingClientRect();
                                            var baseY = (window.scrollY || 0) + contRect.top;
                                            var contH = container.offsetHeight || contRect.height || 2100;
                                            var step = Math.max(400, Math.floor((window.innerHeight || 4096) * 0.5));
                                            for (var sy = 0; sy <= contH + step && savedHosts.size < expected; sy += step) {
                                                window.scrollTo(0, baseY + sy - 100);
                                                await sleep(400);
                                                await captureOnce(container, pageLabel, savedHosts);
                                            }
                                        }
                                        window.__jlog('[japscan] ' + pageLabel + ' completed: ' + savedHosts.size + '/' + expected + ' tile(s)');
                                        try {
                                            if (container.parentNode) container.parentNode.removeChild(container);
                                        } catch(e) {}
                                        dImgContainers[i] = null;
                                    }
                                    window.__jlog('[japscan] webtoon done, ' + tilesEmitted + ' tile(s) saved across ' + total + ' d-img containers');
                                    try { window.$${interfaceName}_passDone.post(""); } catch(e) {}
                                    return;
                                }

                                // Kotlin committed to the webtoon hook set before this page loaded,
                                // and the paginated reader refuses to deliver its payload with those
                                // hooks installed — so there is nothing useful to do from here.
                                // Report and bail instead of grinding through a driver that cannot
                                // work; if this ever fires, the fix is to reload the WebView with
                                // the paginated hooks, not a third capture technique.
                                window.__jlog('[japscan] MISMATCH: url hinted webtoon but the DOM mounted the paginated reader — giving up');
                                try { window.$${interfaceName}_passDone.post(""); } catch(e) {}
                            })();
""".trimIndent()
