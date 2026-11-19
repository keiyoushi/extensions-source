package eu.kanade.tachiyomi.extension.ru.desu

import android.widget.Toast
import androidx.preference.ListPreference
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.ratingStars
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Desu :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override fun Headers.Builder.configureHeaders() = apply {
        set("User-Agent", "Mihon (+https://github.com/keiyoushi/extensions-source)")
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(1, 2.seconds) { it.host == baseUrl.toHttpUrl().host }
    }

    // ============================== Manga Details ===============================
    private fun MangaDetDto.toSManga(
        genresStr: String? = genres?.joinToString { it.name } ?: "",
        authorsStr: String? = authors?.joinToString { it.name },
    ): SManga {
        val ratingValue = score?.value ?: 0.0f

        val rawAgeStop = when (content_rating) {
            "no" -> ""
            else -> content_rating?.replace("_plus", "+") ?: ""
        }

        val category = when (kind) {
            "manga" -> "Манга"
            "manhwa" -> "Манхва"
            "manhua" -> "Маньхуа"
            "comics" -> "Комикс"
            "one_shot" -> "Ваншот"
            else -> "Манга"
        }

        var altName = ""

        if (!synonyms.isNullOrEmpty()) {
            altName = "Альтернативные названия:\n" +
                synonyms.joinToString(separator = " / ") +
                "\n\n"
        }

        return SManga.create().also { manga ->
            manga.title = if (isEng.equals("rus")) {
                russian
            } else {
                name
            }
            manga.url = "/$id"
            manga.thumbnail_url = cover.preview
            val totalVotes = score?.votes ?: 0L
            manga.description = if (isEng.equals("rus")) {
                name
            } else {
                russian
            } + "\n" + ratingStars(ratingValue.toDouble()) + " " + ratingValue + " (голосов: " +
                totalVotes + ")\n" + altName + description
            manga.genre = ("$category, $rawAgeStop, $genresStr").split(", ").filter { it.isNotEmpty() }.joinToString { it.trim() }
            manga.status = when (trans_status) {
                "continued" -> SManga.ONGOING

                "completed" -> SManga.COMPLETED

                else -> when (status) {
                    "ongoing" -> SManga.ONGOING

                    "released" -> SManga.COMPLETED

                    //  "copyright" -> SManga.LICENSED  Hides available chapters!
                    else -> SManga.UNKNOWN
                }
            }
            manga.author = authorsStr
        }
    }

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // Текстовий пошук: окремий запит /manga/search/, фільтри в ньому не діють
        if (query.isNotEmpty()) return searchByQuery(page, query)

        val order = filters.firstInstanceOrNull<OrderBy>()?.value ?: ORDER_VALUES[ORDER_POPULAR]
        val types = filters.firstInstanceOrNull<TypeList>()?.state.orEmpty()
            .filter { it.state }
            .map { it.id }
        val statuses = filters.firstInstanceOrNull<StatusList>()?.state.orEmpty()
            .filter { it.state }
            .map { it.id }
        // жанри і теги разом: "id-slug" або "id-!slug" (виключення)
        val genreParams = (
            filters.firstInstanceOrNull<GenreList>()?.state.orEmpty() +
                filters.firstInstanceOrNull<TagList>()?.state.orEmpty()
            )
            .filterNot { it.isIgnored() }
            .map { it.toParam() }

        // Каталог (фільтри / сортування): парсинг HTML сторінки /manga/
        val url = "$baseUrl/manga/".toHttpUrl().newBuilder().apply {
            if (page > 1) addQueryParameter("page", page.toString())
            if (statuses.isNotEmpty()) addQueryParameter("status", statuses.joinToString(","))
            if (types.isNotEmpty()) addQueryParameter("kinds", types.joinToString(","))
            if (genreParams.isNotEmpty()) addQueryParameter("genres", genreParams.joinToString(","))
            addQueryParameter("order_by", order)
        }.build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return parseCatalog(document)
    }

    // ============================== Text search ===============================
    private suspend fun searchByQuery(page: Int, query: String): MangasPage {
        // Сайт повертає всі результати одним списком, без пагінації
        if (page > 1) return MangasPage(emptyList(), false)

        val body = FormBody.Builder()
            .add("q", query)
            .add("type", "manga")
            .add("_xfToken", "")
            .add("_xfRequestUri", "/manga/")
            .add("_xfNoRedirect", "1")
            .add("_xfResponseType", "json")
            .build()

        val result = client.post(
            url = "$baseUrl/manga/search/",
            headers = headers.newBuilder()
                .set("X-Requested-With", "XMLHttpRequest")
                .set("X-Ajax-Referer", "$baseUrl/manga/")
                .build(),
            fetch = Fetch.XHR.from("/manga/"),
            body = body,
        ).parseAs<SearchDto>()

        val doc = Jsoup.parseBodyFragment(result.templateHtml, baseUrl)
        val mangas = doc.select("li.AniMangaSearchCard").mapNotNull { it.toSearchManga() }
        return MangasPage(mangas, false)
    }

    private fun Element.toSearchManga(): SManga? {
        val link = selectFirst("a.AniMangaSearchCard__link") ?: return null
        // href: manga/slug.6423/ -> id = 6423
        val id = link.attr("href").substringBefore('?').trimEnd('/').substringAfterLast('.')
        if (id.isEmpty() || !id.all { it.isDigit() }) return null

        val russianName = selectFirst(".AniMangaSearchCard__title")?.text()?.trim().orEmpty()
        val originalName = selectFirst(".AniMangaSearchCard__subtitle")?.text()?.trim().orEmpty()

        // x120 -> preview, щоб обкладинка була такою ж, як у каталозі
        val cover = selectFirst("img.AniMangaSearchCard__cover")?.attr("src")
            ?.replace("/covers/x120/", "/covers/preview/")
            ?.takeIf { it.isNotEmpty() }

        return SManga.create().apply {
            url = "/$id"
            title = if (isEng.equals("rus")) {
                russianName.ifEmpty { originalName }
            } else {
                originalName.ifEmpty { russianName }
            }
            thumbnail_url = cover
        }
    }

    // ============================== Catalog parsing ===============================
    private fun parseCatalog(document: Document): MangasPage {
        val mangas = document.select("ol.memberList > li.memberListItem").mapNotNull { it.toCatalogManga() }

        val hasNext = PAGE_COUNTER_REGEX.find(document.selectFirst("span.pageNavHeader")?.text() ?: document.text())?.let {
            val (current, last) = it.destructured
            current.toInt() < last.toInt()
        } ?: (document.selectFirst("a:containsOwn(Вперёд)") != null)

        return MangasPage(mangas, hasNext)
    }

    private fun Element.toCatalogManga(): SManga? {
        val link = selectFirst("h3 a.animeTitle") ?: selectFirst("h3 a") ?: return null
        // href відносний: manga/out.3365/ -> id = 3365
        val id = link.attr("href").substringBefore('?').trimEnd('/').substringAfterLast('.')
        if (id.isEmpty() || !id.all { it.isDigit() }) return null

        val originalName = link.text().trim()
        // <div class="dimmed"><span>Русское название</span></div>; запасний варіант: title="Русское (Original)"
        val russianName = selectFirst("div.dimmed span")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: link.attr("title").removeSuffix(" ($originalName)").ifEmpty { originalName }

        // <span class="img" style="background-image: url('https://static.desu.uno/.../3674.jpg?1722258191');">
        val cover = selectFirst("span.img")?.attr("style")
            ?.let { COVER_REGEX.find(it)?.groupValues?.get(1) }
            ?.let { if (it.startsWith("/")) baseUrl + it else it }

        return SManga.create().apply {
            url = "/$id"
            title = if (isEng.equals("rus")) russianName else originalName
            thumbnail_url = cover
        }
    }

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList(OrderBy(ORDER_POPULAR)))

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList(OrderBy(ORDER_UPDATED)))

    // ============================== Manga/Chapters ===============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = if (fetchDetails) {
            async {
                val responseManga = client.get("$baseUrl$API_URL${manga.url}/", Fetch.XHR)
                responseManga.parseAs<InfoWrapperDto<MangaDetDto>>().manga.toSManga()
            }
        } else {
            null
        }

        val chaptersDeferred = if (fetchChapters) {
            async {
                val responseChapter = client.get("$baseUrl$API_URL${manga.url}/chapters", Fetch.XHR)
                val objChapter = responseChapter.parseAs<SeriesWrapperDto<List<ChaptersDto>>>().chapters
                objChapter.map { chapter ->
                    val fullNumStr = "${chapter.volume}. Глава ${chapter.number}"
                    SChapter.create().apply {
                        name = chapter.title?.let { "$fullNumStr $it" } ?: fullNumStr
                        url = chapter.id.toString()
                        memo = buildJsonObject {
                            put("mangaUrl", manga.url)
                            put("viewUrl", chapter.view_url)
                        }
                        chapter_number = chapter.number.toFloatOrNull() ?: -1f
                        date_upload = chapter.publish_date.times(1000L)
                    }
                }
            }
        } else {
            null
        }

        val finalManga = mangaDeferred?.await() ?: manga
        val finalChapters = chaptersDeferred?.await() ?: chapters

        SMangaUpdate(finalManga, finalChapters)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga${manga.url}"

    // ============================== Chapters Images ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val titleId = chapter.memo["mangaUrl"]!!.string.removePrefix("/")
        val chapterId = chapter.url
        val url = "$baseUrl$API_URL$titleId/chapters/$chapterId"

        val response = client.get(url.toHttpUrl(), Fetch.XHR)

        val result = response.parseAs<ChapterWrapperDto<ChapterDataDto>>()

        return result.chapter.pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.url)
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val viewUrl = chapter.memo["viewUrl"]?.string
            ?: throw Exception("Обновите список глав!")
        return viewUrl
    }

    // =========================== Deeplink (Manga from Browser) ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0] == "manga" && url.pathSegments[1].length > 1) {
            val tmpManga = SManga.create().apply {
                this.url = "/${url.pathSegments[1].substringAfterLast(".")}"
            }
            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    // ============================== Filters ===============================
    override fun getFilterList(data: JsonElement?) = FilterList(
        OrderBy(),
        TypeList(getTypeList()),
        StatusList(getStatusList()),
        GenreList(getGenreList()),
        TagList(getTagList()),
    )

    // ============================== Preference ===============================
    private var isEng: String? = preferences.getString(LANGUAGE_PREF, "eng")

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = LANGUAGE_PREF
            title = "Выбор языка на обложке"
            entries = arrayOf("Английский", "Русский")
            entryValues = arrayOf("eng", "rus")
            summary = "%s"
            setDefaultValue("eng")
            setOnPreferenceChangeListener { _, newValue ->
                val warning = "Если язык обложки не изменился очистите базу данных в приложении (Настройки -> Дополнительно -> Очистить базу данных)"
                Toast.makeText(screen.context, warning, Toast.LENGTH_LONG).show()
                true
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val LANGUAGE_PREF = "DesuTitleLanguage"
        private const val API_URL = "/api/manga/"
        private val PAGE_COUNTER_REGEX = Regex("""Страница\s+(\d+)\s+из\s+(\d+)""")
        private val COVER_REGEX = Regex("""url\(['"]?([^'")]+)""")
    }
}
