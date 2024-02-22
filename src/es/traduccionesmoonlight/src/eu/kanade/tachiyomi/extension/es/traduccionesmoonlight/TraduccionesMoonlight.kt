package eu.kanade.tachiyomi.extension.es.traduccionesmoonlight

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class TraduccionesMoonlight : MangaThemesia(
    "Traducciones Moonlight",
    "https://traduccionesmoonlight.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es")),
) {
    // Site moved from Madara to MangaThemesia
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2, 1)
        .build()

    override val seriesAuthorSelector = ".tsinfo .imptdt:contains(autor) i"
    override val seriesStatusSelector = ".tsinfo .imptdt:contains(estado) i"

    // Filter out novels
    override fun searchMangaSelector() = ".utao .uta .imgu:not(:has(.novelabel)), .listupd .bs .bsx:not(:has(.novelabel)), .listo .bs .bsx:not(:has(.novelabel))"
}
