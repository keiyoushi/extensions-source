package eu.kanade.tachiyomi.extension.fr.raijinscans

import android.content.SharedPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
class RemoteScriptBasic(
    val validVersion: List<Int>,
)

@Serializable
class RemoteScript(
    val validVersion: List<Int>,
    // Bare filename (no path) of the JS bundle in the same release. The actual `rjGetPages(ctx, host)`
    // source is downloaded separately from RELEASE_BASE + script. See PageListInterpreter for the contract.
    val script: String,
)

/**
 * Loads the page-list reader script. The reader for raijin-scans descrambles a per-page randomized
 * manifest and walks an obfuscated admin-ajax response; the site rotates that obfuscation, so the
 * descrambler lives in an external JS bundle that can be updated without shipping a new APK
 * (same idea as scanmanga's external markers, but executable). The bundle runs inside a sandboxed
 * WebView and talks to okhttp through the [PageListInterpreter] host bridge.
 *
 * The remote manifest ([RemoteScript]) only names a JS *filename*; the source is fetched separately
 * from the same release. The filename is validated to be a bare name (no path) so it cannot point
 * outside the release directory.
 *
 * Resolution order: in-memory cache -> prefs cache (within TTL) -> remote fetch -> [DEFAULT_SCRIPT].
 * The remote manifest is version-gated against [PARSER_VERSION] so an incompatible bundle is ignored.
 */
class ReaderScriptManager(
    private val client: OkHttpClient,
    private val preferences: SharedPreferences,
) {
    private var cachedScript: String? = null

    fun getScript(): String {
        val lastUpdate = preferences.getString(PREF_SCRIPT_LAST_UPDATE, "0")?.toLongOrNull() ?: 0L
        val cachedSource = preferences.getString(PREF_SCRIPT_SOURCE, null)
        val cacheExpired = (System.currentTimeMillis() - lastUpdate) >= CACHE_TTL_MS

        cachedScript?.let { if (!cacheExpired) return it }

        if (cachedSource != null && !cacheExpired) {
            cachedScript = cachedSource
            return cachedSource
        }

        return fetchWithRetry()
    }

    /**
     * Force a fresh fetch, bypassing both caches. Used to recover when a script returned by
     * [getScript] fails at *runtime*: the cached copy may be stale/broken while the remote one has
     * already been fixed. Updates the cache with whatever it resolves (remote or [DEFAULT_SCRIPT]).
     */
    fun refreshScript(): String = fetchWithRetry()

    private fun fetchWithRetry(): String {
        return (1..3).firstNotNullOfOrNull {
            runCatching {
                val manifest = client.newCall(Request.Builder().url(MANIFEST_URL).build()).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val bodyString = response.body.string()

                    // Incompatible bundle: don't retry, fall back to the bundled script.
                    if (PARSER_VERSION !in bodyString.parseAs<RemoteScriptBasic>().validVersion) return DEFAULT_SCRIPT

                    bodyString.parseAs<RemoteScript>()
                }

                val filename = manifest.script
                require(filename.matches(FILENAME_REGEX) && ".." !in filename) {
                    "Rejected reader script filename: $filename"
                }

                client.newCall(Request.Builder().url(RELEASE_BASE + filename).build()).execute().use { jsResponse ->
                    if (!jsResponse.isSuccessful) return@runCatching null

                    jsResponse.body.string().also { source ->
                        cachedScript = source
                        preferences.edit()
                            .putString(PREF_SCRIPT_SOURCE, source)
                            .putString(PREF_SCRIPT_LAST_UPDATE, System.currentTimeMillis().toString())
                            .apply()
                    }
                }
            }.getOrNull()
        } ?: DEFAULT_SCRIPT
    }

    companion object {
        private const val RELEASE_BASE =
            "https://github.com/Starmania/raijin-scans/releases/latest/download/"
        private const val MANIFEST_URL = RELEASE_BASE + "reader.json"

        // Bare filename only: no '/', '\', or other path characters; ".." additionally rejected above.
        private val FILENAME_REGEX = """[A-Za-z0-9._-]+""".toRegex()

        const val PREF_SCRIPT_SOURCE = "external_reader_script_source"
        const val PREF_SCRIPT_LAST_UPDATE = "external_reader_script_last_update"
        private const val CACHE_TTL_MS = 12 * 60 * 60 * 1000L // 12 hours

        private const val PARSER_VERSION = 1

        // Bundled fallback. Mirrors the previous Kotlin descrambler 1:1 (see git history of
        // RaijinScans.pageListParse). Entry point: `rjGetPages(ctx, host)` returning an array of
        // image-url strings. `ctx` carries baseUrl/chapterUrl/html/ajaxHeaders/maxPageRequests;
        // `host.fetch(spec)` performs an okhttp request and resolves to { ok, status, body }.
        val DEFAULT_SCRIPT = """
            async function rjGetPages(ctx, host) {
              var doc = new DOMParser().parseFromString(ctx.html, "text/html");

              var manifestScript = null;
              var scripts = doc.querySelectorAll("script");
              for (var i = 0; i < scripts.length; i++) {
                var t = scripts[i].textContent || "";
                if (t.indexOf("rjfr_") >= 0) { manifestScript = t; break; }
              }
              if (!manifestScript) throw new Error("No reader manifest found. Open the chapter in WebView.");

              // The manifest object is injected either as `.push({...,"m":...,"c":{...}})` or as
              // `window["rjfr_..."][...length]={...,"m":...,"c":{...}}`. The first key is randomized
              // (e.g. {"rj<hex>":1,"m":...}), so anchor on the "m" key and walk back to the enclosing
              // "{" rather than requiring "m" to be first, then brace-match forward (respecting strings).
              var mKey = manifestScript.search(/"m"\s*:/);
              if (mKey < 0) throw new Error("Invalid manifest format");
              var start = manifestScript.lastIndexOf("{", mKey);
              if (start < 0) throw new Error("Invalid manifest format");
              var manifest = JSON.parse(extractObject(manifestScript, start));

              // config = manifest.m.split("|").map(k => manifest.c[k]).join("") -> base64 -> JSON
              var mOrder = manifest.m.split("|");
              var cObj = manifest.c;
              var b64 = mOrder.map(function (k) { return cObj[k]; }).join("");
              var config = JSON.parse(decodeBase64(b64));

              // Two permutations: ordered[m[i]] = d[i]; then vals[i] = ordered[l[i]].
              var shuffled = config.d;
              var perm = config.m;
              var order = config.l;
              var ordered = new Array(shuffled.length);
              perm.forEach(function (p, i) { ordered[p] = shuffled[i]; });
              var vals = order.map(function (o) {
                if (ordered[o] === undefined) throw new Error("Reader manifest layout changed. Open the chapter in WebView.");
                return ordered[o];
              });

              // action = the only "rjfr_" string; keyArr = field names; contentValues = vals[1..6].
              var action = vals.filter(function (v) { return typeof v === "string" && v.indexOf("rjfr_") === 0; })[0];
              var keyArr = vals[13];
              var contentValues = [vals[1], vals[2], vals[3], vals[4], vals[5], vals[6]];

              var rootEl = doc.querySelector("[data-rj-free-reader-root]");
              var rjfrValue = rootEl ? rootEl.getAttribute("data-rj-free-reader-root") : "";

              var pages = [];
              var cursor = "";
              var run = true;
              var guard = 0;

              while (run && guard++ < ctx.maxPageRequests) {
                var multipart = [["action", action]];
                contentValues.forEach(function (v, j) { multipart.push([keyArr[j], String(v)]); });
                multipart.push([keyArr[6], String(pages.length)]); // count of pages already loaded
                multipart.push([keyArr[7], "0"]); // offset, always 0
                multipart.push([keyArr[8], rjfrValue]);
                multipart.push([keyArr[9], cursor]);

                var resp = await host.fetch({
                  url: ctx.baseUrl + "/wp-admin/admin-ajax.php",
                  method: "POST",
                  headers: ctx.ajaxHeaders,
                  multipart: multipart,
                });
                if (!resp.ok) throw new Error("Failed to get page: " + resp.status);

                var root = JSON.parse(resp.body);
                var found = findImages(root);
                if (!found) throw new Error("Reader response format changed. Open the chapter in WebView.");

                found.images.forEach(function (img) {
                  var u = imageUrlOrNull(img);
                  if (u) pages.push(u);
                });

                // cursor = payload's only string primitive; run = its only boolean primitive.
                var pv = Object.keys(found.payload).map(function (k) { return found.payload[k]; });
                cursor = pv.filter(function (x) { return typeof x === "string"; })[0] || "";
                run = pv.filter(function (x) { return typeof x === "boolean"; })[0] || false;
              }

              return pages;

              function decodeBase64(s) {
                s = s.replace(/=+${'$'}/, "");
                while (s.length % 4) s += "=";
                return decodeURIComponent(escape(atob(s)));
              }

              // Brace-match a JSON object starting at `start`, ignoring braces inside string literals.
              function extractObject(s, start) {
                var depth = 0, inStr = false, esc = false;
                for (var i = start; i < s.length; i++) {
                  var ch = s[i];
                  if (inStr) {
                    if (esc) esc = false;
                    else if (ch === "\\") esc = true;
                    else if (ch === '"') inStr = false;
                  } else if (ch === '"') inStr = true;
                  else if (ch === "{") depth++;
                  else if (ch === "}") { depth--; if (depth === 0) return s.slice(start, i + 1); }
                }
                throw new Error("Unterminated manifest object");
              }

              // The real image url = the http string whose path is an actual image (a decoy http
              // string to the admin-ajax endpoint also lives in each object).
              function imageUrlOrNull(obj) {
                if (!obj || typeof obj !== "object" || Array.isArray(obj)) return null;
                var keys = Object.keys(obj);
                for (var i = 0; i < keys.length; i++) {
                  var v = obj[keys[i]];
                  if (typeof v === "string" && v.indexOf("http") === 0 && /\.(webp|jpe?g|png|gif|avif)/i.test(v)) return v;
                }
                return null;
              }
              function isImageArray(arr) {
                return Array.isArray(arr) && arr.length > 0 && imageUrlOrNull(arr[0]) !== null;
              }
              // Find the image-object array anywhere in the tree, with its parent object.
              function findImages(el) {
                if (el && typeof el === "object" && !Array.isArray(el)) {
                  var keys = Object.keys(el);
                  for (var i = 0; i < keys.length; i++) {
                    if (isImageArray(el[keys[i]])) return { payload: el, images: el[keys[i]] };
                  }
                  for (var j = 0; j < keys.length; j++) {
                    var r = findImages(el[keys[j]]);
                    if (r) return r;
                  }
                } else if (Array.isArray(el)) {
                  for (var k = 0; k < el.length; k++) {
                    var r2 = findImages(el[k]);
                    if (r2) return r2;
                  }
                }
                return null;
              }
            }
        """.trimIndent()
    }
}
