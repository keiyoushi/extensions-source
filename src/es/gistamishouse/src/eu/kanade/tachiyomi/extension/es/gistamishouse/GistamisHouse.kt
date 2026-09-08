package eu.kanade.tachiyomi.extension.es.gistamishouse

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.Type
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class GistamisHouse : ZeistManga() {
    override val useNewChapterFeed = true
    override val hasFilters = true
    override val hasLanguageFilter = false

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override val popularMangaSelector = "div.PopularPosts div.grid > figure:not(:has(span[data=Capitulo]))"

    override val mangaDetailsSelectorAltName = "div.y6x11p:contains(Otros Nombres) > span.dt"
    override val mangaDetailsSelectorInfoTitle = ""

    override val chapterCategory = ""
    private val chapterCategories = listOf("Capitulo", "Cap")

    override suspend fun getChapterList(feedUrl: String, doc: Document?): List<SChapter> {
        val result = client.get(feedUrl).parseAs<ZeistMangaDto>()

        return result.feed?.entry?.filter { it.category.orEmpty().any { category -> chapterCategories.contains(category.term) } }
            ?.map { it.toSChapter(baseUrl, parseDate(it.getPublishedDate())) }
            ?: throw Exception("Failed to parse from chapter API")
    }

    override val pageListSelector = "article.oh div.post p"

    override fun getGenreList(): List<Genre> = listOf(
        Genre("Acción", "Acción"),
        Genre("Aventura", "Aventura"),
        Genre("Comedia", "Comedia"),
        Genre("Dementia", "Dementia"),
        Genre("Demonios", "Demonios"),
        Genre("Drama", "Drama"),
        Genre("Ecchi", "Ecchi"),
        Genre("Fantasía", "Fantasía"),
        Genre("Videojuegos", "Videojuegos"),
        Genre("Harem", "Harem"),
        Genre("Histórico", "Histórico"),
        Genre("Horror", "Horror"),
        Genre("Josei", "Josei"),
        Genre("Magia", "Magia"),
        Genre("Arte marcial", "Arte marcial"),
        Genre("Mecha", "Mecha"),
        Genre("Militar", "Militar"),
        Genre("Música", "Música"),
        Genre("Misterio", "Misterio"),
        Genre("Parody", "Parody"),
        Genre("Policia", "Policia"),
        Genre("Filosófico", "Filosófico"),
        Genre("Romance", "Romance"),
        Genre("Samurai", "Samurai"),
        Genre("Escolar", "Escolar"),
        Genre("Sci-Fi", "Sci-Fi"),
        Genre("Seinen", "Seinen"),
        Genre("Shoujo", "Shoujo"),
        Genre("GL", "GL"),
        Genre("BL", "BL"),
        Genre("HET", "HET"),
        Genre("Shounen", "Shounen"),
        Genre("Vida cotidiana", "Vida cotidiana"),
        Genre("Espacio", "Espacio"),
        Genre("Deportes", "Deportes"),
        Genre("Super poderes", "Super poderes"),
        Genre("Sobrenatural", "Sobrenatural"),
        Genre("Thriller", "Thriller"),
        Genre("Vampiro", "Vampiro"),
        Genre("Vida laboral", "Vida laboral"),
    )

    override fun getStatusList(): List<Status> = listOf(
        Status("Activo", "Activo"),
        Status("Completo", "Completo"),
        Status("Cancelado", "Cancelado"),
        Status("Futuro", "Futuro"),
        Status("Pausado", "Pausado"),
    )

    override fun getTypeList(): List<Type> = listOf(
        Type("Manga", "Manga"),
        Type("Manhua", "Manhua"),
        Type("Manhwa", "Manhwa"),
    )
}
