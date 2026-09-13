package eu.kanade.tachiyomi.extension.en.anisascans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class AnisaScans : Madara() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3) { !it.encodedPath.startsWith("/wp-content/uploads/") }

    override val chapterMode = ChapterMode.MangaAjax
}
