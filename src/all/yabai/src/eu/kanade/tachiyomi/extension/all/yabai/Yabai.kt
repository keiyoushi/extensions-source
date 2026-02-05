package eu.kanade.tachiyomi.extension.all.yabai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Yabai : HttpSource() {
    override val name = "Yabai"

    override val baseUrl = "https://yabai.si"

    override val lang = "all"

    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::tokenInterceptor)
        .build()

    private var popularNextHash: String? = null

    private var searchNextHash: String? = null

    private var storedToken: String? = null

    private fun tokenInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val modifiedRequest = request.newBuilder()
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("X-Inertia", "true")
            .addHeader("X-Inertia-Version", "b6320c13b244af5aafcd16668b9b38e4")

        if (request.method == "POST") {
            modifiedRequest.addHeader("Content-Type", "application/json")

            if (request.header("X-XSRF-TOKEN") == null) {
                val token = getToken()
                val response = chain.proceed(
                    modifiedRequest
                        .addHeader("X-XSRF-TOKEN", token)
                        .build(),
                )

                if (!response.isSuccessful && response.code == 419) {
                    response.close()
                    storedToken = null
                    val newToken = getToken()
                    return chain.proceed(
                        modifiedRequest
                            .addHeader("X-XSRF-TOKEN", newToken)
                            .build(),
                    )
                }

                return response
            }
        }

        return chain.proceed(modifiedRequest.build())
    }

    private fun getToken(): String {
        if (storedToken.isNullOrEmpty()) {
            val request = GET(baseUrl, headers)
            val response = client.newCall(request).execute()

            var found = false

            val headers = response.headers("Set-Cookie")
            headers.forEach {
                if (it.startsWith("XSRF-TOKEN=")) {
                    storedToken = it
                        .split(";")
                        .first()
                        .substringAfter("=")
                        .replace("%3D", "=")
                    found = true
                }
            }

            if (!found) {
                throw IOException("Unable to find CSRF token")
            }
        }

        return storedToken!!
    }

    override fun getFilterList() = getFilters()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page == 1) {
            searchNextHash = null
        }

        val queryBody = QueryDto(
            qry = query,
            cursor = searchNextHash,
        ).apply {
            filters.forEach { filter ->
                when (filter) {
                    is SelectFilter -> {
                        when (filter.name) {
                            "Category" -> {
                                categories[filter.vals[filter.state]]?.let {
                                    cat = it.toString()
                                }
                            }

                            "Language" -> {
                                languages[filter.vals[filter.state]]?.let {
                                    lng = it
                                }
                            }

                            else -> {}
                        }
                    }

                    else -> {}
                }
            }
        }

        return POST(
            "$baseUrl/g",
            headers,
            queryBody.toJsonString().toRequestBody(),
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<DataResponse<IndexProps>>()

        val galleries = data.props.postList.data.map {
            it.toSManga()
        }

        searchNextHash = data.props.postList.meta.nextCursor

        return MangasPage(galleries, searchNextHash != null)
    }

    override fun popularMangaRequest(page: Int): Request {
        if (page == 1) {
            popularNextHash = null
        }

        val queryBody = QueryDto(
            cursor = popularNextHash,
        )

        return POST(
            "$baseUrl/g",
            headers,
            queryBody.toJsonString().toRequestBody(),
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<DataResponse<IndexProps>>()

        val galleries = data.props.postList.data.map {
            it.toSManga()
        }

        popularNextHash = data.props.postList.meta.nextCursor

        return MangasPage(galleries, popularNextHash != null)
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun mangaDetailsRequest(manga: SManga) = GET(
        "$baseUrl${manga.url}",
        headers,
    )

    override fun mangaDetailsParse(response: Response) = response.parseAs<DataResponse<DetailProps>>().props.post.data.toSManga()

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response) = listOf(response.parseAs<DataResponse<DetailProps>>().props.post.data.toSChapter())

    override fun pageListRequest(chapter: SChapter) = GET(
        "$baseUrl${chapter.url}/read",
        headers,
    )

    override fun pageListParse(response: Response) = response.parseAs<DataResponse<ReaderProps>>().props.pages.data.list.toPages()

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        val createdAtFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
