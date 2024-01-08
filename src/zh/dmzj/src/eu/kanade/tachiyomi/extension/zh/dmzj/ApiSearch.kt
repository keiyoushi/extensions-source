package eu.kanade.tachiyomi.extension.zh.dmzj

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

object ApiSearch {

    fun textSearchUrl(query: String) =
        "http://sacg.idmzj.com/comicsum/search.php".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .toString()

    fun parsePage(response: Response): MangasPage {
        if (!response.isSuccessful) {
            response.close()
            return MangasPage(emptyList(), false)
        }
        // "var g_search_data = [...];"
        val js = response.body.string().run { substring(20, length - 1) }
        val data: List<MangaDto> = json.decodeFromString(js)
        return MangasPage(data.map { it.toSManga() }, false)
    }

    @Serializable
    class MangaDto(
        private val id: Int,
        private val comic_name: String,
        private val comic_author: String,
        private val comic_cover: String,
    ) {
        fun toSManga() = SManga.create().apply {
            url = getMangaUrl(id.toString())
            title = comic_name
            author = comic_author.formatList()
            thumbnail_url = comic_cover
        }
    }
}
