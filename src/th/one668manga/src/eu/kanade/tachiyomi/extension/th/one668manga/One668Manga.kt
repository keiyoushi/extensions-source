package eu.kanade.tachiyomi.extension.th.one668manga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import okhttp3.Headers

@Source
abstract class One668Manga : MangaThemesia() {
    override fun Headers.Builder.configureHeaders() = removeAll("Referer")
}
