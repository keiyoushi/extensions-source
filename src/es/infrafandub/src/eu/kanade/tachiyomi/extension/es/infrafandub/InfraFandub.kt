package eu.kanade.tachiyomi.extension.es.infrafandub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class InfraFandub : Madara() {
    override val supportsPostId = false
    override val supportsLatest = false
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es"))

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2, 1.seconds)

    override val chapterMode = ChapterMode.MangaAjax

    override fun archiveSelector() = "div.manga-item"
    override val archiveUrlSelector = "div.title a"

    override val mangaDetailsSelectorTitle = "h1.series-title"
    override val mangaDetailsSelectorAuthor = "div.series-details div.detail-item:contains(Autor) span.detail-value"
    override val mangaDetailsSelectorArtist = "div.series-details div.detail-item:contains(Artista) span.detail-value"
    override val mangaDetailsSelectorGenre = "div.genres a.genre-tag"
    override val mangaDetailsSelectorDescription = "div.summary-text"
    override val mangaDetailsSelectorThumbnail = "aside.sidebar img.series-cover"
    override val mangaDetailsSelectorStatus = "div.series-details div.detail-item:contains(Estado) span.detail-value"
}
