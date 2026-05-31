package eu.kanade.tachiyomi.extension.all.onepiecefans

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import rx.Observable

open class OnePieceFans(
    override val lang: String,
    val internalLang: String,
) : HttpSource(),
    ConfigurableSource {

    override val name = "One Piece Fans"

    override val baseUrl = "https://one-piece-fans2.com"

    override val supportsLatest = false

    private val preferences = getPreferences()

    private val defaultThumbnailUrl = "$baseUrl/images/luffy.png"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/fansubs-config.json", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<Map<String, List<Fansub>>>()

        val mangas = result[internalLang]?.map { fansub ->
            SManga.create().apply {
                title = "One Piece (${fansub.title})"
                thumbnail_url = preferences.getThumbnailUrl()
                url = fansub.path
                initialized = true
            }
        } ?: emptyList()

        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = popularMangaRequest(page)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/$internalLang/${manga.url}"

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/server.php?lang=$internalLang&folderName=${manga.url}", headers)

    protected open val chapterPrefix = "Chapter"

    override fun chapterListParse(response: Response): List<SChapter> {
        val folderName = response.request.url.queryParameter("folderName")
        val chapters = response.parseAs<List<String>>()

        return chapters.map { number ->
            SChapter.create().apply {
                name = "$chapterPrefix $number"
                url = "$folderName/$number"
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/$internalLang/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val (folderName, chapterNumber) = chapter.url.split("/", limit = 2)
        return GET("$baseUrl/server.php?lang=$internalLang&folderName=$folderName&chapter=$chapterNumber", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val folderName = response.request.url.queryParameter("folderName")
        val chapterNumber = response.request.url.queryParameter("chapter")
        val images = response.parseAs<List<String>>()

        return images.mapIndexed { index, fileName ->
            Page(index, imageUrl = "$baseUrl/mangafiles/$internalLang/$folderName/$chapterNumber/$fileName")
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_THUMBNAIL_URL
            title = "Thumbnail URL"
            summary = "Custom thumbnail image URL"
            dialogTitle = "Set Thumbnail URL"
            setDefaultValue(defaultThumbnailUrl)

            setOnPreferenceChangeListener { _, newValue ->
                val value = newValue.toString().trim()

                val isValid = value.isBlank() || value.toHttpUrlOrNull() != null

                if (!isValid) {
                    Toast.makeText(screen.context, "Invalid URL", Toast.LENGTH_SHORT).show()
                }

                isValid
            }
        }.also(screen::addPreference)
    }

    private fun SharedPreferences.getThumbnailUrl(): String = getString(PREF_THUMBNAIL_URL, defaultThumbnailUrl)!!

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    @Serializable
    class Fansub(
        val path: String,
        val title: String,
    )

    companion object {
        const val PREF_THUMBNAIL_URL = "pref_thumbnail_url"
    }
}
