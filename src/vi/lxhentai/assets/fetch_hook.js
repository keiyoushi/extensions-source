// Fetch hook - intercepts /get_token, image URLs, and unblocks Turnstile
// Injected via onPageStarted BEFORE any page scripts run
(function() {
    if (window.__lxHookInstalled) return;
    window.__lxHookInstalled = true;
    window.__lxToken = null;
    window.__lxImageUrls = [];
    window.__lxCapturedUrls = null;

    var _realFetch = window.fetch;
    window.__lxRealFetch = _realFetch;

    var _origSlice = Array.prototype.slice;
    Array.prototype.slice = function() {
        try {
            if (!window.__lxCapturedUrls && this.length > 2) {
                var sample = Math.min(3, this.length);
                var looksLikeUrls = true;
                for (var i = 0; i < sample; i++) {
                    if (typeof this[i] !== 'string' || (this[i].indexOf('http') !== 0 && this[i].indexOf('//') !== 0)) {
                        looksLikeUrls = false;
                        break;
                    }
                }
                if (looksLikeUrls) {
                    window.__lxCapturedUrls = _origSlice.call(this);
                }
            }
        } catch(e) {}
        return _origSlice.apply(this, arguments);
    };

    var _propTrapInterval = setInterval(function() {
        if (window.__lxPropTrapped) { clearInterval(_propTrapInterval); return; }
        try {
            var scripts = document.querySelectorAll('script');
            for (var i = 0; i < scripts.length; i++) {
                var text = scripts[i].textContent || '';
                var match = text.match(/window\['(_0x[a-f0-9]{6,})'\]/);
                if (match) {
                    window.__lxPropTrapped = true;
                    var _captured = null;
                    try {
                        Object.defineProperty(window, match[1], {
                            configurable: true, enumerable: true,
                            get: function() { return _captured; },
                            set: function(val) {
                                _captured = val;
                                if (Array.isArray(val) && val.length > 0 && !window.__lxCapturedUrls) {
                                    window.__lxCapturedUrls = val.slice();
                                }
                            }
                        });
                    } catch(e) {}
                    clearInterval(_propTrapInterval);
                    break;
                }
            }
        } catch(e) {}
    }, 50);

    window.fetch = function(input, init) {
        var url = (typeof input === 'string') ? input : (input && input.url) || '';

        if (url.indexOf('/get_token') >= 0) {
            return _realFetch.apply(this, arguments).then(function(resp) {
                var clone = resp.clone();
                clone.json().then(function(data) {
                    if (data && data.action_token) {
                        window.__lxToken = data.action_token;
                    }
                }).catch(function() {});
                return resp;
            }).catch(function(err) { throw err; });
        }

        if (init && init.headers) {
            var h = init.headers;
            var tok = null;
            if (h instanceof Headers) { tok = h.get('Token') || h.get('token'); }
            else if (typeof h === 'object') { tok = h['Token'] || h['token']; }
            if (tok && url.indexOf('http') === 0 && window.__lxImageUrls.indexOf(url) < 0) {
                window.__lxImageUrls.push(url);
            }
        }

        return _realFetch.apply(this, arguments);
    };

    try { window.fetch.toString = function() { return 'function fetch() { [native code] }'; }; } catch(e) {}

    var _replacedOnce = false;
    var _replaceInterval = setInterval(function() {
        try {
            if (_replacedOnce) { clearInterval(_replaceInterval); return; }
            if (window.fetch.toString().indexOf('[native code]') === -1) {
                _replacedOnce = true;
                var savedReal = window.__lxRealFetch;
                window.fetch = function(input, init) {
                    var url = (typeof input === 'string') ? input : (input && input.url) || '';

                    if (url.indexOf('/get_token') >= 0) {
                        return savedReal.apply(this, arguments).then(function(resp) {
                            var clone = resp.clone();
                            clone.json().then(function(data) {
                                if (data && data.action_token) {
                                    window.__lxToken = data.action_token;
                                }
                            }).catch(function() {});
                            return resp;
                        }).catch(function(err) { throw err; });
                    }

                    if (init && init.headers) {
                        var h = init.headers;
                        var tok = null;
                        if (h instanceof Headers) { tok = h.get('Token') || h.get('token'); }
                        else if (typeof h === 'object') { tok = h['Token'] || h['token']; }
                        if (tok && url.indexOf('http') === 0 && window.__lxImageUrls.indexOf(url) < 0) {
                            window.__lxImageUrls.push(url);
                        }
                    }

                    return savedReal.apply(this, arguments);
                };
                try { window.fetch.toString = function() { return 'function fetch() { [native code] }'; }; } catch(e) {}
                clearInterval(_replaceInterval);
            }
        } catch(e) {}
    }, 100);

    try {
        localStorage.removeItem('turnstile_blocked');
        localStorage.removeItem('turnstile_blocked_time');
    } catch(e) {}
})();
