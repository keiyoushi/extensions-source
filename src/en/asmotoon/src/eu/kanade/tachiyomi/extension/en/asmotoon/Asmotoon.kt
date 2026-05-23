package eu.kanade.tachiyomi.extension.en.asmotoon

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.util.Locale

class Asmotoon :
    Keyoapp(
        "Asmodeus Scans",
        "https://asmotoon.com",
        "en",
    ) {
    override val client = super
        .client
        .newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 5)
        .build()

    // filtering novel entries
    override fun popularMangaSelector() = "div:contains(Trending) + div .group:not([data-type=novel])"
    override fun latestUpdatesSelector() = ".group:not([data-type=novel])"
    override fun searchMangaSelector() = ".group:not([data-type=novel])"

    override val descriptionSelector: String = "#expand_content"
    override val genreSelector: String = ".gap-3 .gap-1 a"

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        genre = buildList {
            document.selectFirst(typeSelector)?.text()?.replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase(
                        Locale.ENGLISH,
                    )
                } else {
                    it.toString()
                }
            }.let(::add)
            document.select(genreSelector).forEach { add(it.text().removeSuffix(",")) }
        }.joinToString()
    }
}
