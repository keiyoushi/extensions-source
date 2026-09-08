package eu.kanade.tachiyomi.extension.ar.yurimoonsub

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Source
abstract class YuriMoonSub : ZeistManga() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override fun getChapterFeedUrl(doc: Document, mangaTitle: String): String = URLDecoder.decode(super.getChapterFeedUrl(doc, mangaTitle), StandardCharsets.UTF_8.toString())
        .removeArabicChars()

    private fun String.removeArabicChars() = this.replace(ARABIC_CHARS_REGEX, "")
        .replace(EXTRA_SPACES_REGEX, "")

    companion object {
        val ARABIC_CHARS_REGEX = "[\\u0600-\\u06FF]".toRegex()
        val EXTRA_SPACES_REGEX = "\\s{2,}".toRegex()
    }
}
