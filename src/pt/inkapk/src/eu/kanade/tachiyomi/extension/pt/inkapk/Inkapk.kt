package eu.kanade.tachiyomi.extension.pt.inkapk

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Inkapk : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.forLanguageTag("pt-BR"))

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    override val mangaSubString = "obras"

    override val filterGenresSelector = ".ink-genre-bar"
    override val genreDirectory = "obras-genre"

    override val chapterMode = ChapterMode.MangaAjax

    // ===================================== Details ==========================================

    override val mangaDetailsSelectorTitle = ".ink-det-title"
    override val mangaDetailsSelectorThumbnail = ".ink-det-cover img"
    override val mangaDetailsSelectorDescription = ".ink-det-desc"
    override val mangaDetailsSelectorGenre = ".ink-det-genres .ink-genre-pill"
    override val mangaDetailsSelectorStatus = ".lbl:contains(Status) + span"
    override val mangaDetailsSelectorAuthor = ".lbl:contains(Autor) + span"
    override val mangaDetailsSelectorArtist = ".lbl:contains(Arte) + span"
}
