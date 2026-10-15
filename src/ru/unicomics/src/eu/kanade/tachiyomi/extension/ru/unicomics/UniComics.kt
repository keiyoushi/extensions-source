package eu.kanade.tachiyomi.extension.ru.unicomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds

@Source
abstract class UniComics : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        connectTimeout(10.seconds)
        readTimeout(30.seconds)
        rateLimit(3)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/comics/series/page/$page").asJsoup()
        val mangas = document.select(".comics-grid .comic-card").mapNotNull { element ->
            popularMangaFromElement(element)
        }
        val hasNextPage = document.selectFirst("select.mobilePageSelector option[selected] ~ option") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element): SManga? {
        val titleLink = element.selectFirst(".comic-title-link") ?: element.selectFirst("a") ?: return null
        val url = titleLink.absUrl("href").takeIf { it.isNotEmpty() } ?: return null
        val ruTitle = element.selectFirst(".comic-title-ru")?.text()
        val enTitle = element.selectFirst(".comic-title-en")?.text()
        val title = ruTitle.takeUnless { it.isNullOrEmpty() }
            ?: enTitle.takeUnless { it.isNullOrEmpty() }
            ?: titleLink.text().takeIf { it.isNotEmpty() } ?: return null

        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(url)
            thumbnail_url = element.selectFirst(".comic-image-link img, img")?.absUrl("src")
        }
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/comics/online/page/$page").asJsoup()
        val mangas = document.select(".comics-grid .comic-card").mapNotNull { element ->
            popularMangaFromElement(element)
        }
        val hasNextPage = document.selectFirst("select.mobilePageSelector option[selected] ~ option") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val events = filters.firstInstanceOrNull<GetEventsList>()
        val publisher = filters.firstInstanceOrNull<Publishers>()

        val url = when {
            query.isNotEmpty() -> HttpUrl.Builder()
                .scheme("https")
                .host("yandex.ru")
                .addPathSegments("search/site/")
                .addQueryParameter("searchid", "14915852")
                .addQueryParameter("text", query)
                .addQueryParameter("web", "0")
                .addQueryParameter("l10n", "ru")
                .addQueryParameter("p", (page - 1).toString())
                .build().toString()

            (events?.state ?: 0) > 0 -> "$baseUrl$PATH_EVENTS"
            (publisher?.state ?: 0) > 0 -> {
                val publisherName = publisher!!.urls[publisher.state]
                "$baseUrl$PATH_PUBLISHERS/$publisherName/page/$page"
            }

            else -> return getPopularManga(page)
        }

        return searchMangaParse(client.get(url).asJsoup())
    }

    private fun searchMangaParse(document: Document): MangasPage {
        if (document.location().contains("yandex")) {
            if (document.selectFirst(".CheckboxCaptcha, .captcha__captcha") != null) {
                throw Exception("Пройдите капчу Yandex в WebView (слишком много запросов)")
            }

            val mangas = document.select(".b-serp-item__title-link").mapNotNull { a ->
                val href = a.absUrl("href")
                if (!href.contains("unicomics.ru")) return@mapNotNull null

                val urlString = href.replace("/comics/issue/", "/comics/series/")
                    .replace("/comics/online/", "/comics/series/")
                val seriesUrl = ISSUE_REGEX.replace(urlString, "")

                SManga.create().apply {
                    setUrlWithoutDomain(seriesUrl)
                    title = a.text().substringBefore(" (").substringBefore(" №")
                }
            }
            val hasNext = document.selectFirst(".b-pager__next") != null
            return MangasPage(mangas.distinctBy { it.url }, hasNext)
        }

        if (document.location().contains(PATH_EVENTS)) {
            val mangas = document.select(".events-grid .event-card, .list_events").mapNotNull { element ->
                val a = element.selectFirst("a") ?: return@mapNotNull null
                val url = a.absUrl("href").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val title = element.selectFirst(".comic-title-ru, .event-title")?.text()?.takeIf { it.isNotEmpty() }
                    ?: a.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null

                SManga.create().apply {
                    setUrlWithoutDomain(url)
                    this.title = title
                    thumbnail_url = element.selectFirst("img")?.absUrl("src")
                }
            }
            return MangasPage(mangas, false)
        }

        val mangas = document.select(".comics-grid .comic-card").mapNotNull { element ->
            popularMangaFromElement(element)
        }
        val hasNextPage = document.selectFirst("select.mobilePageSelector option[selected] ~ option") != null

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val titleid = url.pathSegments.getOrNull(2) ?: return null

        return mangaDetailsParse(
            client.get("$baseUrl$PATH_URL$titleid").asJsoup(),
        ).apply {
            this.url = PATH_URL + titleid
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val updatedManga = async {
            if (fetchDetails) {
                mangaDetailsParse(
                    client.get(getMangaUrl(manga)).asJsoup(),
                )
            } else {
                manga
            }
        }
        val updatedChapters = async {
            if (fetchChapters) getChapters(manga) else chapters
        }

        SMangaUpdate(updatedManga.await(), updatedChapters.await())
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val isIssue = document.selectFirst(".issue-info-grid") != null
        if (isIssue) {
            title = document.selectFirst(".issue-info h1")?.text() ?: ""
            thumbnail_url = document.selectFirst(".issue-cover img")?.absUrl("src")

            val labels = document.select(".issue-info-grid .issue-info-label")
            val values = document.select(".issue-info-grid .issue-info-value")
            val infoMap = labels.mapIndexed { i, el ->
                el.text().removeSuffix(":") to (values.getOrNull(i)?.text() ?: "")
            }.toMap()
            author = infoMap["Издательство"]
        } else {
            val titleRu = document.selectFirst(".series-main h1")?.text()
            val titleEn = document.selectFirst(".series-main h2")?.text()
            title = titleRu ?: titleEn ?: ""
            thumbnail_url = document.selectFirst(".cover-series img, .cover-series-mobile img")?.absUrl("src")

            val labels = document.select(".series-info-grid .label")
            val values = document.select(".series-info-grid .value")
            val infoMap = labels.mapIndexed { i, el ->
                el.text().removeSuffix(":") to (values.getOrNull(i)?.text() ?: "")
            }.toMap()
            author = infoMap["Издательство"]

            description = buildString {
                if (!titleEn.isNullOrEmpty()) append(titleEn).append("\n\n")
                document.selectFirst(".series-description")?.text()?.let { append(it) }
            }
        }
        initialized = true
    }

    private suspend fun getChapters(manga: SManga): List<SChapter> = coroutineScope {
        val chapters = mutableListOf<SChapter>()
        val url = baseUrl + manga.url
        val document = client.get(url).asJsoup()

        val isIssue = document.selectFirst(".issue-info-grid") != null
        if (isIssue) {
            val chapter = SChapter.create().apply {
                name = document.selectFirst(".issue-info h1")?.text() ?: "Глава"
                val match = CHAPTER_NUMBER_REGEX.find(name)
                if (match != null) {
                    chapter_number = match.groupValues[1].toFloatOrNull() ?: 1f
                }

                val readBtn = document.selectFirst(".btn-read-online-issues")
                if (readBtn != null) {
                    setUrlWithoutDomain(readBtn.absUrl("href"))
                } else {
                    setUrlWithoutDomain(manga.url)
                }
            }
            chapters.add(chapter)
            return@coroutineScope chapters
        }

        document.select(".comics-grid .comic-card").forEach { element ->
            chapters.add(chapterFromElement(element))
        }

        val pageUrls = document.select("select.mobilePageSelector option")
            .mapNotNull { it.absUrl("value").takeIf(String::isNotBlank) }
            .drop(1)

        val pageChapters = pageUrls.map { pageUrl ->
            async {
                client.get(pageUrl).asJsoup()
                    .select(".comics-grid .comic-card")
                    .map {
                        chapterFromElement(it)
                    }
            }
        }.awaitAll().flatten()

        chapters.addAll(pageChapters)
        chapters.reversed().map {
            it.apply {
                if (name == manga.title) name = "\u200B" + name
            }
        }
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val readUrl = element.selectFirst(".buttons-grid a:contains(Читать)")?.absUrl("href")?.ifEmpty { null }
            ?: element.selectFirst(".comic-title-link")?.absUrl("href") ?: ""
        setUrlWithoutDomain(readUrl)

        val ruTitle = element.selectFirst(".comic-title-ru")?.text() ?: ""
        val enTitle = element.selectFirst(".comic-title-en")?.text() ?: ""
        name = ruTitle.ifEmpty { enTitle }

        val match = CHAPTER_NUMBER_REGEX.find(name)?.groupValues?.get(1)?.let {
            chapter_number = it.toFloatOrNull() ?: -1f
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val options = document.select("select.mobilePageSelector option")
        if (options.isNotEmpty()) {
            return options.mapIndexed { i, option ->
                Page(i, url = option.absUrl("value"))
            }
        }

        val html = document.html()
        val match = PAGINATOR_REGEX.find(html)
        if (match != null) {
            val totalPages = match.groupValues[1].toIntOrNull() ?: 1
            val basePath = match.groupValues[2]
            return (1..totalPages).mapIndexed { i, pageNum ->
                Page(i, url = "$baseUrl$basePath$pageNum")
            }
        }

        return listOf(Page(0, url = document.location()))
    }

    override suspend fun getImageUrl(page: Page): String {
        val document = client.get(page.url).asJsoup()
        return document.selectFirst(".image_online, #b_image, #image")?.absUrl("src") ?: ""
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get("$baseUrl/comics/publishers").asJsoup()
        .select(".publishers-card > a:first-child")
        .associate { it.text() to it.attr("href").substringAfterLast("/") }.toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            val publishers = data?.parseAs<Map<String, String>>().orEmpty()
            if (publishers.isNotEmpty()) add(Publishers(publishers))
            add(GetEventsList())
        },
    )

    companion object {
        private const val PATH_URL = "/comics/series/"
        private const val PATH_PUBLISHERS = "/comics/publishers"
        private const val PATH_EVENTS = "/comics/events"

        private val ISSUE_REGEX = "-\\d+/?$".toRegex()
        private val CHAPTER_NUMBER_REGEX = "№\\s*(\\d+(?:\\.\\d+)?)".toRegex()
        private val PAGINATOR_REGEX = "new Paginator\\(['\"].*?['\"],\\s*(\\d+),\\s*\\d+,\\s*\\d+,\\s*['\"](.*?)['\"]\\)".toRegex()
    }
}
