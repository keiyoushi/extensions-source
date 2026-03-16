package eu.kanade.tachiyomi.extension.es.escaneodeleyendas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class EscaneoDeLeyendas :
    Madara(
        "Escaneo de Leyendas",
        "https://escaneodeleyendas.com",
        "es",
        SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es")),
    ) {
    override val useNewChapterEndpoint = true

    override fun imageFromElement(element: org.jsoup.nodes.Element): String? = super.imageFromElement(element)?.trim()
}
