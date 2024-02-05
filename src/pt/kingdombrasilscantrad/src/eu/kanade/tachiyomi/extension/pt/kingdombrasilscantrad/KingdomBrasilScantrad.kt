package eu.kanade.tachiyomi.extension.pt.kingdombrasilscantrad

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class KingdomBrasilScantrad : HttpSource() {

    override val name = "Kingdom Brasil Scantrad"

    override val baseUrl = "https://www.kingdombrasil.net"

    private val cdnUrl = "https://static.wixstatic.com/media"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            if (request.url.pathSegments[0] != "blog-frontend-adapter-public" || response.code != 403) {
                return@addInterceptor response
            }

            response.close()
            getWixCookies()
            chain.proceed(request)
        }
        .build()

    private val json: Json by injectLazy()

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            url = "/capitulos"
            title = "Kingdom"
            author = "Hara Yasuhisa"
            artist = "Hara Yasuhisa"
            description = "Durante o período dos Reinos Combatentes na China, Li Xin e Piao são dois jovens irmãos que sonham em se tornar grandes generais, apesar de seu baixo status de escravos órfãos. Um dia, eles encontram um homem de nobreza, que dá a Piao a oportunidade de realizar um importante dever dentro do palácio real de Qin. Separando-se, Xin e Piao prometem um dia se tornarem os maiores generais do mundo. No entanto, após um feroz golpe de estado ocorrer no palácio, Xin se encontra com um Piao moribundo, cujas últimas palavras o estimulam a entrar em ação e o levam a encontrar o jovem e futuro rei de Qin, Ying Zheng."
            genre = "Ação, Aventura, Drama, Histórico, Seinen"
            status = SManga.ONGOING
            thumbnail_url = "https://i.imgur.com/jomSsRZ.jpeg"
            initialized = true
        }

        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if ("kingdom".contains(query.lowercase())) {
            return fetchPopularManga(page)
        }

        return Observable.just(MangasPage(emptyList(), false))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response) =
        throw UnsupportedOperationException()

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    private fun pagedChapterListRequest(page: Int) =
        GET(
            "$baseUrl/blog-frontend-adapter-public/v2/post-feed-page?includeContent=false&languageCode=pt&page=$page&pageSize=50&type=ALL_POSTS",
            headers,
        )

    override fun chapterListRequest(manga: SManga) = pagedChapterListRequest(1)

    override fun chapterListParse(response: Response): List<SChapter> {
        var page = 1
        var data = response.parseAs<PostFeedPageResponse>().postFeedPage.posts

        return buildList {
            addAll(data.posts.map { it.toSChapter() })

            while (data.pagingMetaData.offset + 50 < data.pagingMetaData.total) {
                page++
                data = client.newCall(pagedChapterListRequest(page))
                    .execute()
                    .parseAs<PostFeedPageResponse>()
                    .postFeedPage
                    .posts
                addAll(data.posts.map { it.toSChapter() })
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val slug = chapter.url.substringAfterLast("/")

        return GET(
            "$baseUrl/blog-frontend-adapter-public/v2/post-page/$slug?postId=$slug&translationsName=main&languageCode=pt",
            headers,
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PostPageResponse>()

        return data.postPage.post.content!!.entityMap.values
            .first { it.type == "wix-draft-plugin-gallery" }
            .data
            .items
            .mapIndexed { i, it ->
                Page(i, imageUrl = "$cdnUrl/${it.url}")
            }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun getWixCookies() =
        client.newCall(GET("$baseUrl/_api/v2/dynamicmodel", headers)).execute().close()

    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromString(body.string())

    private fun PostDto.toSChapter() = SChapter.create().apply {
        url = this@toSChapter.url.path
        name = title
        date_upload = runCatching {
            dateFormat.parse(firstPublishedDate)!!.time
        }.getOrDefault(0L)
    }
}
