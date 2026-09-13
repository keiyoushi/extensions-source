package eu.kanade.tachiyomi.extension.id.komiknesia

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import okio.ByteString.Companion.decodeBase64
import java.io.IOException
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class KomikNesia : KeiSource() {

    private val apiUrl = "https://api-be.komiknesia.my.id/api"

    private inline fun <reified T> Response.parseAs(): T = parseAs(
        transform = { bodyString ->
            if (bodyString.contains("\"encrypted\":true") || bodyString.contains("\"encrypted\": true")) {
                val envelope = bodyString.parseAs<EncryptedEnvelopeDto>()
                decrypt(envelope.data, envelope.time)
            } else {
                bodyString
            }
        },
    )

    private fun decrypt(encryptedData: String, time: Double): String {
        try {
            val keyStr = String.format(Locale.US, "%.8f", time / 32.0).padEnd(32, '0').take(32)
            val raw = encryptedData.decodeBase64()?.toByteArray()
                ?: throw IOException("Failed to decode base64 encrypted data")
            if (raw.size < 16) throw IOException("Encrypted data too short")
            val iv = raw.copyOfRange(0, 16)
            val ciphertext = raw.copyOfRange(16, raw.size)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyStr.toByteArray(Charsets.UTF_8), "AES"), IvParameterSpec(iv))
            return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            throw IOException("Failed to decrypt API response", e)
        }
    }

    // ===============================
    // Popular
    // ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(OrderFilter().apply { state = 2 }))

    // ===============================
    // Latest
    // ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    // ===============================
    // Search
    // ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/contents".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        filter.state
                            .filter { it.state }
                            .forEach { addQueryParameter("genre[]", it.id) }
                    }
                    is StatusFilter -> {
                        if (filter.state != 0) {
                            addQueryParameter("status", filter.toUriPart())
                        }
                    }
                    is OrderFilter -> {
                        if (filter.state != 0) {
                            addQueryParameter("orderBy", filter.toUriPart())
                        }
                    }
                    else -> {}
                }
            }
        }.build()

        val payload = client.get(url).parseAs<PayloadDto<List<MangaDto>>>()
        val mangas = payload.data.map { it.toSManga() }
        val hasNextPage = payload.meta?.let { it.page < it.totalPages } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    // ===============================
    // Details
    // ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.removePrefix("/komik/").removePrefix("/")
        val payload = client.get("$apiUrl/comic/$slug")
            .parseAs<PayloadDto<MangaDto>>()
        return SMangaUpdate(
            payload.data.toSManga().apply { initialized = true },
            payload.data.chapters?.map { it.toSChapter() } ?: emptyList(),
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = runCatching {
        if (!url.host.endsWith("komiknesiaku.com")) return null
        val pathSegments = url.pathSegments.filter { it.isNotEmpty() }
        if (pathSegments.firstOrNull() != "komik") return null
        val slug = pathSegments.getOrNull(1) ?: return null
        val payload = client.get("$apiUrl/comic/$slug")
            .parseAs<PayloadDto<MangaDto>>()
        payload.data.toSManga().apply { initialized = true }
    }.getOrNull()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/komik/${manga.url.removePrefix("/komik/").removePrefix("/")}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/view/${chapter.url.removePrefix("/view/").removePrefix("/")}"

    // ===============================
    // Pages
    // ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = chapter.url.removePrefix("/view/").removePrefix("/")
        val payload = client.get("$apiUrl/chapters/slug/$slug")
            .parseAs<PayloadDto<PageListDto>>()
        return payload.data.images.mapIndexed { idx, img ->
            Page(idx, imageUrl = img)
        }
    }

    // ===============================
    // Filters
    // ===============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/contents/genres").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<PayloadDto<List<GenreDto>>>()?.data
            ?.map { Genre(it.name, it.id.toString()) }

        return FilterList(
            listOfNotNull(
                OrderFilter(),
                StatusFilter(),
                genres?.takeIf { it.isNotEmpty() }?.let {
                    GenreFilter(it)
                },
            ),
        )
    }
}
