package eu.kanade.tachiyomi.extension.pt.mangeek

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

@Source
abstract class ManGeek : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    private val apiUrl = "http://geekstations.com.br/api/v2/pt".toHttpUrl()

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(
        fetchHome().catalogMangas().map { it.toSManga() },
        hasNextPage = false,
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(
        fetchHome().news
            .map { it.manga }
            .distinctBy { it.id }
            .map { it.toSManga() },
        hasNextPage = false,
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val includedTags = filters.includedTags()

        if (normalizedQuery.isBlank() && includedTags.isEmpty()) {
            return getPopularManga(page)
        }

        val mangas = if (normalizedQuery.isBlank()) {
            client.post(
                signedUrl("discover"),
                TagsBody(includedTags).toJsonRequestBody(),
            ).parseAs<List<MangaDto>>()
        } else {
            client.post(
                signedUrl("search", normalizedQuery),
                TagsBody(includedTags).toJsonRequestBody(),
            ).parseAs<List<MangaDto>>()
        }

        return MangasPage(
            mangas.distinctBy { it.id }.map { it.toSManga() },
            hasNextPage = false,
        )
    }

    private suspend fun fetchHome(): HomeDto = client.get(signedUrl("home")).parseAs<HomeDto>()

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = fetchHome().tags.toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<List<String>>() ?: return FilterList()

        return FilterList(TagsFilter(tags))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { url.pathSegments.getOrNull(0) == "manga" }
            ?: return null

        val manga = SManga.create().apply { this.url = id }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = client.get(signedUrl("manga", manga.url)).parseAs<MangaDto>()

        return SMangaUpdate(
            manga = dto.toSManga(details = true),
            chapters = dto.chapters
                .orEmpty()
                .asReversed()
                .map { it.toSChapter() },
        )
    }

    override val supportsRelatedMangas = false

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = throw UnsupportedOperationException()

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val primary = runCatching { fetchChapterPages("chapter", chapter.url) }.getOrNull()

        if (!primary.isNullOrEmpty()) {
            return primary
        }

        return fetchChapterPages("mirror", chapter.url)
    }

    private suspend fun fetchChapterPages(route: String, chapterId: String): List<Page> = client.get(signedUrl(route, chapterId)).parseAs<ChapterPagesDto>().toPageList()

    private fun signedUrl(route: String, input: String? = null): HttpUrl {
        val nonce = System.currentTimeMillis().toString(16).uppercase(Locale.ROOT)
        val signatureInput = input ?: nonce
        val key = md5("M<$signatureInput#MANG33K>D")

        return apiUrl.newBuilder()
            .addPathSegment(route)
            .addPathSegment(nonce)
            .apply { input?.let(::addPathSegment) }
            .addPathSegment(key)
            .build()
    }

    private fun md5(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
