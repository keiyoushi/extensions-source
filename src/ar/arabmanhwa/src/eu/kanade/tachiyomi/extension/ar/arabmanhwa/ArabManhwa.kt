package eu.kanade.tachiyomi.extension.ar.arabmanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response

@Source
class ArabManhwa : Madara("ArabManhwa", "https://arabmanhwa.com", "ar") {

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun popularMangaSelector() = "article.page-item-detail, .manga__item"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaNextPageSelector(): String = "div.nav-previous, nav.navigation-ajax, a.nextpostslink, wp-pagenavi .next"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = POST(
        "$baseUrl/wp-admin/admin-ajax.php",
        headers,
        FormBody.Builder()
            .add("action", "wp-manga-search-manga")
            .add("title", query)
            .build(),
    )

    private inline fun <reified T> Response.parseAs(): T = json.decodeFromString(body.string())

    @Serializable
    data class SearchResponseDto(
        val data: List<SearchEntryDto>,
        val success: Boolean,
    )

    @Serializable
    data class SearchEntryDto(
        val url: String = "",
        val title: String = "",
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchResponseDto>()

        if (!dto.success) {
            return MangasPage(emptyList(), false)
        }

        val manga = dto.data.map {
            SManga.create().apply {
                setUrlWithoutDomain(it.url)
                title = it.title
            }
        }

        return MangasPage(manga, false)
    }
}
