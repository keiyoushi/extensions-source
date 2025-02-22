package eu.kanade.tachiyomi.extension.ar.yurimoonsub

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class YuriMoonSub : ZeistManga(
    "Yuri Moon Sub",
    "https://yurimoonsub.blogspot.com",
    "ar",
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun getChapterFeedUrl(doc: Document): String {
        return URLDecoder.decode(super.getChapterFeedUrl(doc), StandardCharsets.UTF_8.toString())
            .removeArabicChars()
    }

    private fun String.removeArabicChars() =
        this.replace(ARABIC_CHARS_REGEX, "")
            .replace(EXTRA_SPACES_REGEX, "")

    companion object {
        val ARABIC_CHARS_REGEX = "[\\u0600-\\u06FF]".toRegex()
        val EXTRA_SPACES_REGEX = "\\s{2,}".toRegex()
    }
}
