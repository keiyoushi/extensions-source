package eu.kanade.tachiyomi.extension.ja.ganma

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import rx.Observable

class Ganma : HttpSource() {
    override val name = "GANMA!"
    override val lang = "ja"
    override val baseUrl = "https://ganma.jp"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("X-From", "$baseUrl/web")
        .add("Content-Type", "application/json;charset=UTF-8")
        .add("Accept", "application/json, text/plain, */*")

    private val apiUrl = "$baseUrl/api/graphql"

    private var operationsMap: Map<String, String>? = null
    private var lastSearchCursor: String? = null
    private var lastFilterCursor: String? = null

    // https://ganma.jp/web/_next/static/chunks/app/layout-98772c0967d4bfb7.js
    // Hashes to bypass OnlyPersistedQueryIsAllowed
    private fun fetchAndParseHashes(): Map<String, String> {
        val mainPage = client.newCall(GET("$baseUrl/web", headers)).execute()
        val document = mainPage.asJsoup()

        val mainScriptUrl = document.selectFirst("script[src*=/app/layout-]")
            ?.attr("abs:src")
            ?.ifEmpty { null }
            ?: throw Exception("Could not find layout script")

        val scriptContent = client.newCall(GET(mainScriptUrl, headers)).execute().body.string()

        val manifestRegex = """operations:(\[.+?])\};""".toRegex()
        val manifestMatch = manifestRegex.find(scriptContent)
            ?: throw Exception("Could not find operations manifest in script")

        val manifestJson = manifestMatch.groupValues[1]

        val operationRegex = """id:"([a-f0-9]{64})",body:".*?",name:"(\w+)"""".toRegex()
        return operationRegex.findAll(manifestJson).associate {
            val (hash, name) = it.destructured
            name to hash
        }.also { operationsMap = it }
    }

    private inline fun <reified T> graphQlRequest(operationName: String, variables: T, useAppHeaders: Boolean = true): Request {
        val hashes = operationsMap ?: fetchAndParseHashes()
        val hash = hashes[operationName] ?: throw Exception("Could not find hash for operation: $operationName")

        val finalHeaders = headersBuilder().apply {
            if (useAppHeaders) {
                set("User-Agent", "GanmaReader/9.9.1 Android")
            } else {
                set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
            }
        }.build()

        val extensions = Payload.Extensions(Payload.Extensions.PersistedQuery(version = 1, sha256Hash = hash))
        val payload = Payload(operationName, variables, extensions)
        val payloadSerializer = Payload.serializer(serializer<T>())

        val requestBody = jsonInstance.encodeToString(payloadSerializer, payload).toRequestBody("application/json; charset=utf-8".toMediaType())
        return POST(apiUrl, finalHeaders, requestBody)
    }

    private fun updateImageUrlWidth(url: String?, width: Int = 4999): String? = url?.toHttpUrlOrNull()
        ?.newBuilder()
        ?.setQueryParameter("w", width.toString())
        ?.build()
        ?.toString()

    private val cacheWebOnlyAliases: Set<String> by lazy {
        try {
            fetchWebOnlyAliases()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun fetchWebOnlyAliases(): Set<String> {
        val response = client.newCall(GET("$baseUrl/web/magazineCategory/webOnly", headers)).execute()
        val document = response.asJsoup()
        return document.select("a[href*=/web/magazine/]")
            .map { it.attr("href").substringAfterLast("/") }
            .toSet()
    }

    override fun popularMangaRequest(page: Int): Request = graphQlRequest("home", EmptyVariables, useAppHeaders = true)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<GraphQLResponse<HomeDto>>().data
        val mangas = data.ranking.totalRanking.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = graphQlRequest("home", EmptyVariables, useAppHeaders = true)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<GraphQLResponse<HomeDto>>().data
        val mangas = data.latestTotalRanking10.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            if (page == 1) {
                lastSearchCursor = null
            }
            val variables = SearchVariables(keyword = query, first = 20, after = lastSearchCursor)
            return graphQlRequest("magazinesByKeywordSearch", variables, useAppHeaders = false)
        }

        if (page == 1) {
            lastFilterCursor = null
        }
        val category = filters.filterIsInstance<CategoryFilter>().first().selected

        return when (category.type) {
            "day" -> {
                val variables = DayOfWeekVariables(dayOfWeek = category.id, first = 20, after = lastFilterCursor)
                graphQlRequest("serialMagazinesByDayOfWeek", variables)
            }

            "finished" -> {
                val variables = FinishedVariables(first = 20, after = lastFilterCursor)
                graphQlRequest("finishedMagazines", variables)
            }

            else -> graphQlRequest("home", EmptyVariables)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val operationName = Buffer().use {
            response.request.body?.writeTo(it)
            it.readUtf8().parseAs<JsonObject>()["operationName"]?.jsonPrimitive?.content
        }

        if (operationName == "magazinesByKeywordSearch") {
            val data = response.parseAs<GraphQLResponse<SearchDto>>().data
            lastSearchCursor = data.searchComic.pageInfo.endCursor
            val mangas = data.searchComic.edges
                .mapNotNull { it.node }
                .map { it.toSManga() }
            return MangasPage(mangas, data.searchComic.pageInfo.hasNextPage)
        }

        return when (operationName) {
            "serialMagazinesByDayOfWeek" -> {
                val data = response.parseAs<GraphQLResponse<SerialResponseDto>>().data.serialPerDayOfWeek.panels
                lastFilterCursor = data.pageInfo.endCursor
                val mangas = data.edges
                    .map { it.node.storyInfo.magazine }
                    .map { it.toSManga() }
                MangasPage(mangas, data.pageInfo.hasNextPage)
            }

            "finishedMagazines" -> {
                val data = response.parseAs<GraphQLResponse<FinishedResponseDto>>().data.magazinesByCategory.magazines
                lastFilterCursor = data.pageInfo.endCursor
                val mangas = data.edges
                    .map { it.node }
                    .map { it.toSManga() }
                MangasPage(mangas, data.pageInfo.hasNextPage)
            }

            "home" -> {
                val data = response.parseAs<GraphQLResponse<HomeDto>>().data
                val mangas = data.ranking.totalRanking
                    .map { it.toSManga() }
                MangasPage(mangas, false)
            }

            else -> MangasPage(emptyList(), false)
        }
    }

    private fun MangaItemDto.toSManga(): SManga = SManga.create().apply {
        url = this@toSManga.alias
        title = this@toSManga.title
        thumbnail_url = updateImageUrlWidth(this@toSManga.todaysJacketImageURL ?: this@toSManga.rectangleWithLogoImageURL)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val variables = MagazineDetailVariables(magazineIdOrAlias = manga.url)
        return graphQlRequest("magazineDetail", variables)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/web/magazine/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val magazine = response.parseAs<GraphQLResponse<MagazineDetailDto>>().data.magazine
        val manga = SManga.create().apply {
            url = magazine.alias
            title = magazine.title
            author = magazine.authorName
            description = magazine.description
            status = if (magazine.isFinished) SManga.COMPLETED else SManga.ONGOING
            thumbnail_url = updateImageUrlWidth(magazine.todaysJacketImageURL)
        }

        if (magazine.todaysJacketImageURL == null) {
            try {
                val searchResponse = client.newCall(searchMangaRequest(1, manga.title, FilterList())).execute()
                if (searchResponse.isSuccessful) {
                    val searchData = searchResponse.parseAs<GraphQLResponse<SearchDto>>().data
                    val searchResultUrl = searchData.searchComic.edges
                        .firstOrNull { it.node?.alias == manga.url }
                        ?.node?.todaysJacketImageURL

                    if (searchResultUrl != null) {
                        manga.thumbnail_url = updateImageUrlWidth(searchResultUrl)
                    }
                }
            } catch (e: Exception) {
            }
        }
        return manga
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val chapters = mutableListOf<StoryInfoNode>()
        var hasNextPage = true
        var cursor: String? = null

        while (hasNextPage) {
            val variables = StoryInfoListVariables(magazineIdOrAlias = manga.url, first = 100, after = cursor)
            val response = client.newCall(graphQlRequest("storyInfoList", variables, useAppHeaders = false)).execute()
            val data = response.parseAs<GraphQLResponse<ChapterListDto>>().data.magazine.storyInfos

            chapters.addAll(data.edges.map { it.node })
            hasNextPage = data.pageInfo.hasNextPage
            cursor = data.pageInfo.endCursor
        }

        chapters.mapIndexed { index, chapter ->
            SChapter.create().apply {
                url = "${manga.url}/${chapter.storyId}"
                name = (chapter.title + chapter.subtitle?.let { " $it" }.orEmpty()).trim()
                date_upload = chapter.contentsRelease
                chapter_number = (chapters.size - index).toFloat()
                val accessCondition = chapter.contentsAccessCondition
                val isPremiumType = accessCondition.typename != "FreeStoryContentsAccessCondition"
                val isPurchasable = chapter.isSellByStory && (accessCondition.info?.coins ?: 0) > 0

                if ((isPremiumType || isPurchasable) && !chapter.isPurchased) {
                    name = "\uD83E\uDE99 $name"
                }
            }
        }.reversed()
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val (alias, storyId) = chapter.url.split("/")
        val isWebSeries = alias in cacheWebOnlyAliases
        val variables = StoryReaderVariables(magazineIdOrAlias = alias, storyId = storyId)
        val apiRequest = graphQlRequest("magazineStoryForReader", variables, useAppHeaders = !isWebSeries)

        return client.newCall(apiRequest).asObservableSuccess().map { response ->
            val data = response.parseAs<GraphQLResponse<PageListDto>>().data
            if (data.magazine.storyContents.error != null) {
                throw Exception("This chapter is locked. Log in via WebView to read if you have premium or have purchased this chapter.")
            }
            val pageImages = data.magazine.storyContents.pageImages
                ?: throw Exception("Could not find page images")

            val pages = (1..pageImages.pageCount).map { i ->
                val imageUrl = "${pageImages.pageImageBaseURL}$i.jpg?${pageImages.pageImageSign}"
                Page(i - 1, imageUrl = updateImageUrlWidth(imageUrl))
            }.toMutableList()

            data.magazine.storyContents.afterword?.imageURL?.let {
                pages.add(Page(pages.size, imageUrl = updateImageUrlWidth(it)))
            }
            pages
        }
    }

    // Filter
    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(Filter.Header("NOTE: Search query ignores filters"))
        if (operationsMap != null) {
            filters.add(CategoryFilter(getCategoryList()))
        } else {
            filters.add(Filter.Header("Press 'Reset' to load filters"))
        }
        return FilterList(filters)
    }

    private class Category(val name: String, val id: String, val type: String) {
        override fun toString(): String = name
    }

    private fun getCategoryList() = listOf(
        Category("人気", "popular", "popular"),
        Category("完結", "finished", "finished"),
        Category("月曜日", "MONDAY", "day"),
        Category("火曜日", "TUESDAY", "day"),
        Category("水曜日", "WEDNESDAY", "day"),
        Category("木曜日", "THURSDAY", "day"),
        Category("金曜日", "FRIDAY", "day"),
        Category("土曜日", "SATURDAY", "day"),
        Category("日曜日", "SUNDAY", "day"),
    )

    private class CategoryFilter(categories: List<Category>) : Filter.Select<Category>("カテゴリー", categories.toTypedArray()) {
        val selected: Category
            get() = values[state]
    }

    // Unsupported
    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
