package eu.kanade.tachiyomi.multisrc.mangathemesia

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Document
import java.lang.ref.SoftReference

abstract class MangaThemesiaAlt(
    private val randomUrlPrefKey: String = "pref_auto_random_url",
) : MangaThemesia(),
    ConfigurableSource {

    protected open val listUrl get() = "$mangaUrlDirectory/list-mode/"
    protected open val listSelector = "div#content div.soralist ul li a.series"

    protected val preferences by getPreferencesLazy {
        if (contains("__random_part_cache")) {
            edit().remove("__random_part_cache").apply()
        }
        if (contains("titles_without_random_part")) {
            edit().remove("titles_without_random_part").apply()
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = randomUrlPrefKey
            title = intl["pref_dynamic_url_title"]
            summary = intl["pref_dynamic_url_summary"]
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    private fun getRandomUrlPref() = preferences.getBoolean(randomUrlPrefKey, true)

    private val mutex = Mutex()
    private var cachedValue: SoftReference<Map<String, String>>? = null
    private var fetchTime = 0L

    private suspend fun getUrlMapInternal(): Map<String, String> {
        if (fetchTime + 3600000 < System.currentTimeMillis()) {
            // reset cache
            cachedValue = null
        }

        // fast way
        cachedValue?.get()?.let {
            return it
        }
        return mutex.withLock {
            cachedValue?.get()?.let {
                return it
            }

            fetchUrlMap().also {
                cachedValue = SoftReference(it)
                fetchTime = System.currentTimeMillis()
                preferences.urlMapCache = it
            }
        }
    }

    protected open suspend fun fetchUrlMap(): Map<String, String> {
        client.get("$baseUrl$listUrl").use { response ->
            val document = response.asJsoup()

            return document.select(listSelector).associate {
                val url = it.absUrl("href")

                val slug = url.removeSuffix("/")
                    .substringAfterLast("/")

                val permaSlug = slug
                    .replaceFirst(slugRegex, "")

                permaSlug to slug
            }
        }
    }

    protected suspend fun getUrlMap(cached: Boolean = false): Map<String, String> = if (cached && cachedValue == null) {
        preferences.urlMapCache
    } else {
        getUrlMapInternal()
    }

    // cache in preference for webview urls
    private var SharedPreferences.urlMapCache: Map<String, String>
        get() = runCatching {
            getString("url_map_cache", "{}")!!.parseAs<Map<String, String>>()
        }.getOrElse {
            emptyMap()
        }

        set(newMap) = edit().putString("url_map_cache", newMap.toJsonString()).apply()

    override fun searchMangaParse(document: Document): MangasPage {
        val mp = super.searchMangaParse(document)

        if (!getRandomUrlPref()) return mp

        val mangas = mp.mangas.toPermanentMangaUrls()

        return MangasPage(mangas, mp.hasNextPage)
    }

    protected fun List<SManga>.toPermanentMangaUrls(): List<SManga> = onEach {
        val slug = it.url
            .removeSuffix("/")
            .substringAfterLast("/")

        val permaSlug = slug
            .replaceFirst(slugRegex, "")

        it.url = "$mangaUrlDirectory/$permaSlug/"
    }

    protected open val slugRegex = Regex("""^(\d+-)""")

    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        if (getRandomUrlPref()) {
            getUrlMap()
        }
        return super.fetchMangaUpdate(manga, chapters, fetchDetails, fetchChapters)
    }

    override fun getMangaUrl(manga: SManga): String {
        if (!getRandomUrlPref()) return super.getMangaUrl(manga)

        val slug = manga.url
            .substringBefore("#")
            .removeSuffix("/")
            .substringAfterLast("/")
            .replaceFirst(slugRegex, "")

        val randomSlug = cachedValue?.get()?.get(slug) ?: slug

        return "$baseUrl$mangaUrlDirectory/$randomSlug/"
    }
}
