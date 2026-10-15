package eu.kanade.tachiyomi.extension.es.manhuaonline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class SamuraiScan : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd MMMM, yyyy", Locale.forLanguageTag("es"))

    override val chapterMode = ChapterMode.MangaAjax

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override val mangaSubString = "leer"
    override val genreDirectory = "l-generos"

    override val mangaDetailsSelectorDescription = "div.summary__content"
}
