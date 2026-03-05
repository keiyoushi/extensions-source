package eu.kanade.tachiyomi.extension.ar.mangalionz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.text.SimpleDateFormat
import java.util.Locale

class MangaLionz :
    Madara(
        "MangaLionz",
        "https://manga-lionz.org",
        "ar",
        dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("ar")),
    ) {

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val chapterUrlSuffix = ""

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request = if (query.isNotBlank()) {
        POST(
            "$baseUrl/wp-admin/admin-ajax.php",
            headers,
            FormBody
                .Builder()
                .add("action", "wp-manga-search-manga")
                .add("title", query)
                .build(),
        )
    } else {
        super.searchMangaRequest(page, query, filters)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.body.string()

        return try {
            val dto = body.parseAs<SearchResponseDto>()

            if (!dto.success) {
                MangasPage(emptyList(), false)
            } else {
                val manga = dto.data.map {
                    SManga.create().apply {
                        setUrlWithoutDomain(it.url)
                        title = it.title
                    }
                }
                MangasPage(manga, false)
            }
        } catch (_: Exception) {
            super.searchMangaParse(
                response.newBuilder()
                    .body(body.toResponseBody(response.body.contentType()))
                    .build(),
            )
        }
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
}
