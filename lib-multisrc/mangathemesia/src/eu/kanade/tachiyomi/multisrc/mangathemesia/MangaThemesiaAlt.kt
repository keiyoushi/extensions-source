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
) : MangaThemesia(name, baseUrl, lang, mangaUrlDirectory, dateFormat), ConfigurableSource {

    protected val preference: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = randomEnablePrefKey
            title = "Automatically update changing urls"
            summary = "Enabling this will automatically update random numeric part of manga urls.\n" +
                "Helps with 404 during update and \"in library\" mark upon browsing.\n\n" +
                "example: https://example.com/manga/12345-cool-manga -> https://example.com/manga/4567-cool-manga\n\n" +
                "Note: This setting may require clearing database in advanced settings\n" +
                "and migrating all manga to the same source"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    private val randomEnablePrefKey = "pref_auto_random_url"

    private fun getRandomUrlPref() = preference.getBoolean(randomEnablePrefKey, true)

    private var randomPartCache = SuspendLazy(::updateRandomPart)

    protected open fun getRandomPart(response: Response): String {
        return response.asJsoup()
            .selectFirst(searchMangaSelector())!!
            .select("a").attr("href")
            .removeSuffix("/")
            .substringAfterLast("/")
            .substringBefore("-")
    }

    protected suspend fun updateRandomPart() =
        client.newCall(GET("$baseUrl$mangaUrlDirectory/", headers))
            .await()
            .use(::getRandomPart)

    override fun searchMangaParse(response: Response): MangasPage {
        val mp = super.searchMangaParse(response)

        if (!getRandomUrlPref()) return mp

        val mangas = mp.mangas.toPermanentMangaUrls()

        return MangasPage(mangas, mp.hasNextPage)
    }

    protected fun List<SManga>.toPermanentMangaUrls(): List<SManga> {
        return map {
            val permaSlug = it.url
                .removeSuffix("/")
                .substringAfterLast("/")
                .replaceFirst(slugRegex, "")

            it.url = "$mangaUrlDirectory/$permaSlug/"

            it
        }
    }

    protected open val slugRegex = Regex("""^\d+-""")

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (!getRandomUrlPref()) return super.mangaDetailsRequest(manga)

        val slug = manga.url
            .substringBefore("#")
            .removeSuffix("/")
            .substringAfterLast("/")
            .replaceFirst(slugRegex, "")

        val randomPart = randomPartCache.blockingGet()

        return GET("$baseUrl$mangaUrlDirectory/$randomPart-$slug/", headers)
    }

    override fun getMangaUrl(manga: SManga): String {
        if (!getRandomUrlPref()) return super.getMangaUrl(manga)

        val slug = manga.url
            .substringBefore("#")
            .removeSuffix("/")
            .substringAfterLast("/")
            .replaceFirst(slugRegex, "")

        // we don't want to make network calls when user simply opens the entry
        val randomPart = randomPartCache.peek()?.let { "$it-" } ?: ""

        return "$baseUrl$mangaUrlDirectory/$randomPart$slug/"
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
}

internal class SuspendLazy<T : Any>(
    private val initializer: suspend () -> T,
) {

    private val mutex = Mutex()
    private var cachedValue: SoftReference<T>? = null
    private var fetchTime = 0L

    suspend fun get(): T {
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
            val result = initializer()
            cachedValue = SoftReference(result)
            fetchTime = System.currentTimeMillis()

            result
        }
    }

    fun peek(): T? {
        return cachedValue?.get()
    }

    fun blockingGet(): T {
        return runBlocking { get() }
    }
}
