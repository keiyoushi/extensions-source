package eu.kanade.tachiyomi.extension.all.onepiecefans

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Source
abstract class OnePieceFans :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    private val internalLang: String get() = lang

    private val preferences = getPreferences()

    private val defaultThumbnailUrl get() = "$baseUrl/images/luffy.png"

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.get("$baseUrl/fansubs-config.json").parseAs<Map<String, List<Fansub>>>()

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

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getPopularManga(page)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/$internalLang/${manga.url}"

    protected open val chapterPrefix = "Chapter"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val chapterNumbers = client.get("$baseUrl/server.php?lang=$internalLang&folderName=${manga.url}")
            .parseAs<List<String>>()

        val newChapters = chapterNumbers.map { number ->
            SChapter.create().apply {
                name = "$chapterPrefix $number"
                url = "${manga.url}/$number"
            }
        }

        return SMangaUpdate(manga, newChapters)
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/$internalLang/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (folderName, chapterNumber) = chapter.url.split("/", limit = 2)
        val images = client.get("$baseUrl/server.php?lang=$internalLang&folderName=$folderName&chapter=$chapterNumber")
            .parseAs<List<String>>()

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

    @Serializable
    class Fansub(
        val path: String,
        val title: String,
    )

    companion object {
        const val PREF_THUMBNAIL_URL = "pref_thumbnail_url"
    }
}
