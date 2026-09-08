package eu.kanade.tachiyomi.extension.es.sapphirescan

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class SapphireScan : ZeistManga() {
    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3) { it.host == baseUrlHost }

    // Madara -> ZeistManga migration
    override fun getMangaUrl(manga: SManga): String {
        if (manga.url.contains("/manga/")) {
            throw Exception("Migrar de $name a $name (misma extensión)")
        }
        return super.getMangaUrl(manga)
    }

    // Madara -> ZeistManga migration
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.url.contains("/manga/")) {
            throw Exception("Migrar de $name a $name (misma extensión)")
        }
        return super.getPageList(chapter)
    }

    override val pageListSelector = "div.check-box"
}
