package eu.kanade.tachiyomi.extension.ar.mangaspark

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.UserAgentType
import keiyoushi.lib.randomua.setRandomUserAgent
import okhttp3.Headers
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaSpark : Madara() {

    override val dateFormat = SimpleDateFormat("d MMMM، yyyy", Locale("ar"))
    override val chapterUrlSuffix = ""
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
    override val pageListParseSelector = "div.wp-manga-chapter-img img, div.reading-content img"

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .setRandomUserAgent(UserAgentType.MOBILE, filterInclude = listOf("Chrome"))
        .set("Accept-Language", "ar,en;q=0.9")

    override fun getMangaUrl(manga: SManga): String = super.getMangaUrl(manga)
}
