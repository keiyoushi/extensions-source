package eu.kanade.tachiyomi.extension.vi.lxhentai

import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.runWebView
import kotlin.time.Duration.Companion.seconds

/** Loads chapter in WebView, solves Cloudflare Turnstile, decodes obfuscated image URLs. */
object TokenResolver {

    class Result(val token: String = "", val srcs: List<String> = emptyList())

    private const val MAX_ATTEMPTS = 2

    suspend fun resolve(chapterUrl: String): Result {
        repeat(MAX_ATTEMPTS) {
            try {
                return resolveOnce(chapterUrl)
            } catch (_: WebViewTimeoutException) {
            }
        }
        return Result()
    }

    private suspend fun resolveOnce(chapterUrl: String): Result {
        val payloadLock = Any()
        var resolved = false
        var latestResult: Result? = null

        return runWebView(timeout = 45.seconds) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            blockImages = false
            userAgent = userAgent.replace(webViewTokenRegex, ")")

            poll(1.seconds) {
                if (resolved) return@poll

                evaluateJs(
                    """(function(){
                        var b=document.querySelector('.swal2-confirm');
                        if(b && !b.disabled && b.textContent.includes('tiếp tục')) b.click();
                    })()""",
                )

                evaluateJs(CHECK_AND_DECODE_SCRIPT) { value ->
                    synchronized(payloadLock) {
                        val result = parseResult(value) ?: return@evaluateJs
                        latestResult = result
                        resolved = true
                    }
                }

                val result = synchronized(payloadLock) { latestResult }
                if (result != null) {
                    resolve(result)
                }
            }

            loadUrl(chapterUrl)
        }
    }

    private fun parseResult(value: String): Result? {
        val cleaned = value.trim().removeSurrounding("\"").removeSurrounding("'")
        if (cleaned.isEmpty() || cleaned == "null" || cleaned == "[]") return null

        return try {
            val json = cleaned
                .removePrefix("Object {").removeSuffix("}")
                .replace("Object {", "{")
            val tokenMatch = Regex(""""token"\s*:\s*"([^"]*)"""").find(json)
            val urlsMatch = Regex(""""urls"\s*:\s*\[([^\]]*)\]""").find(json)

            val token = tokenMatch?.groupValues?.get(1) ?: return null
            val urlsRaw = urlsMatch?.groupValues?.get(1) ?: return null

            val urls = Regex(""""([^"]*http[^"]*)"""").findAll(urlsRaw)
                .map { it.groupValues[1] }
                .toList()

            if (token.isNotEmpty() && urls.isNotEmpty()) Result(token, urls) else null
        } catch (_: Exception) {
            null
        }
    }

    private val webViewTokenRegex = Regex("""\;\s*wv\)""")

    private const val CHECK_AND_DECODE_SCRIPT = """(function(){
        try {
            // Dismiss Turnstile dialog
            var b = document.querySelector('.swal2-confirm');
            if (b && !b.disabled && b.textContent.indexOf('tiếp tục') >= 0) b.click();

            // Check token
            var t = window.actionToken;
            if (!t || typeof t !== 'string' || t.length === 0) return JSON.stringify({token:'',urls:[]});

            // Decode image URLs from obfuscated inline script
            var scripts = document.querySelectorAll('script:not([src])');
            var target = null;
            for (var i = 0; i < scripts.length; i++) {
                var txt = scripts[i].textContent;
                if (txt.indexOf('["KGZ1') >= 0 || txt.indexOf('=\["KGZ1') >= 0) {
                    target = txt;
                    break;
                }
            }
            if (!target) return JSON.stringify({token:t,urls:[]});

            // Layer 1: base64 array → string
            var b64Match = target.match(/=\[((?:"[A-Za-z0-9+/=]{20,}",?\s*)+)\]/);
            if (!b64Match) return JSON.stringify({token:t,urls:[]});
            var parts = b64Match[1].match(/"([^"]+)"/g);
            if (!parts) return JSON.stringify({token:t,urls:[]});
            var joined = parts.map(function(s){return s.replace(/"/g,'');}).join('');
            var raw = atob(joined);
            var layer1;
            try { layer1 = decodeURIComponent(escape(raw)); } catch(e2) { layer1 = raw; }

            // Layer 2: hex key + numeric arrays → XOR decode
            var key2Match = layer1.match(/var _\w+='([0-9a-f]{20,})'/);
            if (!key2Match) return JSON.stringify({token:t,urls:[]});
            var key2 = key2Match[1];
            var arrRe = /var _\w+=\[((?:-?\d+,?)*)\]/g;
            var combined = [];
            var m;
            while ((m = arrRe.exec(layer1)) !== null) {
                var nums = m[1].split(',').filter(function(s){return s.length>0;}).map(Number);
                combined = combined.concat(nums);
            }
            if (combined.length === 0) return JSON.stringify({token:t,urls:[]});
            var decoded = '';
            for (var i = 0; i < combined.length; i++) {
                decoded += String.fromCharCode((combined[i] ^ key2.charCodeAt(i % key2.length)) & 0xFF);
            }

            // Layer 3: hex key + base64 JSON → image URLs
            var key3Match = decoded.match(/var _\w+="([0-9a-f]{20,})"/);
            if (!key3Match) return JSON.stringify({token:t,urls:[]});
            var key3 = key3Match[1];
            var jsonB64Match = decoded.match(/var _\w+="([A-Za-z0-9+/=]{50,})"/);
            if (!jsonB64Match) return JSON.stringify({token:t,urls:[]});
            var jsonArr = JSON.parse(atob(jsonB64Match[1]));
            var urls = [];
            for (var j = 0; j < jsonArr.length; j++) {
                var item;
                try { item = decodeURIComponent(escape(atob(jsonArr[j]))); }
                catch(e3) { item = atob(jsonArr[j]); }
                var url = '';
                for (var k = 0; k < item.length; k++) {
                    url += String.fromCharCode((item.charCodeAt(k) ^ key3.charCodeAt(k % key3.length)) & 0xFF);
                }
                if (url.indexOf('http') === 0 && urls.indexOf(url) < 0) urls.push(url);
            }
            return JSON.stringify({token:t,urls:urls});
        } catch(e) { return JSON.stringify({token:'',urls:[]}); }
    })()"""
}
