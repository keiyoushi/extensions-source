package eu.kanade.tachiyomi.multisrc.mangathemesia

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.lang.ref.SoftReference
import java.text.SimpleDateFormat
import java.util.Locale

abstract class MangaThemesiaAlt(
    name: String,
    baseUrl: String,
    lang: String,
    mangaUrlDirectory: String = "/manga",
    dateFormat: SimpleDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US),
    private val randomUrlPrefKey: String = "pref_auto_random_url",
) : MangaThemesia(name, baseUrl, lang, mangaUrlDirectory, dateFormat), ConfigurableSource {

    protected val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = randomUrlPrefKey
            title = "Automatically update dynamic URLs"
            summary = "Automatically update random numbers in manga URLs.\n" +
                "Helps mitigating HTTP 404 errors during update and \"in library\" marks when browsing.\n" +
                "Note: This setting may require clearing database in advanced settings " +
                "and migrating all manga to the same source"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    private fun getRandomUrlPref() = preferences.getBoolean(randomUrlPrefKey, true)

    private var randomPartCache = SuspendLazy(::getUpdatedRandomPart) { preferences.randomPartCache = it }

    // cache in preference for webview urls
    private var SharedPreferences.randomPartCache: String
        get() = getString("__random_part_cache", "")!!
        set(newValue) = edit().putString("__random_part_cache", newValue).apply()

    protected open fun getRandomPartFromUrl(url: String): String {
        val slug = url
            .removeSuffix("/")
            .substringAfterLast("/")

        return slugRegex.find(slug)?.groupValues?.get(1) ?: ""
    }

    protected open fun getRandomPartFromResponse(response: Response): String {
        return response.asJsoup()
            .selectFirst(searchMangaSelector())!!
            .select("a").attr("href")
            .let(::getRandomPartFromUrl)
    }

    protected suspend fun getUpdatedRandomPart() =
        client.newCall(GET("$baseUrl$mangaUrlDirectory/", headers))
            .await()
            .use(::getRandomPartFromResponse)

    override fun searchMangaParse(response: Response): MangasPage {
        val mp = super.searchMangaParse(response)

        if (!getRandomUrlPref()) return mp

        // extract random part during browsing to avoid extra call
        mp.mangas.firstOrNull { it.url.matches(slugRegex) }?.run {
            randomPartCache.set(getRandomPartFromUrl(url))
        }

        val mangas = mp.mangas.toPermanentMangaUrls()

        return MangasPage(mangas, mp.hasNextPage)
    }

    protected fun List<SManga>.toPermanentMangaUrls(): List<SManga> {
        for (i in indices) {
            val permaSlug = this[i].url
                .removeSuffix("/")
                .substringAfterLast("/")
                .replaceFirst(slugRegex, "")

            this[i].url = "$mangaUrlDirectory/$permaSlug/"
        }

        return this
    }

    protected open val slugRegex = Regex("""^(\d+-)""")

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (!getRandomUrlPref()) return super.mangaDetailsRequest(manga)

        val slug = manga.url
            .substringBefore("#")
            .removeSuffix("/")
            .substringAfterLast("/")
            .replaceFirst(slugRegex, "")

        val randomPart = randomPartCache.blockingGet()

        return GET("$baseUrl$mangaUrlDirectory/$randomPart$slug/", headers)
    }

    override fun getMangaUrl(manga: SManga): String {
        if (!getRandomUrlPref()) return super.getMangaUrl(manga)

        val slug = manga.url
            .substringBefore("#")
            .removeSuffix("/")
            .substringAfterLast("/")
            .replaceFirst(slugRegex, "")

        val randomPart = randomPartCache.peek() ?: preferences.randomPartCache

        return "$baseUrl$mangaUrlDirectory/$randomPart$slug/"
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
}

internal class SuspendLazy(
    private val initializer: suspend () -> String,
    private val saveCache: (String) -> Unit,
) {

    private val mutex = Mutex()
    private var cachedValue: SoftReference<String>? = null
    private var fetchTime = 0L

    suspend fun get(): String {
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

            initializer().also { set(it) }
        }
    }

    fun set(newVal: String) {
        cachedValue = SoftReference(newVal)
        fetchTime = System.currentTimeMillis()

        saveCache(newVal)
    }

    fun peek(): String? {
        return cachedValue?.get()
    }

    fun blockingGet(): String {
        return runBlocking { get() }
    }
}
