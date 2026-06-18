package eu.kanade.tachiyomi.extension.ja.mangasaison

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.GraphQLErrorInterceptor
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLPost
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.zip.readZipEntry
import keiyoushi.zip.zipDirectory
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okio.buffer
import java.io.IOException
import java.net.URLEncoder

class MangaSaison :
    HttpSource(),
    ConfigurableSource {
    override val name = "Manga Saison"
    override val lang = "ja"
    override val baseUrl = "https://mechacomi.jp"
    override val supportsLatest = true

    private val pageLimit = 30
    private val apiUrl = "$baseUrl/api/query"
    private val viewerUrl = "$baseUrl/api/v1/mdviewer"
    private val algoliaUrl = "https://L66VA7452H-dsn.algolia.net/1/indexes/*/queries"
    private val preferences by getPreferencesLazy()
    private val algoliaHeaders = headersBuilder()
        .add("X-Algolia-Application-Id", "L66VA7452H")
        .add("X-Algolia-Api-Key", "dfe863b14fd0035402b32fa3bc00d27c")
        .build()

    override val client = network.client.newBuilder()
        .addInterceptor(GraphQLErrorInterceptor())
        .addInterceptor(ImageInterceptor())
        .addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.code == 500 && (request.url.pathSegments[3] == "access-provider" || request.url.pathSegments[2] == "mdviewer")) {
                throw IOException("Log in via WebView and purchase this product to read.")
            }
            response
        }
        .build()

    override fun popularMangaRequest(page: Int) = graphQLPost(
        apiUrl,
        headers,
        POPULAR_QUERY,
        "storeLatestWeeklySalesRankings",
        PopularVariables(listOf("overall"), 100),
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseGraphQLAs<RankingResponse>()
        val mangas = result.storeLatestWeeklySalesRankings.flatMap { it.ranking.map(Ranking::toSManga) }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = graphQLPost(
        apiUrl,
        headers,
        LATEST_QUERY,
        "newArrivalContents",
        LatestVariables("general", pageLimit, (page - 1) * pageLimit),
    )

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseGraphQLAs<LatestResponse>()
        val mangas = result.newArrivalContents.map { it.toSManga() }
        val hasNextPage = result.newArrivalContents.size >= pageLimit
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = SearchRequestBody(
            listOf(
                SearchRequest(
                    "cominavi",
                    "filters=isSearchable=1 AND isValid=1 AND payTitleId=-1&query=${URLEncoder.encode(query, "UTF-8")}&hitsPerPage=36&page=${page - 1}&attributesToRetrieve=titleId,titleName,compressedTitleThumbnailPath&attributesToHighlight=&attributesToSnippet=&clickAnalytics=false&typoTolerance=false&restrictSearchableAttributes=titleName,titleNameHira,titleNameKana,authors.authorName,authors.authorNameHira,authors.authorNameKana,publisherName",
                ),
            ),
        ).toJsonRequestBody()
        return POST(algoliaUrl, algoliaHeaders, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>().results
        val mangas = result.flatMap { it.hits.map(Hit::toSManga) }
        return MangasPage(mangas, result.any { it.hasNextPage() })
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/titles/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga) = graphQLPost(
        apiUrl,
        headers,
        DETAILS_QUERY,
        "bookTitle",
        DetailsVariables(manga.url),
    )

    override fun mangaDetailsParse(response: Response): SManga = response.parseGraphQLAs<DetailsResponse>().bookTitle.toSManga()

    override fun chapterListRequest(manga: SManga): Request = graphQLPost(
        apiUrl,
        headers,
        CHAPTER_LIST_QUERY,
        "bookContents",
        ChapterListVariables(manga.url.toInt(), 0, "desc"),
    )

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val result = response.parseGraphQLAs<ChapterResponse>()
        return result.bookContents
            .filter { !hideLocked || (!it.isLocked && !it.isPreview) }
            .map { it.toSChapter() }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/viewer/mdviewer/browser/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$baseUrl/api/v1/mdviewer/content".toHttpUrl().newBuilder()
            .addQueryParameter("distributionId", chapter.url)
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val distributionId = response.request.url.queryParameter("distributionId")
        val result = response.parseAs<ViewerResponse>()
        val accessUrl = "$viewerUrl/access-provider".toHttpUrl().newBuilder().apply {
            if (result.contentType != "main") {
                addPathSegment(result.contentType)
            }
        }
            .addQueryParameter("contentId", result.contentId)
            .addQueryParameter("distributionId", distributionId)
            .build()

        val access = client.newCall(GET(accessUrl, headers)).execute().parseAs<ContentResponse>()
        val uzeUrl = access.url
        val token = access.token

        val entries = client.zipDirectory(uzeUrl, headers).entries
        val byName = entries.associateBy { it.name }

        val pkgEntry = byName[PACKAGE_JSON]
            ?: entries.firstOrNull { it.name == "package.json" || it.name.endsWith("/package.json") }
            ?: throw Exception("manifest not found in .uze (looked for $PACKAGE_JSON). ${entries.size} entries, ${entries.take(5).joinToString { it.name }}")

        val pkg = client.readZipEntry(uzeUrl, pkgEntry, headers).buffer().inputStream().parseAs<MdPackage>()

        return pkg.spine.mapIndexedNotNull { index, item ->
            val entry = byName[item.href] ?: return@mapIndexedNotNull null
            val fragment = listOf(
                token,
                entry.localHeaderOffset,
                entry.compressedSize,
                entry.method,
            ).joinToString(";")

            val pageUrl = uzeUrl.toHttpUrl().newBuilder()
                .fragment(fragment)
                .build()
                .toString()
            Page(index, imageUrl = pageUrl)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val PACKAGE_JSON = "META-INF/package.json"
    }
}
