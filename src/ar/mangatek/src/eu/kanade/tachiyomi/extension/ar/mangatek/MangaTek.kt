package eu.kanade.tachiyomi.extension.ar.mangatek

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class MangaTek : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(SpeechBubblePainterInterceptor())
        rateLimit(3)
    }

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()

        val mangas = document.select(".flex-grow .grid a").map { element ->
            SManga.create().apply {
                title = element.select("h3").attr("title")
                setUrlWithoutDomain(element.attr("abs:href"))
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }

        val hasNextPage = document.selectFirst("nav a[aria-disabled=false] .fa-chevron-left") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/manga-list?sort=views&page=$page")
        return response.toMangasPage()
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/manga-list?page=$page")
        return response.toMangasPage()
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga-list".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("search", query.trim())
            }
            addQueryParameter("page", page.toString())
            filters.filterIsInstance<UrlPartFilter>().forEach {
                it.addUrlParameter(this)
            }
        }.build()
        return client.get(url).toMangasPage()
    }

    // ============================== Filters ==============================

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val response = client.get("$API_BASE/api/tags?limit=500")
        return response.parseAs<JsonElement>()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<TagsResponse>()?.data
            .orEmpty()
            .filter { it.counter > 0 }
            .map { it.name }
            .distinct()
            .sorted()

        return FilterList(
            SortFilter(),
            StatusFilter(),
            GenreFilter(tags),
        )
    }

    // ========================= Details & Chapters  =========================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        check(url.pathSegments.size >= 2) { "Unsupported URL" }
        val slug = url.pathSegments[1]
        val manga = SManga.create().apply {
            this.url = "/manga/$slug"
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga.apply {
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$baseUrl${manga.url}".toHttpUrl()
        val data: MangaDto = client.get(url).asJsoup().extractAstroProp("manga")
        val slug = url.pathSegments[1]

        return SMangaUpdate(
            data.manga.toSManga(manga.url),
            data.manga.chapters.map { it.toSChapter(slug) },
        )
    }

    //  ============================== Astro ==============================

    private inline fun <reified T> Document.extractAstroProp(key: String): T {
        val prop = selectFirst("[props*=$key]")?.attr("props")
            ?: throw Exception("Unable to find prop with $key")
        return prop.parseAs<JsonElement>().unwrapAstro().parseAs()
    }

    private fun JsonElement.unwrapAstro(): JsonElement = when (this) {
        is JsonArray -> when {
            size == 2 && this[0] is JsonPrimitive -> this[1].unwrapAstro()
            else -> JsonArray(map { it.unwrapAstro() })
        }
        is JsonObject -> JsonObject(mapValues { it.value.unwrapAstro() })
        else -> this
    }

    //  ============================== Page ==============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        val props: ChapterProps = document.extractAstroProp("imageUrls")

        val (overlayData, apiOffset) = props.overlayBlob?.let { blob ->
            decrypt(blob) to props.overlayPageOffset
        } ?: run {
            val chapterId = props.chapterId
            val unlockToken = props.unlockToken
            if (chapterId != null && unlockToken != null) {
                unlockOverlay(chapterId, unlockToken)
            } else {
                null
            }
        } ?: (null to null)

        val offset = apiOffset ?: props.overlayPageOffset ?: 0
        val overlaysByPageNumber: Map<Int, OverlayPage> = overlayData?.pages
            ?.associateBy { it.pageNumber } ?: emptyMap()

        return props.imageUrls.mapIndexed { index, imageUrl ->
            val overlayPage = if (overlaysByPageNumber.isNotEmpty()) {
                overlaysByPageNumber[index - offset + 1]
                    ?: overlaysByPageNumber[index - offset]
                    ?: overlaysByPageNumber[index]
            } else {
                null
            }

            if (overlayPage == null || overlayPage.overlays.isEmpty()) {
                Page(index, imageUrl = imageUrl)
            } else {
                val url = imageUrl.toHttpUrl().newBuilder()
                    .fragment(overlayPage.overlays.toJsonString())
                    .build().toString()

                Page(index, imageUrl = url)
            }
        }
    }

    private suspend fun unlockOverlay(chapterId: Long, unlockToken: String): Pair<OverlayData, Int?>? {
        val proof = "$UNLOCK_PROOF_SALT|$unlockToken|$chapterId".sha256Hex()
        val payload = buildJsonObject {
            put("chapterId", chapterId)
            put("token", unlockToken)
            put("proof", proof)
        }
        val response = client.post(
            url = UNLOCK_API_URL,
            body = payload.toJsonRequestBody(),
        )
        val unlockResponse = response.parseAs<UnlockResponse>()
        val overlay = unlockResponse.overlay ?: return null
        val key = unlockResponse.key ?: KEY
        val overlayData = decrypt(overlay, key)
        return overlayData to unlockResponse.overlayPageOffset
    }

    private fun String.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-url") -> attr("abs:data-url")
        hasAttr("data-zoom-src") -> attr("abs:data-zoom-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    // decrypt

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex string length" }

        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun decrypt(blob: String, keyHex: String = KEY): OverlayData {
        val (ivHex, ctHex, tagHex) = blob.split(":").also {
            require(it.size == 3) { "unexpected overlayBlob format" }
        }

        val iv = ivHex.hexToBytes()
        val ciphertext = ctHex.hexToBytes()
        val tag = tagHex.hexToBytes()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyHex.hexToBytes(), "AES"),
                GCMParameterSpec(tag.size * 8, iv),
            )
        }

        return String(cipher.doFinal(ciphertext + tag), Charsets.UTF_8).parseAs<OverlayData>()
    }

    companion object {
        val PAGE_REGEX = Regex(""".*?\.(webp|png|jpg|jpeg)(?:\?[^#]*)?#\[.*?]""", RegexOption.IGNORE_CASE)
        private const val KEY = "ff453871399fe268588a0936b45376022d85ed0fd1292001d5102f6a30291dc1"
        private const val UNLOCK_PROOF_SALT = "322c4e08571941fa05abf1a6a2b45c9a9bf7bcc94af61b66"
        private const val API_BASE = "https://api.mangatek.com"
        private const val UNLOCK_API_URL = "$API_BASE/api/reader/unlock"
    }
}
