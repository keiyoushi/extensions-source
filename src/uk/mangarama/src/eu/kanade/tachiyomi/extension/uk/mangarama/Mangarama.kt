package eu.kanade.tachiyomi.extension.uk.mangarama

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter

@Source
abstract class Mangarama :
    Madara(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override val chapterMode = ChapterMode.MangaAjax

    override val chapterDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    override val altNameSelector = ".post-content_item:contains(Альтернативна) .summary-content"

    override val mangaDetailsSelectorStatus = "div.summary-content, div.summary-heading:contains(Статус) + div"

    override val orderByFilterOptions = listOf(
        intl["order_by_filter_trending"] to "trending",
        intl["order_by_filter_latest"] to "latest",
        intl["order_by_filter_az"] to "alphabet",
        intl["order_by_filter_rating"] to "rating",
        intl["order_by_filter_views"] to "views",
        intl["order_by_filter_new"] to "new-manga",
    )

    override suspend fun fetchChapters(mangaPath: String, id: String, mangaPage: Document?): List<SChapter> {
        val body = FormBody.Builder()
            .add("manga-core", mangaPath.trimEnd('/').substringAfterLast('/'))
            .add("manga_ajax", "1")
            .add("maction", "get_chapters")
            .build()

        val ajaxResponse = client.post("$baseUrl${mangaPath.trimEnd('/')}/ajax/chapters/", xhrHeaders, body)
            .asJsoup()

        val mainPage = mangaPage ?: client.get("$baseUrl$mangaPath").asJsoup()
        val json = mainPage.selectFirst("script:containsData(ManhvaChapterListUI)")?.data()
            ?: throw Exception("Manga data not found")

        val chapterData = json
            .substringAfter("ManhvaChapterListUI = ")
            .substringBeforeLast(";")
            .trim()
            .parseAs<ChapterDatesDto>()

        val hideLocked = hideLocked()

        return ajaxResponse.select(chapterListSelector()).mapNotNull { element ->
            chapterFromElement(element, mangaPath, chapterData, hideLocked)
        }
    }

    private fun chapterFromElement(element: Element, mangaPath: String, data: ChapterDatesDto?, hideLocked: Boolean): SChapter? {
        val chapter = super.chapterFromElement(element, mangaPath) ?: return null

        data?.chapterDates?.get(chapter.url)?.let { dateStr ->
            chapter.date_upload = parseChapterDate(dateStr)
        }

        val isLocked = data?.chapterAccess?.get(chapter.url)?.locked == true
        if (isLocked) {
            if (hideLocked) return null

            chapter.name = "🔒 ${chapter.name}"
            chapter.memo = buildJsonObject {
                chapter.memo.forEach { (key, value) -> put(key, value) }
                put("locked", "true")
            }
        }

        return chapter
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.memo["locked"]?.string == "true") throw Exception("Цей розділ доступний лише з Преміум.")

        val chapterUrl = getChapterUrl(chapter)
        val data = client.get(chapterUrl).asJsoup()

        val json = data.selectFirst("script:containsData(window.MANHVA_READER_CONFIG)")?.data()
            ?: throw Exception("Chapter data not found")

        val dataJson = json
            .substringAfter("window.MANHVA_READER_CONFIG = ")
            .substringBeforeLast(";")
            .trim()
            .parseAs<PageJSON>()

        val imagesUrl = dataJson.endpoint.toHttpUrl().newBuilder().apply {
            addQueryParameter("post", dataJson.postId.toString())
            addQueryParameter("chapter", dataJson.chapterSlug)
            addQueryParameter("token", dataJson.token)
        }.build()

        val nonceHeaders = headersBuilder()
            .set("X-WP-Nonce", dataJson.restNonce)
            .build()

        val dataImages = client.get(imagesUrl, nonceHeaders).parseAs<Images>()

        return dataImages.pages.mapIndexed { index, string ->
            // url sets `Referrer` header to `chapterUrl`
            Page(index, url = chapterUrl, imageUrl = string)
        }
    }

    // ============================ Preferences =============================
    private fun hideLocked(): Boolean = preferences.getBoolean(HIDE_LOCKED_CHAPTERS, true)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_CHAPTERS
            title = HIDE_LOCKED_CHAPTERS_TITLE
            summary = HIDE_LOCKED_CHAPTERS_SUM
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_CHAPTERS = "hide_locked_chapters"
        private const val HIDE_LOCKED_CHAPTERS_TITLE = "Приховувати преміум глави"
        private const val HIDE_LOCKED_CHAPTERS_SUM = "Може викликати помилки при оновленні. Будуть відмічені іконкою: \uD83D\uDD12"
    }
}
