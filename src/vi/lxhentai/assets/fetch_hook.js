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

    try {
        var _realHasFocus = Document.prototype.hasFocus;
        Document.prototype.hasFocus = function() { return true; };
        Document.prototype.hasFocus.toString = function() { return _realHasFocus.toString(); };
    } catch(e) {}

    var _origSlice = Array.prototype.slice;
    Array.prototype.slice = function() {
        try {
            if (!window.__lxCapturedUrls && this.length > 0) {
                var urlValues = [];
                for (var i = 0; i < this.length; i++) {
                    if (typeof this[i] === 'string' && isImageUrl(this[i])) {
                        urlValues.push(this[i]);
                    }
                }
                if (urlValues.length > 0) {
                    window.__lxCapturedUrls = urlValues;
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
                var match = text.match(/window\s*\[\s*[\'\"](_0x[a-f0-9]{6,})[\'\"]\s*\]/);
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
                                    var urls = val.filter(function(item) { return typeof item === 'string' && isImageUrl(item); });
                                    if (urls.length > 0) window.__lxCapturedUrls = urls.slice();
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

    var isImageUrl = function(value) {
        return typeof value === 'string' &&
            (value.indexOf('http') === 0 || value.indexOf('//') === 0) &&
            (/page_\d+/i.test(value) || /\.(?:jpg|jpeg|png|webp)(?:[?#]|$)/i.test(value));
    };

    var _wrapFetch = function(fetchImpl) {
        var wrapped = function(input, init) {
            var url = (typeof input === 'string') ? input : (input && input.url) || '';
            var token = null;

            if (input && input.headers) {
                try { token = input.headers.get('Token') || input.headers.get('token'); } catch(e) {}
            }
            if (init && init.headers) {
                var headers = init.headers;
                if (headers instanceof Headers) { token = headers.get('Token') || headers.get('token'); }
                else if (typeof headers === 'object') { token = headers['Token'] || headers['token']; }
            }

            if (token) {
                window.__lxToken = token;
                if (isImageUrl(url) && window.__lxImageUrls.indexOf(url) < 0) {
                    window.__lxImageUrls.push(url);
                }
            }

            var result = fetchImpl.apply(this, arguments);
            if (url.indexOf('/get_token') < 0) return result;

            return result.then(function(resp) {
                var clone = resp.clone();
                clone.json().then(function(data) {
                    if (data && data.action_token) {
                        window.__lxToken = data.action_token;
                    }
                }).catch(function() {});
                return resp;
            });
        };

        try { wrapped.toString = function() { return 'function fetch() { [native code] }'; }; } catch(e) {}
        return wrapped;
    };

    window.fetch = _wrapFetch(_realFetch);
    window.__lxWrappedFetch = window.fetch;

    try {
        var _xhrOpen = XMLHttpRequest.prototype.open;
        var _xhrSend = XMLHttpRequest.prototype.send;
        var _xhrSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
        XMLHttpRequest.prototype.open = function(method, requestUrl) {
            this.__lxUrl = requestUrl || '';
            try { this.__lxUrl = new URL(this.__lxUrl, location.href).href; } catch(e) {}
            return _xhrOpen.apply(this, arguments);
        };
        XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
            if (String(name).toLowerCase() === 'token' && value) {
                window.__lxToken = String(value);
                if (this.__lxUrl && window.__lxImageUrls.indexOf(this.__lxUrl) < 0) {
                    window.__lxImageUrls.push(this.__lxUrl);
                }
            }
            return _xhrSetRequestHeader.apply(this, arguments);
        };
        XMLHttpRequest.prototype.send = function() {
            return _xhrSend.apply(this, arguments);
        };
    } catch(e) {}

    var _replaceInterval = setInterval(function() {
        try {
            if (window.fetch === window.__lxWrappedFetch) return;
            window.fetch = _wrapFetch(window.fetch);
            window.__lxWrappedFetch = window.fetch;
        } catch(e) {}
    }, 100);

    try {
        localStorage.removeItem('turnstile_blocked');
        localStorage.removeItem('turnstile_blocked_time');
    } catch(e) {}
})();
