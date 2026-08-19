// Poll script - retrieves token + image URLs captured by fetch_hook
// Runs in evaluateJs every second until both token and URLs are ready
(function() {
    try {
        var stateKey = '__lx_retry_' + location.pathname;
        var readRetryState = function() {
            try {
                return JSON.parse(localStorage.getItem(stateKey) || '{}');
            } catch(e) {
                return window.__lxRetryState || {};
            }
        };
        var writeRetryState = function(state) {
            window.__lxRetryState = state;
            try { localStorage.setItem(stateKey, JSON.stringify(state)); } catch(e) {}
        };
        var retryState = readRetryState();
        var visibleDialogs = Array.from(document.querySelectorAll('.swal2-container'))
            .filter(function(dialog) {
                return getComputedStyle(dialog).display !== 'none' &&
                    dialog.getAttribute('aria-hidden') !== 'true';
            });
        var turnstileCount = document.querySelectorAll('[id*="turnstile"], iframe[src*="challenges.cloudflare.com"]').length;

        var verificationActive = Boolean(window.__lxCaptchaShown || window.getTokenRequestInProgress);
        if (!window.__lxPollStarted) window.__lxPollStarted = Date.now();

        if (!window.__lxCapturedUrls && !window.__lxToken && !verificationActive &&
            Date.now() - window.__lxPollStarted > 5000 && !window.__lxKgzFallbackTried) {
            var kgzScripts = Array.from(document.querySelectorAll('script'))
                .filter(function(script) {
                    return !script.src && (script.textContent || '').indexOf('KGZ1') >= 0;
                });
            if (kgzScripts.length > 0) {
                window.__lxKgzFallbackTried = true;
                kgzScripts.forEach(function(script, index) {
                    try {
                        (0, eval)(script.textContent || '');
                        Object.keys(window).forEach(function(key) {
                            if (!/^_0x[a-f0-9]+$/i.test(key) || !Array.isArray(window[key])) return;

                            var captured = window[key].filter(function(url) {
                                if (typeof url !== 'string') return false;
                                var normal = /\/page[_-]\d+\.(?:jpg|jpeg|png|webp)(?:[?#]|$)/i.test(url);
                                var puzzle = /^https?:\/\/s\d+\.lxmanga\.xyz\/.*\/\d+-[a-f0-9]+\.(?:jpg|jpeg|png|webp)(?:[?#]|$)/i.test(url);
                                return normal || puzzle;
                            });
                            if (captured.length > 0) {
                                window.__lxCapturedUrls = captured;
                            }
                        });
                    } catch(e) {
                    }
                });
            }
        }
        var verificationStarted = window.__lxVerificationStarted || 0;
        if (verificationActive && !verificationStarted) {
            verificationStarted = Date.now();
            window.__lxVerificationStarted = verificationStarted;
        }
        var failedDialog = visibleDialogs.find(function(dialog) {
            return /xác minh thất bại|verification failed|quá lâu không phản hồi/i.test(dialog.textContent || '');
        });
        if (failedDialog) {
            var reloadButton = Array.from(failedDialog.querySelectorAll('button')).find(function(button) {
                return /tải lại|reload|retry/i.test(button.textContent || '') && !button.disabled;
            });
            var reloadCount = retryState.dialogReloads || 0;
            if (reloadButton && reloadCount < 2) {
                retryState.dialogReloads = reloadCount + 1;
                writeRetryState(retryState);
                reloadButton.click();
                return JSON.stringify({token: '', urls: [], reloading: true});
            }
        }

        if (!window._lxClicked) {
            var activeDialog = visibleDialogs
                .find(function(dialog) {
                    return dialog.querySelector('.swal2-popup');
                });
            var turnstileResponse = document.querySelector(
                'input[name="cf-turnstile-response"], input[id*="turnstile"][id$="_response"], input[id*="cf-chl-widget"][id$="_response"]'
            );
            var hasTurnstileResponse = turnstileResponse && turnstileResponse.value;
            var canConfirm = hasTurnstileResponse || window.__lxToken;
            var btns = activeDialog ? activeDialog.querySelectorAll('.swal2-confirm') : [];
            for (var bi = 0; bi < btns.length; bi++) {
                var b = btns[bi];
                if (b && !b.disabled && canConfirm) {
                    var txt = (b.textContent || '').toLowerCase();
                    var isVerificationButton = txt.indexOf('ok') >= 0 ||
                        txt.indexOf('tiếp tục') >= 0 ||
                        txt.indexOf('continue') >= 0 ||
                        txt.indexOf('đọc') >= 0 ||
                        txt.indexOf('xem') >= 0 ||
                        (window.__lxToken && btns.length === 1);
                    if (isVerificationButton) {
                        b.click();
                        window._lxClicked = true;
                        window.__lxClickedAt = Date.now();
                        break;
                    }
                }
            }
        }

        if (window._lxClicked && activeDialog && window.__lxToken &&
            Date.now() - (window.__lxClickedAt || 0) > 2500) {
            window._lxClicked = false;
        }

        if (verificationActive && !hasTurnstileResponse && verificationStarted &&
            Date.now() - verificationStarted > 12000) {
            var retryCount = retryState.verificationReloads || 0;
            if (retryCount < 2) {
                retryState.verificationReloads = retryCount + 1;
                writeRetryState(retryState);
                window.__lxVerificationStarted = 0;
                location.reload();
                return JSON.stringify({token: '', urls: [], reloading: true});
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
        if (window.__lxImageUrls && window.__lxImageUrls.length > 0) {
            urls = urls.concat(window.__lxImageUrls);
        }

        urls = urls.filter(function(url, index) {
            var isNormalPage = /\/page[_-]\d+\.(?:jpg|jpeg|png|webp)(?:[?#]|$)/i.test(url || '');
            var isPuzzlePage = /^https?:\/\/s\d+\.lxmanga\.xyz\/.*\/\d+-[a-f0-9]+\.(?:jpg|jpeg|png|webp)(?:[?#]|$)/i.test(url || '');
            return url && urls.indexOf(url) === index && (isNormalPage || isPuzzlePage);
        }).sort(function(a, b) {
            var pageA = parseInt((a.match(/(?:page[_-]|\/)(\d+)(?:-|\.)/i) || [])[1] || '0', 10);
            var pageB = parseInt((b.match(/(?:page[_-]|\/)(\d+)(?:-|\.)/i) || [])[1] || '0', 10);
            return pageA - pageB;
        });

        var token = window.__lxToken || null;
        var currentCount = urls.length;
        if (currentCount !== window.__lxLastUrlCount) {
            window.__lxLastUrlCount = currentCount;
            window.__lxStableSince = Date.now();
        }
        var stableLongEnough = window.__lxStableSince && Date.now() - window.__lxStableSince >= 2500;

        if (!token && urls.length === 0 && !verificationActive && visibleDialogs.length === 0 &&
            Date.now() - window.__lxPollStarted > 20000) {
        }

        if (token && urls.length > 0 && stableLongEnough) {
            window.__lxVerificationStarted = 0;
            window.__lxVerificationReloads = 0;
            try { localStorage.removeItem(stateKey); } catch(e) {}
            return JSON.stringify({token: token, urls: urls});
        }

        return JSON.stringify({token: token || '', urls: urls || [], ready: false});
    } catch(e) {
        return JSON.stringify({token: '', urls: [], error: String(e)});
    }
})();
