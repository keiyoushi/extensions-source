package eu.kanade.tachiyomi.extension.id.komikrealm

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.Type
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaIntl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import uy.kohesive.injekt.injectLazy

class KomikRealm : ZeistManga(
    "KomikRealm",
    "https://www.komikrealm.my.id",
    "id",
) {
    private val json: Json by injectLazy()

    override val hasFilters = true

    override val hasLanguageFilter = false

    override val chapterCategory = ""

    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl("Project")
            .addQueryParameter("orderby", "updated")
            .addQueryParameter("max-results", "12")
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val jsonString = response.body.string()
        val result = json.decodeFromString<ZeistMangaDto>(jsonString)

        val mangas = result.feed?.entry.orEmpty()
            .filter { it.category.orEmpty().any { category -> category.term == "Series" } }
            .filter { !it.category.orEmpty().any { category -> category.term == "Anime" } }
            .map { it.toSManga(baseUrl) }

        return MangasPage(mangas, false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val profileManga = document.select(".bigcontent")
        val infoManga = profileManga.select("ul.infonime li")

        return SManga.create().apply {
            thumbnail_url = profileManga.select("img").first()!!.attr("data-src")
            description = profileManga.select(".sinoposis").text()
            genre = profileManga.select("div.info-genre > a[rel=tag]")
                .joinToString { it.text() }

            infoManga.forEach {
                val title = it.select("b").text()
                val desc = it.select("span").text()

                when (title) {
                    "Status" -> status = parseStatus(desc)
                    "Author" -> author = desc
                    "Artist" -> artist = desc
                }
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val url = getChapterFeedUrl(document)

        val req = GET(url, headers)
        val res = client.newCall(req).execute()

        val jsonString = res.body.string()
        val result = json.decodeFromString<ZeistMangaDto>(jsonString)

        return result.feed?.entry
            ?.filter {
                !it.category.orEmpty().any { category ->
                    category.term == "Series"
                }
            }
            ?.map { it.toSChapter(baseUrl) }
            ?: throw Exception("Failed to parse from chapter API")
    }

    private val imagePageRegex = """(http|https)://[^"]+""".toRegex()

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.select(".post-body > script").html()
        val matches = imagePageRegex.findAll(script)
        return matches.mapIndexed { i, match ->
            Page(i, "", match.value)
        }.toList()
    }

    private val labelChapterRegex = """var label_chapter = "([^"]+)";""".toRegex()

    override fun getChapterFeedUrl(doc: Document): String {
        val script = doc.select(".post-body > script")
        val feed = labelChapterRegex.find(script.html())
            ?.groupValues?.get(1)
            ?: throw Exception("Failed to find chapter feed")

        return apiUrl(chapterCategory)
            .addPathSegments(feed)
            .addQueryParameter("max-results", "999999")
            .build().toString()
    }

    private val intl by lazy { ZeistMangaIntl(lang) }

    override fun getStatusList(): List<Status> = listOf(
        Status(intl.all, ""),
        Status(intl.statusOngoing, "Ongoing"),
        Status(intl.statusCompleted, "Completed"),
    )

    override fun getTypeList(): List<Type> = listOf(
        Type(intl.all, ""),
        Type(intl.typeManga, "Manga"),
        Type(intl.typeManhua, "Manhua"),
        Type(intl.typeManhwa, "Manhwa"),
    )

    override fun getGenreList(): List<Genre> = listOf(
        Genre("Drama", "Drama"),
        Genre("Mature", "Mature"),
        Genre("Supernatural", "Supernatural"),
    )
}
