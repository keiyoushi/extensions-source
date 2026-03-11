package eu.kanade.tachiyomi.extension.en.qiscans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.multisrc.iken.Images
import eu.kanade.tachiyomi.multisrc.iken.Options
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.extractNextJs
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import rx.Observable
import java.util.concurrent.TimeUnit

class QiScans :
    Iken(
        "Qi Scans",
        "en",
        "https://qimanhwa.com",
        "https://api.qimanhwa.com",
    ) {

    override val client = super.client.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .build()

    override val statusFilterOptions: Options =
        listOf(
            "All" to "",
            "Ongoing" to "ONGOING",
            "Hiatus" to "HIATUS",
            "Dropped" to "DROPPED",
            "Completed" to "COMPLETED",
        )

    override val typeFilterOptions: Options = emptyList()

    override val sortOptions: Options =
        listOf(
            "Latest" to "lastChapterAddedAt",
            "Most Views" to "totalViews",
            "Newly Added" to "createdAt",
            "Title" to "postTitle",
        )

    override val sortDirectionOptions: Options =
        listOf(
            "Descending" to "desc",
            "Ascending" to "asc",
        )

    override fun searchMangaParse(response: Response): MangasPage = super.searchMangaParse(response).apply {
        mangas.forEach(::normalizeMangaTextFields)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = super.fetchMangaDetails(manga).map {
        it.apply(::normalizeMangaTextFields)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        if (document.select("#publicSalt, #challenge").isNotEmpty()) {
            throw Exception("vShield challenge detected. Open in WebView to solve it.")
        }

        if (document.isLockedChapterPage()) {
            throw Exception("Paid chapter unavailable.")
        }

        val images = document.extractNextJs<Images>() ?: throw Exception("Unable to retrieve NEXT data")

        return images.images
            .sortedBy { it.order ?: Int.MAX_VALUE }
            .mapIndexed { idx, p ->
                Page(idx, imageUrl = p.url.replace(" ", "%20"))
            }
    }

    private fun Document.isLockedChapterPage(): Boolean {
        if (selectFirst("svg.lucide-lock") != null) return true

        val text = body().text()
        return text.contains("unlock chapter", ignoreCase = true) ||
            text.contains("chapter locked", ignoreCase = true) ||
            text.contains("paid chapter", ignoreCase = true) ||
            text.contains("purchase", ignoreCase = true) ||
            text.contains("coins", ignoreCase = true)
    }

    private fun normalizeMangaTextFields(manga: SManga) {
        manga.title = decodeHtmlEntities(manga.title)
        manga.author = manga.author?.let(::decodeHtmlEntities)
        manga.artist = manga.artist?.let(::decodeHtmlEntities)
        manga.description = manga.description?.let(::decodeHtmlEntities)
        manga.genre = manga.genre?.let(::decodeHtmlEntities)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_CHAPTER_PREF_KEY
            title = "Display paid chapters"
            summaryOn = "Paid chapters will appear."
            summaryOff = "Only free chapters will be displayed."
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    companion object {
        private fun decodeHtmlEntities(value: String): String = Parser.unescapeEntities(value, false)
    }
}
