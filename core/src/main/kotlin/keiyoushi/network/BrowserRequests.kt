package keiyoushi.network

import okhttp3.HttpUrl
import okhttp3.Request

/**
 * How a browser would make a request: its [kind] and the page it comes [from].
 *
 * Pass one to `get`/`post`, e.g. `client.get(url, Fetch.XHR)` or `client.get(url, Fetch.DOCUMENT.from(mangaUrl))`, or
 * set it on a [Request.Builder] with [fetch].
 */
class Fetch private constructor(val kind: Kind, val from: String?) {
    /** Named after the filters of DevTools' Network tab. */
    enum class Kind {
        /** A page load (Doc). */
        DOCUMENT,

        /** The page's own `fetch()`/XHR call, e.g. a JSON API or an ajax endpoint (Fetch/XHR). */
        XHR,

        /** A script load, e.g. a JS bundle read for an API key or a route (JS). */
        SCRIPT,

        /** An image load (Img). */
        IMAGE,

        /** The request goes out as built, without the headers a browser adds. */
        NONE,
    }

    /**
     * The same kind, coming from [page], e.g. the chapter page for the reader's calls; a path is resolved against
     * `baseUrl`. It goes out as `Referer` the way browsers send it: in full to the same origin, as the origin
     * elsewhere, and not at all from https to http.
     */
    fun from(page: String) = Fetch(kind, page)

    /** The same kind, coming from [page], see [from]. */
    fun from(page: HttpUrl) = from(page.toString())

    companion object {
        val DOCUMENT = Fetch(Kind.DOCUMENT, null)
        val XHR = Fetch(Kind.XHR, null)
        val SCRIPT = Fetch(Kind.SCRIPT, null)
        val IMAGE = Fetch(Kind.IMAGE, null)
        val NONE = Fetch(Kind.NONE, null)
    }
}

/** Sends the request the way [fetch] says a browser would. */
fun Request.Builder.fetch(fetch: Fetch): Request.Builder = kind(fetch.kind).tag(FromPage::class.java, fetch.from?.let(::FromPage))

internal fun Request.Builder.kind(kind: Fetch.Kind): Request.Builder = tag(Fetch.Kind::class.java, kind)

/** The page a request comes from, see [Fetch.from]. */
internal class FromPage(val page: String)

internal val Request.kind: Fetch.Kind? get() = tag(Fetch.Kind::class.java)
