package eu.kanade.tachiyomi.extension.en.fairiesvstentacles

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class FairiesVsTentacles :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2).addInterceptor(TextInterceptor())

    override fun getHomeUrl() = "$baseUrl/fvt/latest/"

    override suspend fun getPopularManga(page: Int) = MangasPage(entries(), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = entries().filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.first() != "fvt") return null
        return when (url.pathSegments.getOrNull(1)) {
            "main", "latest" -> comic()
            "qna", "questions" -> questions()
            else -> null
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (manga.url == questions().url) questions() else comic()
        if (!fetchChapters) return SMangaUpdate(details, chapters)

        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val chapterList = document.select("option[data-webcomic-url]").map { option ->
            SChapter.create().apply {
                setUrlWithoutDomain(option.absUrl("data-webcomic-url"))
                name = option.text()
            }
        }
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val notes = document.select("div.entry > p")
        val imageUrls = buildList {
            document.select("div.webcomic-media img").mapTo(this) { it.absUrl("src") }
            if (notes.text().isNotBlank() && preferences.getBoolean(SHOW_NOTES_KEY, false)) {
                add(TextInterceptorHelper.createUrl("Author's notes", notes.outerHtml()))
            }
        }
        return imageUrls.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    private val preferences by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_NOTES_KEY
            title = "Show author's notes"
            summary = "Adds the text under a comic page as a page after it"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun entries() = listOf(comic(), questions())

    private fun comic() = SManga.create().apply {
        url = "/fvt/main/"
        title = "Fairies vs Tentacles"
        author = "Eldrik"
        artist = author
        description = "A silly comic about sexy fairies and naughty tentacles by eldrik aethervial."
        status = SManga.ONGOING
        thumbnail_url = "$baseUrl/fvt/wp-content/uploads/page-00.jpg"
    }

    private fun questions() = SManga.create().apply {
        url = "/fvt/qna/"
        title = "Fairies vs Tentacles: Character Questions"
        author = "Eldrik"
        artist = author
        description = "Readers' questions to the characters of Fairies vs Tentacles."
        status = SManga.ONGOING
        thumbnail_url = "$baseUrl/fvt/wp-content/uploads/question-1.jpg"
    }

    companion object {
        private const val SHOW_NOTES_KEY = "showAuthorsNotes"
    }
}
