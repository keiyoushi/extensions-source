package eu.kanade.tachiyomi.extension.pt.animaregia

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class AnimaRegia : MMRCMS("AnimaRegia", "https://animaregia.net", "pt-BR") {

    override val id: Long = 4378659695320121364

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    // Remove the language tag from the title name.
    override fun internalMangaParse(response: Response): MangasPage {
        return super.internalMangaParse(response).let {
            it.copy(
                mangas = it.mangas.map { manga ->
                    manga.apply { title = title.removeSuffix(LANGUAGE_SUFFIX) }
                },
            )
        }
    }

    override fun latestUpdatesFromElement(element: Element, urlSelector: String): SManga? {
        return super.latestUpdatesFromElement(element, urlSelector)
            ?.apply { title = title.removeSuffix(LANGUAGE_SUFFIX) }
    }

    override fun gridLatestUpdatesFromElement(element: Element): SManga {
        return super.gridLatestUpdatesFromElement(element)
            .apply { title = title.removeSuffix(LANGUAGE_SUFFIX) }
    }

    // Override searchMangaParse with same body from internalMangaParse since
    // it can use the other endpoint instead.
    override fun searchMangaParse(response: Response): MangasPage {
        return super.searchMangaParse(response).let {
            it.copy(
                mangas = it.mangas.map { manga ->
                    manga.apply { title = title.removeSuffix(LANGUAGE_SUFFIX) }
                },
            )
        }
    }

    // The website modified the information panel.
    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()

        title = document.selectFirst("h1.widget-title")!!.text()
        thumbnail_url = coverGuess(
            document.select("div.col-sm-5 img.img-thumbnail").firstOrNull()?.attr("abs:src"),
            document.location(),
        )
        description = document.select("div.row div.well p")!!.text().trim()

        for (element in document.select("div.col-sm-5 ul.list-group li.list-group-item")) {
            when (element.text().trim().lowercase(BRAZILIAN_LOCALE).substringBefore(":")) {
                "autor(es)" -> author = element.select("a")
                    .joinToString(", ") { it.text().trim() }
                "artist(s)" -> artist = element.select("a")
                    .joinToString(", ") { it.text().trim() }
                "categorias" -> genre = element.select("a")
                    .joinToString(", ") { it.text().trim() }
                "status" -> status = when (element.select("span.label").text()) {
                    "Completo", "Concluído" -> SManga.COMPLETED
                    "Ativo" -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
            }
        }
    }

    override fun chapterListSelector(): String = "div.row ul.chapters > li"

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup()
            .select(chapterListSelector())
            .map { el ->
                SChapter.create().apply {
                    name = el.select("h5.chapter-title-rtl").text()
                    scanlator = el.select("div.col-md-3 ul li")
                        .joinToString(" & ") { it.text().trim() }
                    date_upload = el.select("div.col-md-4").firstOrNull()
                        ?.text()?.removeSuffix("Download")?.toDate() ?: 0L
                    setUrlWithoutDomain(el.select("h5.chapter-title-rtl a").first()!!.attr("href"))
                }
            }
    }

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMAT.parse(trim())?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        private const val LANGUAGE_SUFFIX = " (pt-br)"
        private val BRAZILIAN_LOCALE = Locale("pt", "BR")

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("dd MMM. yyyy", Locale.ENGLISH)
        }
    }
}
