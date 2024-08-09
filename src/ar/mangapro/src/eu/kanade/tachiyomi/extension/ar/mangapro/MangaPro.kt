package eu.kanade.tachiyomi.extension.ar.mangapro

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MangaPro : MangaThemesia(
    "Manga Pro",
    "https://promanga.pro",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    override val versionId = 3

    override fun pageListParse(document: Document): List<Page> {
        return super.pageListParse(document).onEach {
            val httpUrl = it.imageUrl!!.toHttpUrl()

            if (wpImgRegex.containsMatchIn(httpUrl.host)) {
                it.imageUrl = StringBuilder().apply {
                    val ssl = httpUrl.queryParameter("ssl")
                    when (ssl) {
                        null -> append(httpUrl.scheme)
                        "0" -> append("http")
                        else -> append("https")
                    }
                    append("://")
                    append(httpUrl.pathSegments.joinToString("/"))
                    val search = httpUrl.queryParameter("q")
                    if (search != null) {
                        append("?q=")
                        append(search)
                    }
                }.toString()
            }
        }
    }
}

private val wpImgRegex = Regex("""i\d+\.wp\.com""")
