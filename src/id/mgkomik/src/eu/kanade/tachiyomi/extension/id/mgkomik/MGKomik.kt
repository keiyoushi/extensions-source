package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import okhttp3.Headers
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MGKomik : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd MMM yy", Locale.US)

    override val mangaSubString = "komik"
    override val genreDirectory = "genres"

    override fun Headers.Builder.configureHeaders() = apply {
        set("Sec-CH-UA-Model", "\"\"")
        set("Sec-CH-UA-Platform", "\"\"")
    }

    override val mangaDetailsSelectorDescription = "div.description-summary div.summary__content p"
}
