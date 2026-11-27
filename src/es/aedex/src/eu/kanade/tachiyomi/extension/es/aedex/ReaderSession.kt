package eu.kanade.tachiyomi.extension.es.aedex

import okhttp3.HttpUrl.Companion.toHttpUrl

// What one visit to the reader hands out. The site forgets visits, but the page list Mihon caches keeps their keys
class ReaderSession(
    val readerUrl: String,
    val keys: VisitKeys,
    private val handles: List<String>,
) {
    val pageCount get() = handles.size

    // The fragment carries the visit's keys, and which page of which chapter this is so a stale one can be renewed
    fun imageUrl(index: Int, chapterKey: String) = readerUrl.toHttpUrl().newBuilder()
        .encodedPath("/lector/p/")
        .addPathSegment(handles[index])
        .addPathSegment("")
        .query(null)
        .addQueryParameter("q", "hq")
        .fragment("$keys:$index:$chapterKey")
        .build()
        .toString()
}

class VisitKeys(val token: String, val seed: String, val liveKey: String, val clockOffset: Long) {
    val signer = ReaderSigner("bv", token, seed)

    override fun toString() = "$token:$seed:$liveKey:$clockOffset"

    companion object {
        fun parse(parts: List<String>) = VisitKeys(parts[0], parts[1], parts[2], parts[3].toLong())
    }
}
