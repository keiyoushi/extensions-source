package eu.kanade.tachiyomi.extension.es.barmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.Request
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class BarManga :
    Madara(
        "BarManga",
        "https://archiviumbar.com",
        "es",
        SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
    ) {

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun popularMangaSelector() = "#loop-content .mp-card"

    override val popularMangaUrlSelector = ".mp-card-title > a"

    override val mangaDetailsSelectorTitle = ".breadcrumb > li:last-child > a"

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }
        val script = document.selectFirst(".manga-reader-container + script")!!.data()
        val tokens = PAGE_TOKENS_REGEX.find(script)?.groupValues?.last()?.parseAs<Map<String, String>>() ?: return emptyList()
        val nonce = NONCE_REGEX.find(script)?.groupValues?.last() ?: return emptyList()
        val action = ACTION_REGEX.find(script)?.groupValues?.last() ?: return emptyList()
        val chapterKey = CHAPTER_KEY_REGEX.find(script)?.groupValues?.last() ?: return emptyList()
        return tokens
            .map { (page, token) -> PageDto(nonce, token, page, action, chapterKey) }
            .mapIndexed { index, dto ->
                Page(index, document.location(), dto.toJsonString())
            }
    }

    override fun imageRequest(page: Page): Request {
        val dto = page.imageUrl!!.parseAs<PageDto>()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("action", dto.action)
            .addFormDataPart("token", dto.token)
            .addFormDataPart("page", dto.page)
            .addFormDataPart("nonce", dto.nonce)
            .addFormDataPart("chapter_key", dto.chapterKey)
            .build()

        val imageHeaders = headers.newBuilder()
            .set("Accept", "*/*")
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Referer", page.url)
            .build()
        return POST("$baseUrl/wp-admin/admin-ajax.php", imageHeaders, body)
    }

    @Serializable
    class PageDto(
        val nonce: String,
        val token: String,
        val page: String,
        val action: String,
        // Default value is needed for backward compatibility with cached pages to prevent MissingFieldException
        val chapterKey: String = "",
    )

    companion object {
        private val PAGE_TOKENS_REGEX = """(?:_tokens\s+?=\s+?)([^;]+)""".toRegex()
        private val NONCE_REGEX = """nonce:\s+?"([^"]+)""".toRegex()
        private val ACTION_REGEX = """action:\s+?"([^"]+)""".toRegex()
        private val CHAPTER_KEY_REGEX = """chapterKey:\s+?"([^"]+)""".toRegex()
    }
}
