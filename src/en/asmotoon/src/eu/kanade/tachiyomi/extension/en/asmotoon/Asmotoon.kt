package eu.kanade.tachiyomi.extension.en.asmotoon

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Asmotoon : Keyoapp() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val client = super
        .client
        .newBuilder()
        .rateLimit(3, 5.seconds) { it.host == baseUrlHost }
        .build()

    // filtering novel entries
    override fun popularMangaSelector() = "div:contains(Trending) + div .group:not([data-type=novel])"
    override fun latestUpdatesSelector() = ".group:not([data-type=novel])"
    override fun searchMangaSelector() = ".group:not([data-type=novel])"

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
            }?.let(::add)
            document.select(genreSelector).forEach { add(it.text().removeSuffix(",")) }
        }.joinToString()
    }
}
