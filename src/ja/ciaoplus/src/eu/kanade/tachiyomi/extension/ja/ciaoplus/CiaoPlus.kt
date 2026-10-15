package eu.kanade.tachiyomi.extension.ja.ciaoplus

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.security.MessageDigest

@Source
abstract class CiaoPlus : KeiSource() {
    private val apiUrl get() = "https://api.ciao.shogakukan.co.jp"
    private val pageLimit = 25

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage = getRanking("1", page)

    private suspend fun getRanking(rankingId: String, page: Int): MangasPage {
        val offset = (page - 1) * pageLimit
        val url = "$apiUrl/ranking/all".toHttpUrl().newBuilder()
            .addQueryParameter("platform", "3")
            .addQueryParameter("ranking_id", rankingId)
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", "26")
            .addQueryParameter("is_top", "0")
            .build()

        val titleIds = hashedGet(url).parseAs<RankingApiResponse>().rankingTitleList
            .map { it.id.toString().padStart(5, '0') }

        if (titleIds.isEmpty()) return MangasPage(emptyList(), false)

        val hasNextPage = titleIds.size > pageLimit
        val detailsUrl = "$apiUrl/title/list".toHttpUrl().newBuilder()
            .addQueryParameter("platform", "3")
            .addQueryParameter("title_id_list", titleIds.take(pageLimit).joinToString(","))
            .build()

        val result = hashedGet(detailsUrl).parseAs<TitleListResponse>()
        val mangas = result.titleList.map { it.toSManga() }
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/title/weekly".toHttpUrl().newBuilder()
            .addQueryParameter("platform", "3")
            .build()

        val result = hashedGet(url).parseAs<TitleListResponse>()
        val mangas = result.titleList
            .sortedByDescending { it.episodeFreeUpdated }
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$apiUrl/search/title".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .addQueryParameter("limit", "99999")
                .addQueryParameter("platform", "3")
                .build()

            return hashedGet(url).toMangasPage()
        }

        val filter = filters.firstInstance<CategoryFilter>()
        if (filter.type == FilterType.RANKING) {
            return getRanking(filter.id, page)
        }

        val url = "$apiUrl/search/title".toHttpUrl().newBuilder()
            .addQueryParameter("platform", "3")
            .addQueryParameter("genre_id", filter.id)
            .addQueryParameter("limit", "99999")
            .build()

        return hashedGet(url).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val result = this.parseAs<TitleListResponse>()
        val mangas = result.titleList.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategoryFilter(),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comics/title/${manga.url.substringAfterLast("/")}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val titleId = manga.url.substringAfterLast("/") // for old url compatibility
        val title = async {
            val url = "$apiUrl/title/list".toHttpUrl().newBuilder()
                .addQueryParameter("platform", "3")
                .addQueryParameter("title_id_list", titleId)
                .build()
            hashedGet(url).parseAs<DetailResponse>().titleList.first()
        }

        val details = async {
            if (!fetchDetails) return@async manga
            val result = title.await()
            if (result.genreIdList.isNullOrEmpty()) return@async result.toSManga(null)

            val url = "$apiUrl/genre/list".toHttpUrl().newBuilder()
                .addQueryParameter("platform", "3")
                .addQueryParameter("genre_id_list", result.genreIdList.joinToString(","))
                .build()
            val genres = hashedGet(url).parseAs<GenreListResponse>().genreList?.joinToString { it.genreName }
            result.toSManga(genres)
        }

        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val result = title.await()
            if (result.episodeIdList.isNullOrEmpty()) return@async emptyList()

            val episodeIdList = result.episodeIdList.joinToString(",")
            val body = FormBody.Builder()
                .add("platform", "3")
                .add("episode_id_list", episodeIdList)
                .build()

            val params = mapOf("platform" to "3", "episode_id_list" to episodeIdList)
            client.post("$apiUrl/episode/list", hashedHeaders(params), body)
                .parseAs<EpisodeListResponse>().episodeList
                .map { it.toSChapter(result.titleName) }
                .reversed()
        }

        SMangaUpdate(
            details.await(),
            chapterList.await(),
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/comics/title/${chapter.memo["titleId"]!!.string}/episode/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$apiUrl/web/episode/viewer".toHttpUrl().newBuilder()
            .addQueryParameter("platform", "3")
            .addQueryParameter("episode_id", chapter.url)
            .build()

        val result = hashedGet(url).parseAs<ViewerApiResponse>()
        val fragment = if (result.scrambleVer == 2) "scramble_seed_v2" else "scramble_seed"
        return result.pageList.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = "$imageUrl#$fragment=${result.scrambleSeed}")
        }
    }

    private fun generateHash(params: Map<String, String>): String {
        val paramStrings = params.toSortedMap().map { (key, value) ->
            getHashedParam(key, value)
        }
        val joinedParams = paramStrings.joinToString(",")
        val hash1 = joinedParams.hash("SHA-256")
        return hash1.hash("SHA-512")
    }

    private fun getHashedParam(key: String, value: String): String {
        val keyHash = key.hash("SHA-256")
        val valueHash = value.hash("SHA-512")
        return "${keyHash}_$valueHash"
    }

    private fun String.hash(algorithm: String): String = MessageDigest.getInstance(algorithm).digest(toByteArray()).toHexString()

    private fun hashedHeaders(params: Map<String, String>): Headers = headersBuilder()
        .set("X-Bambi-Hash", generateHash(params))
        .build()

    private suspend fun hashedGet(url: HttpUrl): Response {
        val queryParams = url.queryParameterNames.associateWith { url.queryParameter(it)!! }
        return client.get(url, hashedHeaders(queryParams))
    }
}
