package eu.kanade.tachiyomi.extension.zh.bakamh

import app.cash.quickjs.QuickJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString

internal const val READER_CONFIG_SELECTOR = "script#mkjp-reader-cfg"
internal const val READER_SLOT_SELECTOR = ".mkjp-slot"

/** The site's reader script, which holds the rotating table that decodes the reader config's page list. */
internal class ReaderScript(val url: String, private val script: String) {

    fun pageUrls(config: String, slots: Int): List<String> {
        val urls = QuickJs.create().use { js ->
            js.evaluate(domStub(config.toJsonString(), slots))
            // The script stops with an error at its layout code, after it has filled every slot
            runCatching { js.evaluate(script) }
            js.evaluate("JSON.stringify(mkjpUrls)") as String
        }.parseAs<List<String>>()
        check(urls.isNotEmpty() && urls.all(String::isNotEmpty)) { "Failed to read the page list" }
        return urls
    }

    private fun domStub(config: String, slots: Int) = """
        var window = globalThis, mkjpUrls = [];
        for (var i = 0; i < $slots; i++) mkjpUrls.push("");
        var document = {
            getElementById: function (id) {
                return id === "mkjp-reader-cfg" ? { textContent: $config } : null;
            },
            querySelectorAll: function (selector) {
                return selector !== "$READER_SLOT_SELECTOR" ? [] : mkjpUrls.map(function (_, i) {
                    return {
                        querySelector: function () {
                            return {
                                setAttribute: function (name, value) {
                                    if (name === "data-src") mkjpUrls[i] = value;
                                },
                            };
                        },
                    };
                });
            },
        };
        function atob(input) {
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
        }
        function TextDecoder() {}
        TextDecoder.prototype.decode = function (bytes) {
            return decodeURIComponent(Array.prototype.map.call(bytes, function (byte) {
                return "%" + (byte < 16 ? "0" : "") + byte.toString(16);
            }).join(""));
        };
    """.trimIndent()
}
