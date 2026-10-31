(function (config, slots) {
    var urls = [];
    for (var i = 0; i < slots; i++) urls.push("");

    globalThis.window = globalThis;
    globalThis.mkjpUrls = urls;
    globalThis.document = {
        getElementById: function (id) {
            return id === "mkjp-reader-cfg" ? { textContent: config } : null;
        },
        querySelectorAll: function (selector) {
            return selector !== ".mkjp-slot" ? [] : urls.map(function (_, i) {
                return {
                    querySelector: function () {
                        return {
                            setAttribute: function (name, value) {
                                if (name === "data-src") urls[i] = value;
                            },
                        };
                    },
                };
            });
        },
    };
    globalThis.atob = function (input) {
        var alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        var output = "", buffer = 0, bits = 0;
        for (var i = 0; i < input.length; i++) {
            var value = alphabet.indexOf(input.charAt(i));
            if (value < 0) continue;
            buffer = (buffer << 6 | value) & 65535;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                output += String.fromCharCode(buffer >> bits & 255);
            }
        }
        return output;
    };
    globalThis.TextDecoder = function () {};
    globalThis.TextDecoder.prototype.decode = function (bytes) {
        return decodeURIComponent(Array.prototype.map.call(bytes, function (byte) {
            return "%" + (byte < 16 ? "0" : "") + byte.toString(16);
        }).join(""));
    };
})
