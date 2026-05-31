package eu.kanade.tachiyomi.extension.all.ninemanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NineMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NineMangaEn(),
        NineMangaEs(),
        NineMangaBr(),
        NineMangaRu(),
        NineMangaDe(),
        NineMangaIt(),
        NineMangaFr(),
    )
}

class NineMangaEn : NineManga("NineMangaEn", "https://www.ninemanga.com", "en") {
    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("a.bookname")?.let {
            url = it.attr("abs:href").substringAfter("ninemanga.com")
            title = it.text()
        }
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }
}

class NineMangaEs : NineManga("NineMangaEs", "https://es.ninemanga.com", "es") {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = super.searchMangaRequest(page, query.substringBefore("\'"), filters)

    override fun parseStatus(status: String) = when {
        status.contains("En curso") -> SManga.ONGOING
        status.contains("Completado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val headers = headers.newBuilder()
            .set("Referer", "$baseUrl/")
            .build()

        return GET(baseUrl + chapter.url, headers)
    }

    private val imgRegex = Regex("""all_imgs_url\s*:\s*\[\s*([^]]*)\s*,\s*]""")
    private val redirectRegex = Regex("""window\.location\.href\s*=\s*["'](.*?)["']""")

    override fun pageListParse(document: Document): List<Page> {
        val serverUrl = document.selectFirst("section.section div.post-content-body > a")?.absUrl("href")

        if (serverUrl != null) {
            val serverHeaders = headers.newBuilder()
                .set("Referer", document.baseUri())
                .build()
            return pageListParse(client.newCall(GET(serverUrl, serverHeaders)).execute().asJsoup())
        }

        val redirectScript = document.selectFirst("body > script:containsData(window.location.href)")?.data()

        if (redirectScript != null) {
            val documentLocation = document.location()
            val redirectUrl = redirectRegex.find(redirectScript)
                ?.groupValues?.get(1)
                ?.let { path ->
                    path.toHttpUrlOrNull()
                        ?: documentLocation.toHttpUrl().newBuilder()
                            .encodedPath(path)
                            .build()
                } ?: return super.pageListParse(document)

            val headers = headers.newBuilder()
                .set("Referer", documentLocation)
                .build()

            val redirectedDocument = client.newCall(
                GET(redirectUrl, headers),
            ).execute().asJsoup()

            return pageListParse(redirectedDocument)
        }

        val script = document.selectFirst("script:containsData(all_imgs_url)")?.data()
            ?: return super.pageListParse(document)

        val images = imgRegex.find(script)?.groupValues?.get(1)
            ?.let { "[$it]".parseAs<List<String>>() }
            ?: throw Exception("Image list not found")

        return images.mapIndexed { idx, img ->
            Page(idx, imageUrl = img)
        }
    }

    override fun parseChapterDate(date: String) = parseChapterDateByLang(date)

    override fun getGenreList(): List<Genre> = esGenres
}

class NineMangaBr : NineManga("NineMangaBr", "https://br.ninemanga.com", "pt-BR") {

    override val id: Long = 7162569729467394726

    override fun parseStatus(status: String) = when {
        status.contains("Em tradução") -> SManga.ONGOING
        status.contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String) = parseChapterDateByLang(date)

    override fun getGenreList(): List<Genre> = brGenres
}

class NineMangaRu : NineManga("NineMangaRu", "https://ru.ninemanga.com", "ru") {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = super.searchMangaRequest(page, query.substringBefore("\'"), filters)

    override fun parseStatus(status: String) = when {
        status.contains("завершенный") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String) = parseChapterDateByLang(date)

    override fun getGenreList(): List<Genre> = ruGenres
}

class NineMangaDe : NineManga("NineMangaDe", "https://de.ninemanga.com", "de") {
    override fun parseStatus(status: String) = when {
        status.contains("Laufende") -> SManga.ONGOING
        status.contains("Abgeschlossen") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String) = parseChapterDateByLang(date)

    override fun getGenreList(): List<Genre> = deGenres
}

class NineMangaIt : NineManga("NineMangaIt", "https://it.ninemanga.com", "it") {
    override fun parseStatus(status: String) = when {
        status.contains("In corso") -> SManga.ONGOING
        status.contains("Completato") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String) = parseChapterDateByLang(date)

    override fun getGenreList(): List<Genre> = itGenres
}

class NineMangaFr : NineManga("NineMangaFr", "https://fr.ninemanga.com", "fr") {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = super.searchMangaRequest(page, query.substringBefore("\'"), filters)

    override fun parseStatus(status: String) = when {
        status.contains("En cours") -> SManga.ONGOING
        status.contains("Complété") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String) = parseChapterDateByLang(date)

    override fun getGenreList(): List<Genre> = frGenres
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

fun parseChapterDateByLang(date: String): Long {
    val dateWords = date.split(" ")

    if (dateWords.size == 3) {
        if (dateWords[1].contains(",")) {
            return dateFormat.tryParse(date)
        } else {
            val timeAgo = dateWords[0].toIntOrNull() ?: return 0L
            val calField = when (dateWords[1]) {
                "minutos", "минут", "minuti", "minutes" -> Calendar.MINUTE
                "horas", "hora", "часа", "Stunden", "ore", "heures" -> Calendar.HOUR
                else -> return 0L
            }
            return Calendar.getInstance().apply {
                add(calField, -timeAgo)
            }.timeInMillis
        }
    }
    return 0L
}
