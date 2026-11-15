package eu.kanade.tachiyomi.extension.en.mangakakalot

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Mangakakalot : MangaBox() {

    /* ================================
     * Slug Utilities
     * ================================ */

    private val idSlugRegex = Regex("^[a-z]{2}\\d+$")

    private fun titleToSlug(title: String): String = title
        .lowercase(Locale.ENGLISH)
        .replace("[^a-z0-9\\s-]".toRegex(), "")
        .trim()
        .replace("[\\s-]+".toRegex(), "-")
        .trim('-')

    /**
     * Resolves download issues for very large chapters (~100+ pages).
     *
     * Mangakakalot slows down or rejects image-heavy requests when connections
     * are reused aggressively. Rate limiting alone (jitter) was insufficient.
     *
     * - Dispatcher limits parallel image requests
     * - "Connection: close" forces socket reset per image
     * - Prevents per-page stalls (30s to minutes) near chapter end
     */

    private val imageHeavyDispatcher = Dispatcher().apply {
        maxRequests = 8
        maxRequestsPerHost = 3
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(::mergeImagesInterceptor)
        addInterceptor(::useAltCdnInterceptor)
        dispatcher(imageHeavyDispatcher)
        connectTimeout(30.seconds)
        readTimeout(60.seconds)
        writeTimeout(30.seconds)
        rateLimit(2, 1.seconds)
    }

    override fun computeMangaSlug(manga: SManga): String = super.computeMangaSlug(manga).let {
        if (it.matches(idSlugRegex)) titleToSlug(manga.title) else it
    }
}
