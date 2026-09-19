package eu.kanade.tachiyomi.extension.ar.mangastarz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.UserAgentType
import keiyoushi.lib.randomua.setRandomUserAgent
import okhttp3.Headers
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaStarz : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM، yyyy", Locale.forLanguageTag("ar"))

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        setRandomUserAgent(UserAgentType.MOBILE, filterInclude = listOf("Chrome"))
        set("Accept-Language", "ar,en;q=0.9")
    }

    override fun getMangaUrl(manga: SManga): String = super.getMangaUrl(manga)
}
