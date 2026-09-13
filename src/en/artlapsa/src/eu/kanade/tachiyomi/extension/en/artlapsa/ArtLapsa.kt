package eu.kanade.tachiyomi.extension.en.artlapsa

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document

@Source
abstract class ArtLapsa : Keyoapp() {

    override suspend fun requestGeneres() = client.get("$baseUrl/search")

    override fun parseGenres(document: Document) = document.select("[wire:model.live=genre] option:not(:contains(All))").associate {
        it.text() to it.attr("value")
    }

    override fun searchUrlBuilder(query: String, page: Int) = "$baseUrl/search".toHttpUrl().newBuilder().apply {
        if (page > 1) addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            addQueryParameter("title", query)
        }
    }

    override fun searchMangaSelector() = "main#main-content [wire:key*='serie']"

    override fun parseSearchManga(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map(::searchMangaFromElement)
        return MangasPage(mangas, hasNextPage = mangas.size >= 20)
    }

    override val altNameSelector: String = "div.font-medium:containsOwn(Alternative titles) ~ div span.select-all"
    override val statusSelector = "[alt=Status]"
    override val typeSelector = "[alt=Type]"

    override val paidChapterSelector = "img[alt~=Coin], img[src*=star-circle]"

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
