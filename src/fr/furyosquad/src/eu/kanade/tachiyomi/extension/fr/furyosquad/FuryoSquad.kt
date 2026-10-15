package eu.kanade.tachiyomi.extension.fr.furyosquad

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class FuryoSquad : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = connectTimeout(10.seconds)
        .readTimeout(30.seconds)
        .rateLimit(1)

    // ========================= Popular =========================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/mangas").asJsoup()
        val mangas = document.select("div#fs-tous div.fs-card-body").mapNotNull { element ->
            val titleElement = element.selectFirst("span.fs-comic-title a") ?: return@mapNotNull null
            SManga.create().apply {
                val rawUrl = element.selectFirst("div.fs-card-img-container a")?.attr("href") ?: return@mapNotNull null
                url = rawUrl.toHttpUrlOrNull()?.encodedPath ?: rawUrl
                title = titleElement.text()
                thumbnail_url = element.selectFirst("div.fs-card-img-container img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    // ========================= Latest =========================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select("table.table-striped tr").mapNotNull { element ->
            val titleElement = element.selectFirst("span.fs-comic-title a") ?: return@mapNotNull null
            SManga.create().apply {
                val rawUrl = titleElement.attr("href")
                url = rawUrl.toHttpUrlOrNull()?.encodedPath ?: rawUrl
                title = titleElement.text()
                thumbnail_url = element.selectFirst("img.fs-chap-img")?.attr("abs:src")
            }
        }.distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    // ========================= Search =========================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.contains("furyosociety.com")) return null

        val path = when {
            url.pathSegments.contains("series") -> url.encodedPath
            url.pathSegments.contains("read") -> {
                val readIndex = url.pathSegments.indexOf("read")
                val mangaSlug = url.pathSegments.getOrNull(readIndex + 1) ?: return null
                "/series/$mangaSlug/"
            }
            else -> return null
        }

        return fetchMangaUpdate(
            manga = SManga.create().apply { this.url = path },
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangasPage = getPopularManga(1)
        val filteredMangas = mangasPage.mangas.filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(filteredMangas, false)
    }

    // ========================= Details =========================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()

        manga.apply {
            title = document.selectFirst("h1.fs-comic-title")?.text() ?: title
            val info = document.selectFirst("div.comic-info") ?: return@apply
            info.select("p.fs-comic-label").forEach { el ->
                when (el.text().lowercase(Locale.ROOT)) {
                    "scénario" -> author = el.nextElementSibling()?.text()
                    "dessins" -> artist = el.nextElementSibling()?.text()
                    "genre" -> genre = el.nextElementSibling()?.text()
                }
            }
            description = info.selectFirst("div.fs-comic-description")?.text()
            thumbnail_url = info.selectFirst("img.comic-cover")?.attr("abs:src")
        }

        val chapterList = document.select("div.fs-chapter-list div.element").map { element ->
            SChapter.create().apply {
                val titleElement = element.selectFirst("div.title a")!!
                val rawUrl = titleElement.attr("href")
                url = rawUrl.toHttpUrlOrNull()?.encodedPath ?: rawUrl
                name = titleElement.attr("title")
                date_upload = parseChapterDate(element.selectFirst("div.meta_r")?.text() ?: "")
            }
        }

        return SMangaUpdate(manga, chapterList)
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // ========================= Pages =========================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        return document.select("div.fs-read img[id]").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

    // ========================= Utilities =========================

    private fun parseChapterDate(date: String): Long {
        val lcDate = date.lowercase(Locale.ROOT)
        if (lcDate.startsWith("il y a")) {
            return parseRelativeDate(lcDate)
        }

        return when {
            lcDate.startsWith("avant-hier") -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -2)
                    setMidnight()
                }.timeInMillis
            }

            lcDate.startsWith("hier") -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -1)
                    setMidnight()
                }.timeInMillis
            }

            lcDate.startsWith("aujourd'hui") -> {
                Calendar.getInstance().apply {
                    setMidnight()
                }.timeInMillis
            }

            else -> {
                val dateText = DATE_EXTRACT_REGEX.find(date)?.groupValues?.get(1) ?: date
                DATE_FORMAT.tryParseDate(dateText)
            }
        }
    }

    private fun parseRelativeDate(date: String): Long {
        val match = RELATIVE_DATE_REGEX.find(date) ?: return 0L
        val value = match.groupValues[1].toIntOrNull() ?: return 0L
        val unit = match.groupValues[2]

        return Calendar.getInstance().apply {
            when (unit) {
                "minute", "minutes" -> add(Calendar.MINUTE, -value)
                "heure", "heures" -> add(Calendar.HOUR_OF_DAY, -value)
                "jour", "jours" -> add(Calendar.DATE, -value)
                "semaine", "semaines" -> add(Calendar.DATE, -value * 7)
                "mois" -> add(Calendar.MONTH, -value)
                "an", "ans", "année", "années" -> add(Calendar.YEAR, -value)
            }
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun Calendar.setMidnight() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    companion object {
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d MMM yyyy")
            .toFormatter(Locale.FRENCH)
        private val RELATIVE_DATE_REGEX = Regex("""il y a (\d+) (\w+)""")
        private val DATE_EXTRACT_REGEX = Regex("""le (.*)""")
    }
}
