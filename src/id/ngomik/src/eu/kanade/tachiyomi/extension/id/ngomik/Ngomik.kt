package eu.kanade.tachiyomi.extension.id.ngomik

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import okhttp3.Headers

@Source
abstract class Ngomik : MangaThemesia() {

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.163 Safari/537.36"

    override fun Headers.Builder.configureHeaders() = set("User-Agent", userAgent)

    override val projectPageString = "/pj"

    override val hasProjectPage = true
}
