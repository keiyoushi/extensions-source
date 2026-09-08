package eu.kanade.tachiyomi.extension.en.ritharscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class RitharScans : Keyoapp() {

    override suspend fun requestGeneres() = client.get("$baseUrl/search")

    override fun parseGenres(document: Document) = document.select("[x-data*=genre] button").associate {
        it.text() to it.attr("wire:key")
    }

    override fun searchUrlBuilder(query: String, page: Int) = "$baseUrl/search".toHttpUrl().newBuilder().apply {
        if (query.isNotBlank()) {
            addQueryParameter("title", query)
        }
    }

    // Server-side
    override fun Element.matchesGenres(genres: List<String>) = true
    override fun Element.matchesStatuses(statuses: List<String>) = true

    override fun searchMangaSelector() = "[wire:snapshot*=pages.search] button[tags]"

    override val altNameSelector: String = "div.font-medium:containsOwn(Alternative titles) ~ div span.select-all"
    override val statusSelector = "[alt=Status]"
    override val typeSelector = "[alt=Type]"

    override fun pageListParse(document: Document): List<Page> {
        val data = document.selectFirst("script[type=\"application/ld+json\"]")!!.data().parseAs<ChapterLD>()
        val chapterID = data.url.substringAfterLast('/')
        val seriesID = data.isPartOf.url.substringAfterLast('/')

        return (1..data.numberOfPages).mapIndexed { i, page ->
            Page(
                i,
                url = document.location(),
                imageUrl = "$baseUrl/storage/series/webtoon/$seriesID/chapters/$chapterID/${page.toString().padStart(3, '0')}.jpg",
            )
        }
    }

    override fun getTypeList() = emptyMap<String, String>()
}

@Serializable
internal class ChapterLD(
    val isPartOf: SeriesLD,
    val numberOfPages: Int,
    val url: String,
)

@Serializable
internal class SeriesLD(
    val url: String,
)
