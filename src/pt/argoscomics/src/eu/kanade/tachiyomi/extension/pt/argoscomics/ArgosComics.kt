package eu.kanade.tachiyomi.extension.pt.argoscomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class ArgosComics : Madara(
    "Argos Comics",
    "https://argoscomics.com",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
) {
    override fun latestUpdatesSelector() = "div.wp-block-wp-manga-gutenberg-manga-sliders-block:nth-child(2)"

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesNextPageSelector() = null
}
