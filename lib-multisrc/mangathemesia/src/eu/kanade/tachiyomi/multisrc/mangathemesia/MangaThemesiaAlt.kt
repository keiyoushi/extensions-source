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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
            title = intl["pref_dynamic_url_title"]
            summary = intl["pref_dynamic_url_summary"]
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    private fun getRandomUrlPref() = preferences.getBoolean(randomUrlPrefKey, true)

    private var randomPartCache = SuspendLazy(::getUpdatedRandomPart) { preferences.randomPartCache = it }

    // cache in preference for webview urls
    private var SharedPreferences.randomPartCache: String
        get() = getString("__random_part_cache", "")!!
        set(newValue) = edit().putString("__random_part_cache", newValue).apply()

    // some new titles don't have random part
    // se we save their slug and when they
    // finally add it, we remove the slug in the interceptor
    private var SharedPreferences.titlesWithoutRandomPart: MutableSet<String>
        get() {
            val value = getString("titles_without_random_part", null)
                ?: return mutableSetOf()

            return json.decodeFromString(value)
        }
        set(newValue) {
            val encodedValue = json.encodeToString(newValue)

            edit().putString("titles_without_random_part", encodedValue).apply()
        }

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

    protected suspend fun getUpdatedRandomPart(): String =
        client.newCall(GET("$baseUrl$mangaUrlDirectory/", headers))
            .await()
            .use(::getRandomPartFromResponse)

    override fun searchMangaParse(response: Response): MangasPage {
        val mp = super.searchMangaParse(response)

        if (!getRandomUrlPref()) return mp

        // extract random part during browsing to avoid extra call
        mp.mangas.firstOrNull()?.run {
            val randomPart = getRandomPartFromUrl(url)

            if (randomPart.isNotEmpty()) {
                randomPartCache.set(randomPart)
            }
        }

        val mangas = mp.mangas.toPermanentMangaUrls()

        return MangasPage(mangas, mp.hasNextPage)
    }

    protected fun List<SManga>.toPermanentMangaUrls(): List<SManga> {
        // some absolutely new titles don't have the random part yet
        // save them so we know where to not apply it
        val foundTitlesWithoutRandomPart = mutableSetOf<String>()

        for (i in indices) {
            val slug = this[i].url
                .removeSuffix("/")
                .substringAfterLast("/")

            if (slugRegex.find(slug)?.groupValues?.get(1) == null) {
                foundTitlesWithoutRandomPart.add(slug)
            }

            val permaSlug = slug
                .replaceFirst(slugRegex, "")

            this[i].url = "$mangaUrlDirectory/$permaSlug/"
        }

        if (foundTitlesWithoutRandomPart.isNotEmpty()) {
            foundTitlesWithoutRandomPart.addAll(preferences.titlesWithoutRandomPart)

            preferences.titlesWithoutRandomPart = foundTitlesWithoutRandomPart
        }

        return this
    }

    protected open val slugRegex = Regex("""^(\d+-)""")

    override val client = super.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            if (request.url.fragment != "titlesWithoutRandomPart") {
                return@addInterceptor response
            }

            if (!response.isSuccessful && response.code == 404) {
                response.close()

                val slug = request.url.toString()
                    .substringBefore("#")
                    .removeSuffix("/")
                    .substringAfterLast("/")

                preferences.titlesWithoutRandomPart.run {
                    remove(slug)

                    preferences.titlesWithoutRandomPart = this
                }

                val randomPart = randomPartCache.blockingGet()
                val newRequest = request.newBuilder()
                    .url("$baseUrl$mangaUrlDirectory/$randomPart$slug/")
                    .build()

                return@addInterceptor chain.proceed(newRequest)
            }

            return@addInterceptor response
        }
        .build()

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (!getRandomUrlPref()) return super.mangaDetailsRequest(manga)

        val slug = manga.url
            .substringBefore("#")
            .removeSuffix("/")
            .substringAfterLast("/")
            .replaceFirst(slugRegex, "")

        if (preferences.titlesWithoutRandomPart.contains(slug)) {
            return GET("$baseUrl${manga.url}#titlesWithoutRandomPart")
        }

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

        if (preferences.titlesWithoutRandomPart.contains(slug)) {
            return "$baseUrl${manga.url}"
        }

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
