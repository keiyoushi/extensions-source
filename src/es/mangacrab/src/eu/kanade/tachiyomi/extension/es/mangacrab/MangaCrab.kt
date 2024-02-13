package eu.kanade.tachiyomi.extension.es.mangacrab

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class MangaCrab : Madara(
    "Manga Crab",
    "https://mangacrab3.com",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale("es")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(1, 2)
        .build()

    override val mangaSubString = "series"

    override fun chapterListSelector() = "div.listing-chapters_wrap > ul > li"
    override val mangaDetailsSelectorDescription = "div.c-page__content div.modal-contenido"
}
