package eu.kanade.tachiyomi.extension.pt.animexnovel

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Element
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class AnimeXNovel : ZeistManga(
    "AnimeXNovel",
    "https://www.animexnovel.com",
    "pt-BR",
) {

    // ============================== Popular ===============================

    override val popularMangaSelector = "#PopularPosts2 article"
    override val popularMangaSelectorTitle = "h3 > a"
    override val popularMangaSelectorUrl = popularMangaSelectorTitle

    override fun popularMangaParse(response: Response): MangasPage {
        return super.popularMangaParse(response).let(::filterMangaEntries)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesParse(response: Response): MangasPage {
        return super.latestUpdatesParse(response).let(::filterMangaEntries)
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst(".thumb")?.absUrl("src")
        description = document.selectFirst("#synopsis")?.text()
        document.selectFirst("span[data-status]")?.let {
            status = parseStatus(it.text().lowercase())
        }
        genre = document.select("dl:has(dt:contains(Gênero)) dd a")
            .joinToString { it.text() }

        setUrlWithoutDomain(document.location())
    }

    // ============================== Chapters ===============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val label = document.select("script")
            .map(Element::data)
            .firstOrNull(MANGA_TITLE_REGEX::containsMatchIn)?.let {
                MANGA_TITLE_REGEX.find(it)?.groups?.get(1)?.value
            } ?: return emptyList()

        val script = document.select("script")
            .map(Element::data)
            .firstOrNull(API_KEY_REGEX::containsMatchIn)
            ?: return emptyList()

        val blogId = BLOG_ID_REGEX.find(script)?.groups?.get(1)?.value ?: return emptyList()
        val apiKey = API_KEY_REGEX.find(script)?.groups?.get(1)?.value ?: return emptyList()

        val url = "https://www.googleapis.com/blogger/v3/blogs/$blogId/posts".toHttpUrl().newBuilder()
            .addQueryParameter("key", apiKey)
            .addQueryParameter("labels", label)
            .addQueryParameter("maxResults", "500")
            .build()

        val response = client.newCall(GET(url, headers)).execute()

        if (response.isSuccessful.not()) {
            throw IOException("Capítulos não encontrados")
        }

        return response.parseAs<ChapterWrapperDto>().items.map {
            SChapter.create().apply {
                name = it.title
                date_upload = dateFormat.tryParse(it.published)
                setUrlWithoutDomain(it.url)
            }
        }
    }

    // ============================== Pages ===============================

    override val pageListSelector = "#reader .separator"

    // ============================== Utils ===============================

    private fun filterMangaEntries(mangasPage: MangasPage): MangasPage {
        val prefix = "[Mangá]"
        return mangasPage.copy(
            mangasPage.mangas.filter {
                it.title.contains(prefix)
            }.map {
                it.apply {
                    title = title.substringAfter(prefix).trim()
                }
            },
        )
    }

    companion object {
        private val API_KEY_REGEX = """(?:API_KEY(?:\s+)?=(?:\s+)?.)"([^(\\|")]+)""".toRegex()
        private val BLOG_ID_REGEX = """(?:BLOG_ID(?:\s+)?=(?:\s+)?.)"([^(\\|")]+)""".toRegex()
        private val MANGA_TITLE_REGEX = """iniciarCapituloLoader\("([^"]+)"\)""".toRegex()
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}
