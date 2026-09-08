package eu.kanade.tachiyomi.extension.en.asmotoon

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Asmotoon : Keyoapp() {

    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3, 5.seconds) { it.host == baseUrlHost }
    }

    override fun popularMangaSelector() = "div:contains(Trending) + div .group"
    override fun latestUpdatesSelector() = ".group"
    override fun searchMangaSelector() = latestUpdatesSelector()

    override val genreSelector: String = ".gap-3 .gap-1 a"

    private fun titleToSlug(title: String): String = title
        .lowercase(Locale.ENGLISH)
        .replace("[^a-z0-9\\s-]".toRegex(), "")
        .trim()
        .replace("[\\s-]+".toRegex(), "-")
        .trim('-')

    private fun SManga.fix(): SManga = apply {
        url = url.replace(OLD_CHAPTER_SLUG_REGEX) { titleToSlug(title) }
    }

    override fun getMangaUrl(manga: SManga): String = super.getMangaUrl(manga.fix())

    companion object {
        private val OLD_CHAPTER_SLUG_REGEX = "(?<=/series/)[0-9a-f]{11}(?=/)".toRegex()
    }
}
