package eu.kanade.tachiyomi.extension.en.cocomic

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Cocomic : Madara() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds) { !it.encodedPath.startsWith("/wp-content/uploads/") }

    override val chapterMode = ChapterMode.MangaAjax

    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium)"
}
