package eu.kanade.tachiyomi.extension.en.flamecomics

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream

class FlameComics : MangaThemesia(
    "Flame Comics",
    "https://flamecomics.com",
    "en",
    mangaUrlDirectory = "/series",
) {

    // Flame Scans -> Flame Comics
    override val id = 6350607071566689772

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client = super.client.newBuilder()
        .rateLimit(2, 7)
        .addInterceptor(::composedImageIntercept)
        .build()

    // Split Image Fixer Start
    private val composedSelector: String = "#readerarea div.figure_container div.composed_figure"

    override fun pageListParse(document: Document): List<Page> {
        val hasSplitImages = document
            .select(composedSelector)
            .firstOrNull() != null

        if (!hasSplitImages) {
            return super.pageListParse(document)
        }

        return document.select("#readerarea p:has(img), $composedSelector").toList()
            .filter {
                it.select("img").all { imgEl ->
                    imgEl.attr("abs:src").isNullOrEmpty().not()
                }
            }
            .mapIndexed { i, el ->
                if (el.tagName() == "p") {
                    Page(i, "", el.select("img").attr("abs:src"))
                } else {
                    val imageUrls = el.select("img")
                        .joinToString("|") { it.attr("abs:src") }

                    Page(i, document.location(), imageUrls + COMPOSED_SUFFIX)
                }
            }
    }

    private fun composedImageIntercept(chain: Interceptor.Chain): Response {
        if (!chain.request().url.toString().endsWith(COMPOSED_SUFFIX)) {
            return chain.proceed(chain.request())
        }

        val imageUrls = chain.request().url.toString()
            .removeSuffix(COMPOSED_SUFFIX)
            .split("%7C")

        var width = 0
        var height = 0

        val imageBitmaps = imageUrls.map { imageUrl ->
            val request = chain.request().newBuilder().url(imageUrl).build()
            val response = chain.proceed(request)

            val bitmap = BitmapFactory.decodeStream(response.body.byteStream())

            width += bitmap.width
            height = bitmap.height

            bitmap
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        var left = 0

        imageBitmaps.forEach { bitmap ->
            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            val dstRect = Rect(left, 0, left + bitmap.width, bitmap.height)

            canvas.drawBitmap(bitmap, srcRect, dstRect, null)

            left += bitmap.width
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.PNG, 100, output)

        val responseBody = output.toByteArray().toResponseBody(MEDIA_TYPE)

        return Response.Builder()
            .code(200)
            .protocol(Protocol.HTTP_1_1)
            .request(chain.request())
            .message("OK")
            .body(responseBody)
            .build()
    }
    // Split Image Fixer End

    // Permanent Url start
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return super.fetchPopularManga(page).tempUrlToPermIfNeeded()
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return super.fetchLatestUpdates(page).tempUrlToPermIfNeeded()
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return super.fetchSearchManga(page, query, filters).tempUrlToPermIfNeeded()
    }

    private fun Observable<MangasPage>.tempUrlToPermIfNeeded(): Observable<MangasPage> {
        return this.map { mangasPage ->
            MangasPage(
                mangasPage.mangas.map { it.tempUrlToPermIfNeeded() },
                mangasPage.hasNextPage,
            )
        }
    }

    private fun SManga.tempUrlToPermIfNeeded(): SManga {
        val turnTempUrlToPerm = preferences.getBoolean(getPermanentMangaUrlPreferenceKey(), true)
        if (!turnTempUrlToPerm) return this

        val path = this.url.removePrefix("/").removeSuffix("/").split("/")
        path.lastOrNull()?.let { slug -> this.url = "$mangaUrlDirectory/${deobfuscateSlug(slug)}/" }

        return this
    }

    override fun fetchChapterList(manga: SManga) = super.fetchChapterList(manga.tempUrlToPermIfNeeded())
        .map { sChapterList -> sChapterList.map { it.tempUrlToPermIfNeeded() } }

    private fun SChapter.tempUrlToPermIfNeeded(): SChapter {
        val turnTempUrlToPerm = preferences.getBoolean(getPermanentChapterUrlPreferenceKey(), true)
        if (!turnTempUrlToPerm) return this

        val path = this.url.removePrefix("/").removeSuffix("/").split("/")
        path.lastOrNull()?.let { slug -> this.url = "/${deobfuscateSlug(slug)}/" }
        return this
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val permanentMangaUrlPref = SwitchPreferenceCompat(screen.context).apply {
            key = getPermanentMangaUrlPreferenceKey()
            title = PREF_PERM_MANGA_URL_TITLE
            summary = PREF_PERM_MANGA_URL_SUMMARY
            setDefaultValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit()
                    .putBoolean(getPermanentMangaUrlPreferenceKey(), checkValue)
                    .commit()
            }
        }
        val permanentChapterUrlPref = SwitchPreferenceCompat(screen.context).apply {
            key = getPermanentChapterUrlPreferenceKey()
            title = PREF_PERM_CHAPTER_URL_TITLE
            summary = PREF_PERM_CHAPTER_URL_SUMMARY
            setDefaultValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit()
                    .putBoolean(getPermanentChapterUrlPreferenceKey(), checkValue)
                    .commit()
            }
        }
        screen.addPreference(permanentMangaUrlPref)
        screen.addPreference(permanentChapterUrlPref)
    }

    private fun getPermanentMangaUrlPreferenceKey(): String {
        return PREF_PERM_MANGA_URL_KEY_PREFIX + lang
    }

    private fun getPermanentChapterUrlPreferenceKey(): String {
        return PREF_PERM_CHAPTER_URL_KEY_PREFIX + lang
    }
    // Permanent Url for Manga/Chapter End

    companion object {
        private const val COMPOSED_SUFFIX = "?comp"

        private const val PREF_PERM_MANGA_URL_KEY_PREFIX = "pref_permanent_manga_url_"
        private const val PREF_PERM_MANGA_URL_TITLE = "Permanent Manga URL"
        private const val PREF_PERM_MANGA_URL_SUMMARY = "Turns all manga urls into permanent ones."

        private const val PREF_PERM_CHAPTER_URL_KEY_PREFIX = "pref_permanent_chapter_url"
        private const val PREF_PERM_CHAPTER_URL_TITLE = "Permanent Chapter URL"
        private const val PREF_PERM_CHAPTER_URL_SUMMARY = "Turns all chapter urls into permanent ones."

        /**
         *
         * De-obfuscates the slug of a series or chapter to the permanent slug
         *   * For a series: "12345678-this-is-a-series" -> "this-is-a-series"
         *   * For a chapter: "12345678-this-is-a-series-chapter-1" -> "this-is-a-series-chapter-1"
         *
         * @param obfuscated_slug the obfuscated slug of a series or chapter
         *
         * @return
         */
        private fun deobfuscateSlug(obfuscated_slug: String) = obfuscated_slug
            .replaceFirst(Regex("""^\d+-"""), "")

        private val MEDIA_TYPE = "image/png".toMediaType()
    }
}
