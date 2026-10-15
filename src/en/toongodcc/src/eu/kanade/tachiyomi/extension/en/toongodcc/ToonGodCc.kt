package eu.kanade.tachiyomi.extension.en.toongodcc

import eu.kanade.tachiyomi.multisrc.yiimanga.Yiimanga
import keiyoushi.annotation.Source
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class ToonGodCc : Yiimanga() {
    override val mangaPath = "webtoon"

    override val popularPath = "webtoons"

    override val supportsFilterFetching = true

    override fun genreUrl(genre: String, page: Int): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("genre")
        .addPathSegment(genre)
        .addPathSegment(page.toString())
        .addPathSegment("")
        .build()
}
