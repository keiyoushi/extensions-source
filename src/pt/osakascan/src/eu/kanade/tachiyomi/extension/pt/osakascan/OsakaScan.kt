package eu.kanade.tachiyomi.extension.pt.osakascan

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class OsakaScan : ZeistManga() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    override val supportsLatest = false

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        description = document.selectFirst(mangaDetailsSelectorDescription)?.text()
        document.selectFirst("span[data-status]")?.text()?.let {
            status = parseStatus(it)
        }
        genre = document.select("dt:contains(Gênero) + dd a").joinToString { it.text() }
    }

    override suspend fun getChapterList(feedUrl: String, doc: Document?): List<SChapter> = super.getChapterList(feedUrl, doc)
        .map { chapter ->
            chapter.apply {
                CHAPTER_NUMBER_REGEX.find(name)?.groups?.get(0)?.value?.let {
                    chapter_number = it.toFloat()
                }
            }
        }
        .sortedBy(SChapter::chapter_number).reversed()

    override val pageListSelector = "#reader div.separator"

    companion object {
        val CHAPTER_NUMBER_REGEX = """\d+(\.\d+)?""".toRegex()
    }
}
