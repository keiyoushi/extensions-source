package eu.kanade.tachiyomi.multisrc.mangareader

import android.app.Application
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

abstract class MangaReader : HttpSource(), ConfigurableSource {

    override val supportsLatest = true

    final override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    final override fun popularMangaParse(response: Response) = searchMangaParse(response)

    final override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        var entries = document.select(searchMangaSelector()).map(::searchMangaFromElement)
        if (preferences.getBoolean(SHOW_VOLUME_PREF, false)) {
            entries = entries.flatMapTo(ArrayList(entries.size * 2)) { manga ->
                val volume = SManga.create().apply {
                    url = manga.url + VOLUME_URL_SUFFIX
                    title = VOLUME_TITLE_PREFIX + manga.title
                    thumbnail_url = manga.thumbnail_url
                }
                listOf(manga, volume)
            }
        }
        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null
        return MangasPage(entries, hasNextPage)
    }

    final override fun getMangaUrl(manga: SManga) = baseUrl + manga.url.removeSuffix(VOLUME_URL_SUFFIX)

    abstract fun searchMangaSelector(): String

    abstract fun searchMangaNextPageSelector(): String

    abstract fun searchMangaFromElement(element: Element): SManga

    abstract fun mangaDetailsParse(document: Document): SManga

    final override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val manga = mangaDetailsParse(document)
        if (response.request.url.fragment == VOLUME_URL_FRAGMENT) {
            manga.title = VOLUME_TITLE_PREFIX + manga.title
        }
        return manga
    }

    abstract val chapterType: String
    abstract val volumeType: String

    abstract fun chapterListRequest(mangaUrl: String, type: String): Request

    abstract fun parseChapterElements(response: Response, isVolume: Boolean): List<Element>

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    open fun updateChapterList(manga: SManga, chapters: List<SChapter>) = Unit

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val path = manga.url
        val isVolume = path.endsWith(VOLUME_URL_SUFFIX)
        val type = if (isVolume) volumeType else chapterType
        val request = chapterListRequest(path.removeSuffix(VOLUME_URL_SUFFIX), type)
        val response = client.newCall(request).execute()

        val abbrPrefix = if (isVolume) "Vol" else "Chap"
        val fullPrefix = if (isVolume) "Volume" else "Chapter"
        val linkSelector = Evaluator.Tag("a")
        parseChapterElements(response, isVolume).map { element ->
            SChapter.create().apply {
                val number = element.attr("data-number")
                chapter_number = number.toFloatOrNull() ?: -1f

                val link = element.selectFirst(linkSelector)!!
                name = run {
                    val name = link.text()
                    val prefix = "$abbrPrefix $number: "
                    if (!name.startsWith(prefix)) return@run name
                    val realName = name.removePrefix(prefix)
                    if (realName.contains(number)) realName else "$fullPrefix $number: $realName"
                }
                setUrlWithoutDomain(link.attr("href") + '#' + type + '/' + element.attr("data-id"))
            }
        }.also { if (!isVolume && it.isNotEmpty()) updateChapterList(manga, it) }
    }

    final override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url.substringBeforeLast('#')

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)!!
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_VOLUME_PREF
            title = "Show volume entries in search result"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    companion object {
        private const val SHOW_VOLUME_PREF = "show_volume"

        private const val VOLUME_URL_FRAGMENT = "vol"
        private const val VOLUME_URL_SUFFIX = "#" + VOLUME_URL_FRAGMENT
        private const val VOLUME_TITLE_PREFIX = "[VOL] "
    }
}
