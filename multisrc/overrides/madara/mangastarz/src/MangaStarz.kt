package eu.kanade.tachiyomi.extension.ar.mangastarz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class MangaStarz : Madara(
    "Manga Starz",
    "https://mangastarz.org",
    "ar",
    dateFormat = SimpleDateFormat("d MMMM، yyyy", Locale("ar")),
) {
    override val chapterUrlSuffix = ""

    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"

    override fun popularMangaRequest(page: Int): Request {
        return GET(
            url = "$baseUrl/$mangaSubString/${searchPage(page)}",
            headers = headers,
            cache = CacheControl.FORCE_NETWORK,
        )
    }
    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        POST(
            "$baseUrl/wp-admin/admin-ajax.php",
            headers,
            FormBody.Builder()
                .add("action", "wp-manga-search-manga")
                .add("title", query)
                .build(),
        )

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

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
