package eu.kanade.tachiyomi.extension.zh.rumanhuaunoriginal

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// The name and logo of 如漫画 (rumanhua.com, now rumanhua2.com) on another site with its own catalogue
@Source
abstract class Rumanhua : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getList("custom/top", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getList("custom/update", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/index.php/search".toHttpUrl().newBuilder()
                .addQueryParameter("key", query.trim())
                .build()
            return parseList(client.get(url, Fetch.DOCUMENT).asJsoup(), page)
        }
        return getList(filters.firstInstanceOrNull<ListFilter>()?.path ?: "custom/top", page)
    }

    private suspend fun getList(path: String, page: Int): MangasPage {
        val url = if (page > 1) "$baseUrl/$path/page/$page" else "$baseUrl/$path"
        return parseList(client.get(url, Fetch.DOCUMENT).asJsoup(), page)
    }

    private fun parseList(document: Document, page: Int): MangasPage {
        val mangas = document.select("ul.rankList > li").map { item ->
            SManga.create().apply {
                setUrlWithoutDomain(item.selectFirst("a")!!.absUrl("href"))
                title = item.selectFirst(".title")!!.text()
                author = item.selectFirst(".bottom")?.ownText()?.ifEmpty { null }
                thumbnail_url = item.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a[href$=/page/${page + 1}]") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val siteHost = baseUrl.toHttpUrl().host.removePrefix("m.")
        if (url.host != siteHost && !url.host.endsWith(".$siteHost")) return null
        val mangaPath = when (url.pathSegments.firstOrNull()) {
            "news" -> url.encodedPath
            "show" -> "/news/" + client.get("$baseUrl${url.encodedPath}", Fetch.DOCUMENT).asJsoup().params().comicId
            else -> return null
        }
        val manga = SManga.create().apply { this.url = mangaPath }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val info = document.selectFirst(".detailTop .info")!!
        val fields = info.select("p.subtitle").associate { it.text().substringBefore("：") to it.text().substringAfter("：").trim() }
        val details = SManga.create().apply {
            url = manga.url
            title = info.selectFirst(".title")!!.text()
            thumbnail_url = document.selectFirst(".detailTop img.cover")?.absUrl("src")
            author = fields["作者"]?.ifEmpty { null }
            genre = fields["类型"]?.split(" ")?.filter { it.isNotEmpty() }?.joinToString()?.ifEmpty { null }
            description = MarkdownDescription.Builder()
                .description(document.selectFirst(".detailContent > p")?.text())
                .build()
        }
        // Listed oldest first, sometimes once for each source the site took them from
        val chapterList = document.select("ul.chapterList a").map { link ->
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.text()
            }
        }.reversed()
        chapterList.firstOrNull()?.date_upload = DATE_FORMAT.tryParseDate(
            document.selectFirst("#chapter .top span")?.text()?.substringAfter("："),
            ZoneId.of("Asia/Shanghai"),
        )
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .params()
        .images
        .mapIndexed { index, image ->
            // The reader puts this host in front of the paths it gets for one of its sources
            Page(index, imageUrl = if (image.startsWith("/")) "$SOURCE_IMAGE_HOST$image" else image)
        }

    // The reader's script decrypts its parameters: base64 of a 16-byte IV followed by AES-CBC data
    private fun Document.params(): Params {
        val script = selectFirst("script:containsData(params)")!!.data()
        val bytes = Base64.decode(PARAMS_REGEX.find(script)!!.groupValues[1], Base64.DEFAULT)
        val cipher = aesCbc(KEY.encodeToByteArray(), bytes.copyOfRange(0, 16))
        return bytes.copyOfRange(16, bytes.size).aesDecrypt(cipher).decodeToString().parseAs()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(ListFilter())

    companion object {
        private val PARAMS_REGEX = Regex("""params\s*=\s*'([^']+)'""")
        private const val KEY = "9S8\$vJnU2ANeSRoF"
        private const val SOURCE_IMAGE_HOST = "https://img1.baipiaoguai.org"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}

@Serializable
private class Params(
    val images: List<String>,
    @SerialName("comic_id") val comicId: String,
)
