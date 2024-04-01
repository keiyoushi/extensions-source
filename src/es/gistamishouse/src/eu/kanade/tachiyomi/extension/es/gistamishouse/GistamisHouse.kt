package eu.kanade.tachiyomi.extension.es.gistamishouse

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.Type
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import okhttp3.Response

class GistamisHouse : ZeistManga(
    "Gistamis House",
    "https://gistamishousefansub.blogspot.com",
    "es",
) {
    override val useNewChapterFeed = true
    override val hasFilters = true
    override val hasLanguageFilter = false

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val excludedCategories = listOf("Anime", "Novela")

    override val popularMangaSelector = "div.PopularPosts div.grid > figure:not(:has(span[data=Capitulo]))"

    override val authorSelectorList = listOf(
        "Author",
        "Autor",
        "Mangaka",
    )

    override val mangaDetailsSelectorAltName = "div.y6x11p:contains(Otros Nombres) > span.dt"
    override val mangaDetailsSelectorInfoTitle = ""

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val profileManga = document.selectFirst(mangaDetailsSelector)!!
        return SManga.create().apply {
            thumbnail_url = profileManga.selectFirst("img")!!.attr("abs:src")
            description = buildString {
                append(profileManga.select(mangaDetailsSelectorDescription).text())
                append("\n\n")
                profileManga.selectFirst(mangaDetailsSelectorAltName)?.text()?.takeIf { it.isNotBlank() }?.let {
                    append("Otros Nombres: ")
                    append(it)
                }
            }.trim()
            genre = profileManga.select(mangaDetailsSelectorGenres)
                .joinToString { it.text() }

            val infoElement = profileManga.select(mangaDetailsSelectorInfo)
            var statusFound = false
            infoElement.forEach { element ->
                val infoText = element.ownText().trim().ifEmpty { element.selectFirst(mangaDetailsSelectorInfoTitle)?.text()?.trim() ?: "" }
                val descText = element.select(mangaDetailsSelectorInfoDescription).text().trim()
                when {
                    statusSelectorList.any { infoText.contains(it) } -> {
                        if (!statusFound) status = parseStatus(descText)
                        statusFound = true
                    }

                    authorSelectorList.any { infoText.contains(it) } -> {
                        author = descText
                    }

                    artisSelectorList.any { infoText.contains(it) } -> {
                        artist = descText
                    }
                }
            }
        }
    }

    override val chapterCategory = ""
    private val chapterCategories = listOf("Capitulo", "Cap")

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val url = getChapterFeedUrl(document)
        val res = client.newCall(GET(url, headers)).execute()

        val result = json.decodeFromString<ZeistMangaDto>(res.body.string())
        return result.feed?.entry?.filter { it.category.orEmpty().any { category -> chapterCategories.contains(category.term) } }
            ?.map { it.toSChapter(baseUrl) }
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
