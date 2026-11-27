package eu.kanade.tachiyomi.extension.th.readtoon

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesEncrypt
import keiyoushi.utils.asJsoup
import keiyoushi.utils.cryptoJsDecrypt
import keiyoushi.utils.evpBytesToKey
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.SecureRandom
import kotlin.time.Clock

@Source
abstract class ReadToon : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val query = StatsQuery("views", START_DATE, Clock.System.now().toString(), "desc", page, PAGE_SIZE, CONTENT_TYPE)
        return trpcGet<ContentListDto>("contentStats.getStats", TrpcInput(query, DATE_META).toJsonString()).toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = trpcGet<ContentListDto>("content.list", TrpcInput(ListQuery("", "chapterAt", "desc", page, PAGE_SIZE, CONTENT_TYPE)).toJsonString()).toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()
        val listQuery = ListQuery(
            q = query.trim(),
            sort = if (query.isNotBlank()) "relevant" else sort?.sort ?: "chapterAt",
            order = sort?.order ?: "desc",
            page = page,
            limit = PAGE_SIZE,
            contentType = CONTENT_TYPE,
            genres = filters.firstInstanceOrNull<GenreFilter>()?.selected?.ifEmpty { null },
        )
        return trpcGet<ContentListDto>("content.list", TrpcInput(listQuery).toJsonString()).toMangasPage()
    }

    private fun ContentListDto.toMangasPage() = MangasPage(content.map { it.toSManga() }, hasNextPage)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "content") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        // Novels share the same links
        val content = fetchContent(slug).takeIf { it.isManga } ?: return null
        return content.toSManga().apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/content/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/content/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val content = fetchContent(manga.url)
        return SMangaUpdate(content.toSManga(), content.chapters.map { it.toSChapter(manga.url) })
    }

    private suspend fun fetchContent(slug: String) = client.get("$baseUrl/content/$slug", Fetch.DOCUMENT).asJsoup()
        .extractNextJs<ContentPageDto> { it is JsonObject && "slug" in it && "chapters" in it && "description" in it }!!

    // As in the site's reader, the image list is CryptoJS-encrypted with keys that come with it
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.name.startsWith("🔒")) return emptyList()
        val chapterUrl = getChapterUrl(chapter)
        val slug = chapter.url.substringBefore("/")
        val number = chapter.url.substringAfter("/").toDouble()

        val timestamp = Clock.System.now().epochSeconds
        val keyData = trpcGet<String>("chapter.getEncryptKey", TrpcInput(KeyQuery(slug, number, timestamp)).toJsonString(), chapterUrl)
        val info = keyData.payload().cryptoJsDecrypt("${timestamp}0").parseAs<KeyInfoDto>()
        // The site's reader stops a visitor it takes for a bot behind a Turnstile check
        if (info.bot) throw Exception("Open the chapter in WebView first")
        val key = info.html.cryptoJsDecrypt(KEY_PASSPHRASE)
        val listKey = info.body.cryptoJsDecrypt(key)

        val body = TrpcInput(DecryptQuery(slug, number, key.cryptoJsEncrypt("0"))).toJsonRequestBody()
        val data = client.post("$baseUrl/api/trpc/chapter.decryptChapter", body = body, fetch = Fetch.XHR.from(chapterUrl))
            .parseAs<TrpcResponse<DecryptDto>>().data.data
        val pages = data.payload().cryptoJsDecrypt(listKey).parseAs<PagesDto>()
        return pages.result.mapIndexed { index, path -> Page(index, imageUrl = "${pages.host()}/$path") }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    private suspend inline fun <reified T> trpcGet(procedure: String, input: String, from: String? = null): T {
        val url = "$baseUrl/api/trpc".toHttpUrl().newBuilder()
            .addPathSegment(procedure)
            .addQueryParameter("input", input)
            .build()
        val fetch = if (from != null) Fetch.XHR.from(from) else Fetch.XHR
        return client.get(url, fetch).parseAs<TrpcResponse<T>>().data
    }

    // Encrypted values come as "[uuid]" followed by the CryptoJS text
    private fun String.payload() = substringAfter("]")

    private fun String.cryptoJsEncrypt(passphrase: String): String {
        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }
        val (key, iv) = evpBytesToKey(passphrase.encodeToByteArray(), salt)
        val encrypted = encodeToByteArray().aesEncrypt(aesCbc(key, iv, encrypt = true))
        return Base64.encodeToString(SALTED + salt + encrypted, Base64.NO_WRAP)
    }

    companion object {
        private const val PAGE_SIZE = 24

        // Novels are left out: their chapters are text
        private const val CONTENT_TYPE = "manga"
        private const val START_DATE = "2000-01-01T00:00:00.000Z"
        private val DATE_META = TrpcMeta(mapOf("startDate" to listOf("Date"), "endDate" to listOf("Date")))
        private const val KEY_PASSPHRASE = "<rect width=\"100\" height=\"100\" x=\"50\" y=\"50\" fill=\"purple\" /> "
        private val SALTED = "Salted__".encodeToByteArray()
    }
}
