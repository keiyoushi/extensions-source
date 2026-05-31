package eu.kanade.tachiyomi.extension.es.eternalmangas

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.multisrc.iken.Manga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.utils.extractNextJs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Request
import okhttp3.Response

class EternalMangas :
    Iken(
        "EternalMangas",
        "es",
        "https://eternalmangas.org",
        "https://api.eternalmangas.org",
    ) {

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, rscHeaders)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.extractNextJs<PopularPosts>()!!
        val posts = result.todayPosts + result.weekPosts + result.monthPosts
        val mangas = posts.distinctBy { it.slug }.map { it.toSManga().apply { initialized = false } }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/api/posts?page=$page&perPage=$perPage&isNovel=false&tag=latestUpdate", headers)

    @Serializable
    class PopularPosts(
        @SerialName("todayposts") val todayPosts: List<Manga>,
        @SerialName("weekposts") val weekPosts: List<Manga>,
        @SerialName("monthposts") val monthPosts: List<Manga>,
    )
}
