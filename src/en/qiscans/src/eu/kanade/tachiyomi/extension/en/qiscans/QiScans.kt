package eu.kanade.tachiyomi.extension.en.qiscans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
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

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = super.fetchMangaDetails(manga).map(::normalizeMangaTextFields)

    override fun pageListParse(response: Response): List<Page> = try {
        super.pageListParse(response)
    } catch (e: Exception) {
        if (e.message == "Unlock chapter in webview") {
            throw Exception("Paid chapter unavailable.")
        }
        throw e
    }

    private fun normalizeMangaTextFields(manga: SManga): SManga {
        manga.title = decodeHtmlEntities(manga.title)
        manga.author = manga.author?.let(::decodeHtmlEntities)
        manga.artist = manga.artist?.let(::decodeHtmlEntities)
        manga.description = manga.description?.let(::decodeHtmlEntities)
        manga.genre = manga.genre?.let(::decodeHtmlEntities)
        return manga
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
