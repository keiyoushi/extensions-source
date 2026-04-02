package eu.kanade.tachiyomi.extension.es.manhwaonline

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwaOnline :
    Madara(
        "ManhwaOnline",
        "https://manhwa-online.com",
        "es",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    private val imageArrayRegex = """_d\s*=\s*\[(.*?)];""".toRegex()
    private val xorKeyRegex = """return\(a\^(\d+)\)""".toRegex()

    override fun pageListParse(document: Document): List<Page> {
        fun decodeUrl(encoded: String, k: Int): String = Base64.decode(encoded, Base64.DEFAULT).map {
            ((it.toInt() and 0xFF) xor k).toChar()
        }.joinToString("")

        val scriptData = document.selectFirst("#mowl-shield")!!.data()
        val xorKey = xorKeyRegex.find(scriptData)!!.groupValues[1].toInt()

        return imageArrayRegex.find(scriptData)!!.groupValues[1]
            .split(",")
            .mapIndexed { index, encodedUrl ->
                val cleanEncoded = encodedUrl.trim().removeSurrounding("\"")
                Page(index, imageUrl = decodeUrl(cleanEncoded, xorKey))
            }
    }
}
