package eu.kanade.tachiyomi.extension.tr.mangacix

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
import keiyoushi.utils.aesEncrypt
import keiyoushi.utils.aesGcm
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.ByteString.Companion.toByteString
import java.security.SecureRandom
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Mangacix : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 3, period = 1.seconds)
        .addInterceptor(MangacixAuthInterceptor())

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/secure/titles?onlyStreamable=true&page=$page&perPage=16&order=popularity")
        val dto = response.parseAs<TitlesResponseDto>()
        return MangasPage(dto.pagination.data.map { it.toSManga() }, dto.pagination.currentPage < dto.pagination.lastPage)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/secure/last-episodes?page=$page")
        val dto = response.parseAs<LastEpisodesResponseDto>()
        val mangas = dto.data.distinctBy { it.titleId }.map { it.toSManga() }
        return MangasPage(mangas, dto.currentPage < dto.lastPage)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/secure/titles".toHttpUrl().newBuilder().apply {
            addQueryParameter("onlyStreamable", "true")
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", "16")

            if (query.isNotBlank()) {
                addQueryParameter("query", query)
            }

            filters.firstInstanceOrNull<SortFilter>()?.let {
                addQueryParameter("order", it.selected)
            }

            filters.firstInstanceOrNull<KeywordFilter>()?.let { group ->
                val selected = group.state.filter { it.state }.map { it.value }
                if (selected.isNotEmpty()) {
                    addQueryParameter("keyword", selected.joinToString(","))
                }
            }
        }.build()

        val response = client.get(url)
        val dto = response.parseAs<TitlesResponseDto>()
        return MangasPage(dto.pagination.data.map { it.toSManga() }, dto.pagination.currentPage < dto.pagination.lastPage)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        KeywordFilter(),
    )

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "books") return null
        val id = url.pathSegments.getOrNull(1)?.toLongOrNull() ?: return null
        val manga = SManga.create().apply { this.url = id.toString() }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/books/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get("$baseUrl/secure/titles/${manga.url}?titleId=${manga.url}")
        val dto = response.parseAs<TitleDetailsResponseDto>()
        val updatedManga = dto.title.toSManga()
        val chapterList = dto.title.episodes
            ?.sortedByDescending { it.episodeNumber }
            ?.map { it.toSChapter() }
            ?: emptyList()
        return SMangaUpdate(updatedManga, chapterList)
    }

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String {
        val segments = chapter.url.split("/")
        return if (segments.size >= 2) {
            "$baseUrl/books/${segments[0]}/episode/${segments[1]}"
        } else {
            "$baseUrl/books/${chapter.url}"
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.split("/")
        val titleId = segments[0]
        val episodeNumber = segments.getOrNull(1) ?: return emptyList()

        val response = client.get("$baseUrl/secure/titles/$titleId?titleId=$titleId&episodeNumber=$episodeNumber")
        val dto = response.parseAs<TitleDetailsResponseDto>()
        val images = dto.title.episodeImages ?: return emptyList()

        return images.sortedBy { it.itemOrder }.mapIndexed { index, img ->
            Page(index, imageUrl = img.url)
        }
    }

    // =============================== Crypto ===============================

    private class MangacixAuthInterceptor : Interceptor {
        private val keyBytes = ("i4C7R2fXGocdYg" + "FLzCbDlsJ" + "jukf8G58b").toByteArray(Charsets.UTF_8)
        private val random = SecureRandom()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url
            if (request.method == "GET" && url.encodedPath.startsWith("/secure/")) {
                val query = url.encodedQuery.orEmpty()
                val signedRequest = request.newBuilder()
                    .header("X-E-H", generateXeh(query))
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                return chain.proceed(signedRequest)
            }
            return chain.proceed(request)
        }

        private fun generateXeh(queryString: String): String {
            val iv = ByteArray(12).apply { random.nextBytes(this) }
            val plaintext = ("{version}" + queryString).toByteArray(Charsets.UTF_8)
            val ciphertext = plaintext.aesEncrypt(aesGcm(keyBytes, iv, encrypt = true))
            return "${ciphertext.toByteString().base64()}.${iv.toByteString().base64()}"
        }
    }
}
