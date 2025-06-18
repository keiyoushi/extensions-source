package eu.kanade.tachiyomi.extension.pt.yushukemangas

import eu.kanade.tachiyomi.multisrc.yuyu.YuYu
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class YushukeMangas : YuYu(
    "Yushuke Mangas",
    "https://new.yushukemangas.com",
    "pt-BR",
) {

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val versionId = 2

    override fun mangaDetailsParse(document: Document): SManga {
        if (document.location().contains(MANGA_URL_ID_REGEX).not()) {
            return super.mangaDetailsParse(document)
        }

        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst(".detalhe-capa-img")?.absUrl("src")
            genre = document.select(".detalhe-tags-chips span").joinToString { it.text() }
            description = document.selectFirst(".detalhe-sinopse")?.text()
            document.selectFirst(".detalhe-chip.status")?.ownText()?.let {
                status = it.toStatus()
            }
        }
    }

    override fun getMangaId(manga: SManga): String {
        return when {
            manga.isOldEntry() -> super.getMangaId(manga)
            else -> MANGA_URL_ID_REGEX.find(manga.url)?.groups?.get(1)?.value ?: ""
        }
    }

    private fun SManga.isOldEntry(): Boolean = MANGA_URL_ID_REGEX.containsMatchIn(url).not()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        if (manga.isOldEntry()) {
            return super.fetchChapterList(manga)
        }

        val mangaId = getMangaId(manga).takeIf(String::isNotBlank)
            ?: throw Exception("Manga ID não encontrado")

        val chapters = mutableListOf<SChapter>()
        var page = 1
        val url = manga.url.replace("/$mangaId", "")
        do {
            val chaptersDto = fetchChapterListPage(mangaId, page++).parseAs<ChaptersDto<List<ChapterDto>>>()
            chapters += chaptersDto.chapters.map { it.toSChapter(url) }
        } while (chaptersDto.hasNext())

        return Observable.just(chapters)
    }

    private fun fetchChapterListPage(mangaId: String, page: Int): Response {
        val url = "$baseUrl/ajax/get_chapters.php".toHttpUrl().newBuilder()
            .addQueryParameter("manga_id", mangaId)
            .addQueryParameter("page", page.toString())
            .build()

        return client
            .newCall(GET(url, headers))
            .execute()
    }

    companion object {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        private val MANGA_URL_ID_REGEX = """\/obra\/(\d+)\/.+$""".toRegex()
    }
}
