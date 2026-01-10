package eu.kanade.tachiyomi.extension.all.batoto

import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.all.batotov2.BatoToV2
import eu.kanade.tachiyomi.extension.all.batotov4.BatoToV4
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import rx.Observable

open class BatoTo(
    final override val lang: String,
    siteLang: String = lang,
) : ConfigurableSource, HttpSource() {

    override val name: String = "Bato.to"

    override val id: Long = when (lang) {
        "zh-Hans" -> 2818874445640189582
        "zh-Hant" -> 38886079663327225
        "ro-MD" -> 8871355786189601023
        else -> super.id
    }

    private val preferences by getPreferencesLazy()

    override val baseUrl: String
        get() {
            val index = preferences.getString(MIRROR_PREF_KEY, "0")!!.toInt()
                .coerceAtMost(mirrors.size - 1)

            return mirrors[index]
        }

    private val _delegate: HttpSource =
        if (baseUrl in mirrorsV4) {
            BatoToV4(baseUrl, lang, siteLang, preferences)
        } else {
            BatoToV2(baseUrl, lang, siteLang, preferences)
        }

    override val client = _delegate.client

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = MIRROR_PREF_KEY
            title = "Preferred Mirror"
            entries = mirrors.map {
                if (it in mirrorsV4) {
                    "$it (v4)"
                } else {
                    "$it (v2)"
                }
            }.toTypedArray()
            entryValues = Array(mirrors.size) { it.toString() }
            summary = "%s"
            setDefaultValue("0")

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Restart the app to apply changes", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }

        (_delegate as ConfigurableSource).setupPreferenceScreen(screen)
    }

    override val supportsLatest = _delegate.supportsLatest

    // ************ Search ************ //
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun fetchLatestUpdates(page: Int) = _delegate.fetchLatestUpdates(page)

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun fetchPopularManga(page: Int) = _delegate.fetchPopularManga(page)

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()
    override fun fetchMangaDetails(manga: SManga) = _delegate.fetchMangaDetails(manga)

    override fun getMangaUrl(manga: SManga) = _delegate.getMangaUrl(manga)
    override fun getChapterUrl(chapter: SChapter) = _delegate.getChapterUrl(chapter)

    // searchMangaRequest is not used, see fetchSearchManga instead
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val path = query.toHttpUrl().pathSegments
            val id = if (path.size > 1 && path[0] == "title") {
                path[1].substringBefore("-")
            } else if (path.size > 1 && path[0] == "series") {
                path[1]
            } else {
                return Observable.error(Exception("Unknown url"))
            }

            return _delegate.fetchSearchManga(page, "id:$id", filters)
        }

        return _delegate.fetchSearchManga(page, query, filters)
    }

    override fun fetchChapterList(manga: SManga) = _delegate.fetchChapterList(manga)
    override fun fetchPageList(chapter: SChapter) = _delegate.fetchPageList(chapter)
    override fun fetchImageUrl(page: Page) = _delegate.fetchImageUrl(page)

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()
    override fun pageListParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = _delegate.getFilterList()

    companion object {
        private const val MIRROR_PREF_KEY = "MIRROR"

        // https://batotomirrors.pages.dev/
        private val mirrorsV2 = arrayOf(
            "https://ato.to",
            "https://dto.to",
            "https://fto.to",
            "https://hto.to",
            "https://jto.to",
            "https://lto.to",
            "https://mto.to",
            "https://nto.to",
            "https://vto.to",
            "https://wto.to",
            "https://xto.to",
            "https://yto.to",
            "https://vba.to",
            "https://wba.to",
            "https://xba.to",
            "https://yba.to",
            "https://zba.to",
            "https://bato.ac",
            "https://bato.bz",
            "https://bato.cc",
            "https://bato.cx",
            "https://bato.id",
            "https://bato.pw",
            "https://bato.sh",
            "https://bato.to",
            "https://bato.vc",
            "https://bato.day",
            "https://bato.red",
            "https://bato.run",
            "https://batoto.in",
            "https://batoto.tv",
            "https://batotoo.com",
            "https://batotwo.com",
            "https://batpub.com",
            "https://batread.com",
            "https://battwo.com",
            "https://xbato.com",
            "https://xbato.net",
            "https://xbato.org",
            "https://zbato.com",
            "https://zbato.net",
            "https://zbato.org",
            "https://comiko.net",
            "https://comiko.org",
            "https://mangatoto.com",
            "https://mangatoto.net",
            "https://mangatoto.org",
            "https://batocomic.com",
            "https://batocomic.net",
            "https://batocomic.org",
            "https://readtoto.com",
            "https://readtoto.net",
            "https://readtoto.org",
            "https://kuku.to",
            "https://okok.to",
            "https://ruru.to",
            "https://xdxd.to",
        )

        private val mirrorsV4 = arrayOf(
            "https://bato.si",
            "https://bato.ing",
        )

        private val mirrors = mirrorsV2 + mirrorsV4
    }
}
