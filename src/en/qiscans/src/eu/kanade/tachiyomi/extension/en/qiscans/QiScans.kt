package eu.kanade.tachiyomi.extension.en.qiscans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.multisrc.iken.Images
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.extractNextJs
import okhttp3.Response
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

        val images = document.extractNextJs<Images>()
            ?: if (document.selectFirst("svg.lucide-lock") != null) {
                throw Exception("Paid chapter unavailable.")
            } else {
                throw Exception("Unable to retrieve NEXT data")
            }

        return images.images
            .sortedBy { it.order ?: Int.MAX_VALUE }
            .mapIndexed { idx, p ->
                Page(idx, imageUrl = p.url.replace(" ", "%20"))
            }
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
