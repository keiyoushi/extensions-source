// Poll script - retrieves token + image URLs captured by fetch_hook
// Runs in evaluateJs every second until both token and URLs are ready
(function() {
    try {
        if (!window._lxClicked) {
            var btns = document.querySelectorAll('.swal2-confirm');
            for (var bi = 0; bi < btns.length; bi++) {
                var b = btns[bi];
                if (b && !b.disabled) {
                    var txt = (b.textContent || '').toLowerCase();
                    if (txt.indexOf('ok') >= 0 || txt.indexOf('tiếp tục') >= 0 || txt.indexOf('continue') >= 0) {
                        b.click();
                        window._lxClicked = true;
                        break;
                    }
                }
            }
        }

        if (!window._lxDone) {
            window._lxDone = true;
            try {
                ['pointerdown', 'touchstart', 'wheel', 'keydown'].forEach(function(t) {
                    document.dispatchEvent(new Event(t, {bubbles: true}));
                });
                window.dispatchEvent(new Event('scroll'));
            } catch(e) {}
        }

        var urls = [];
        if (window.__lxCapturedUrls && window.__lxCapturedUrls.length > 0) {
            urls = window.__lxCapturedUrls;
        }
        if (urls.length === 0 && window.__lxImageUrls && window.__lxImageUrls.length > 0) {
            urls = window.__lxImageUrls;
        }

        var token = window.__lxToken || null;

        if (token && urls.length > 0) {
            return JSON.stringify({token: token, urls: urls});
        }

        return JSON.stringify({token: token || '', urls: urls || [], ready: false});
    } catch(e) {
        return JSON.stringify({token: '', urls: [], error: String(e)});
    }
})();
